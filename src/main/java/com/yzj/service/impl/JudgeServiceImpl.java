package com.yzj.service.impl;

import com.yzj.pojo.JudgeResult;
import com.yzj.pojo.TestCase;
import com.yzj.service.JudgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Docker 沙箱判题。
 *
 * 一次提交的流程：
 *   ① 建独立目录（UUID，防止并发互相覆盖）
 *   ② 写 Main.java
 *   ③ 编译一次（容器 1）
 *   ④ 逐个测试点运行（容器 N）
 *   ⑤ finally 清理目录
 *
 * ACM 模式：任一测试点失败立刻返回
 * OI 模式：跑完全部测试点，按 score 累加得分
 */
@Slf4j
@Service
public class JudgeServiceImpl implements JudgeService {

    private static final String FILE_NAME = "Main.java";
    private static final String CLASS_NAME = "Main";
    private static final String OUTPUT_FILE = "out.txt";
    private static final String COMPILE_OUT_FILE = "compile_out.txt";
    private static final String INPUT_FILE = "input.txt";

    private static final String IMAGE = "eclipse-temurin:21-jdk";
    private static final String WSL_DISTRO = "Ubuntu";
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase().contains("win");

    /** 编译超时（秒）—— 比运行宽松得多 */
    private static final long COMPILE_TIMEOUT_SECONDS = 30;
    /** 运行超时（秒）—— 单个测试点 */
    private static final long TIME_LIMIT_SECONDS = 2;
    /** 实际等待时间，给 docker 启动留余量 */
    private static final long RUN_TIMEOUT_SECONDS = TIME_LIMIT_SECONDS + 3;

    @Override
    public JudgeResult judge(String code, List<TestCase> testCases, boolean isOI) {
        if (code == null || code.isBlank()) {
            return JudgeResult.fail("CE", "代码不能为空", 0);
        }
        if (testCases == null || testCases.isEmpty()) {
            return JudgeResult.fail("SE", "题目没有配置测试点", 0);
        }
        try {
            return doJudge(code, testCases, isOI);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("判题被中断", e);
        } catch (IOException e) {
            throw new RuntimeException("判题机内部错误", e);
        }
    }

    private JudgeResult doJudge(String code, List<TestCase> testCases, boolean isOI)
            throws IOException, InterruptedException {

        // 每次提交一个独立目录，避免并发覆盖
        Path workDir = Path.of(System.getProperty("java.io.tmpdir"),
                "oj-judge", UUID.randomUUID().toString());
        Files.createDirectories(workDir);

        try {
            return runInDir(workDir, code, testCases, isOI);
        } finally {
            deleteDir(workDir);
        }
    }

    private JudgeResult runInDir(Path workDir, String code, List<TestCase> testCases, boolean isOI)
            throws IOException, InterruptedException {

        // ① 写源码
        Files.writeString(workDir.resolve(FILE_NAME), code);

        // ② 编译一次，后面所有测试点复用 Main.class
        JudgeResult compileFail = compile(workDir);
        if (compileFail != null) {
            return compileFail;
        }

        // ③ 逐个测试点
        int totalScore = 0;
        int maxScore = 0;
        int passed = 0;
        long maxCost = 0;
        List<String> failures = new ArrayList<>();

        for (int i = 0; i < testCases.size(); i++) {
            TestCase tc = testCases.get(i);
            maxScore += tc.getScore() == null ? 0 : tc.getScore();

            JudgeResult r = runOne(workDir, tc, i + 1);
            maxCost = Math.max(maxCost, r.timeCostMs());

            if ("AC".equals(r.verdict())) {
                passed++;
                totalScore += tc.getScore() == null ? 0 : tc.getScore();
                continue;
            }

            // ---- 这个测试点失败了 ----
            if (!isOI) {
                return r;                       // ACM：立刻返回（cost 就是这个点的）
            }
            failures.add("第 " + (i + 1) + " 个: " + r.verdict());
        }

        if (isOI) {
            return JudgeResult.oi(totalScore, maxScore, passed, testCases.size(),
                    maxCost, String.join("\n", failures));
        }
        return JudgeResult.ac(maxCost);
    }

    /**
     * 编译一次。
     *
     * @return null 表示成功；非 null 是 SE 或 CE 的判定结果
     */
    private JudgeResult compile(Path workDir) throws IOException, InterruptedException {

        String name = "oj-compile-" + UUID.randomUUID().toString().substring(0, 8);
        File outFile = workDir.resolve(COMPILE_OUT_FILE).toFile();

        Process p = startContainer(workDir, name, outFile, null,
                List.of("javac",
                        "-J-Duser.language=en",
                        "-J-Duser.country=US",
                        FILE_NAME));

        if (!p.waitFor(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            killContainer(name);
            return JudgeResult.fail("SE", "编译超时", 0);
        }

        int exit = p.exitValue();
        String out = Files.exists(outFile.toPath()) ? Files.readString(outFile.toPath()) : "";

        if (exit == 125) {
            // docker 自己出错（参数错、镜像缺失、daemon 挂了）—— 不是用户的问题
            return JudgeResult.fail("SE", "判题机环境异常: " + out, 0);
        }
        if (exit != 0) {
            return JudgeResult.fail("CE", out, 0);
        }
        return null;
    }

    /** 跑一个测试点。 */
    private JudgeResult runOne(Path workDir, TestCase tc, int index)
            throws IOException, InterruptedException {

        // ---- 写 input.txt（可空）----
        File inputFile = null;
        if (tc.getInput() != null && !tc.getInput().isEmpty()) {
            inputFile = workDir.resolve(INPUT_FILE).toFile();
            Files.writeString(inputFile.toPath(), tc.getInput());
        }

        String name = "oj-run-" + UUID.randomUUID().toString().substring(0, 8);
        File outFile = workDir.resolve(OUTPUT_FILE).toFile();

        Process p = startContainer(workDir, name, outFile, inputFile,
                List.of("java",
                        "-Xmx128m",
                        "-Duser.language=en",
                        "-Duser.country=US",
                        CLASS_NAME));

        long start = System.currentTimeMillis();

        if (!p.waitFor(RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            // ⚠️ destroyForcibly 只杀 docker 客户端，容器还活着
            p.destroyForcibly();
            killContainer(name);
            return JudgeResult.fail("TLE", "第 " + index + " 个测试点超时",
                    System.currentTimeMillis() - start);
        }

        long cost = System.currentTimeMillis() - start;
        int exit = p.exitValue();
        String out = Files.exists(outFile.toPath()) ? Files.readString(outFile.toPath()) : "";

        if (exit == 125) {
            return JudgeResult.fail("SE", "判题机环境异常: " + out, cost);
        }
        if (exit == 137) {
            // 被 SIGKILL —— 因为超时已经先行返回，这里只可能是 OOM Killer
            return JudgeResult.fail("MLE", "第 " + index + " 个测试点内存超限", cost);
        }
        if (exit != 0) {
            if (out.contains("OutOfMemoryError")) {
                return JudgeResult.fail("MLE", "第 " + index + " 个测试点内存超限", cost);
            }
            return JudgeResult.fail("RE", "第 " + index + " 个测试点运行错误\n" + out, cost);
        }

        String actual = out.trim();
        String expected = tc.getExpected() == null ? "" : tc.getExpected().trim();

        if (actual.equals(expected)) {
            return JudgeResult.ac(cost);
        }
        return JudgeResult.fail("WA", "第 " + index + " 个测试点错误\nactual: " + actual
                + "\nexpected: " + expected, cost);
    }

    // ================= 容器相关 =================

    /**
     * 启动一个判题容器。
     *
     * @param workDir       宿主机工作目录（挂到容器 /work）
     * @param containerName 容器名（超时时用来 kill）
     * @param outputFile    非空则把容器输出重定向到该文件
     * @param inputFile     非空则把该文件接到容器 stdin（需要 -i）
     * @param cmd           容器内执行的命令
     */
    private Process startContainer(Path workDir, String containerName, File outputFile,
                                   File inputFile, List<String> cmd) throws IOException {

        List<String> command = new ArrayList<>();

        // ① 命令前缀：Windows 走 WSL，Linux 直接用
        if (IS_WINDOWS) {
            command.addAll(List.of("wsl", "-d", WSL_DISTRO, "--"));
        }
        command.add("docker");

        // ② 挂载路径（Windows 要转 /mnt/c/...）
        String hostPath = IS_WINDOWS ? toWslPath(workDir) : workDir.toString();

        // ③ docker run 参数
        command.addAll(List.of(
                "run", "--rm", "-i",
                "--pids-limit=100",
                "--name", containerName,
                "--network=none",
                "--memory=256m",
                "--memory-swap=256m",
                "--cpus=1",
                "-v", hostPath + ":/work",
                "-w", "/work",
                IMAGE));

        // ④ 容器内命令
        command.addAll(cmd);

        ProcessBuilder pb = new ProcessBuilder(command);

        if (inputFile != null) {
            pb.redirectInput(inputFile);
        }
        if (outputFile != null) {
            pb.redirectErrorStream(true);
            pb.redirectOutput(outputFile);
        }
        return pb.start();
    }

    /** docker kill 兜底：杀 docker 客户端不会停容器 */
    private void killContainer(String containerName) {
        try {
            List<String> command = new ArrayList<>();
            if (IS_WINDOWS) {
                command.addAll(List.of("wsl", "-d", WSL_DISTRO, "--"));
            }
            command.add("docker");
            command.addAll(List.of("kill", containerName));

            new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("kill 容器失败: {}", containerName, e);
        }
    }

    /** Windows 路径 → WSL 里的 /mnt/... 路径 */
    private static String toWslPath(Path path) {
        String s = path.toAbsolutePath().toString().replace('\\', '/');
        if (s.length() >= 2 && s.charAt(1) == ':') {
            s = "/mnt/" + Character.toLowerCase(s.charAt(0)) + s.substring(2);
        }
        return s;
    }

    // ================= 清理 =================

    private static void deleteDir(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(JudgeServiceImpl::tryDelete);
        } catch (IOException ignored) {
            // 清理失败不影响判题结果
        }
    }

    /** Windows 上文件句柄释放有延迟，所以重试几次 */
    private static boolean tryDelete(Path p) {
        for (int i = 0; i < 3; i++) {
            try {
                Files.deleteIfExists(p);
                return true;
            } catch (IOException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }
}
