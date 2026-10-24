package com.yzj.service.impl;

import com.yzj.pojo.JudgeResult;
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
 * 阶段 2：Docker 沙箱判题
 *
 * 每次提交：
 *   ① 建独立目录
 *   ② 用容器编译（断网）
 *   ③ 用容器运行（断网 + 限内存 + 限 CPU）
 *   ④ 清理目录
 *
 * ⚠️ 本机 Windows 没有 docker，docker 装在 WSL 里，所以命令前缀是 `wsl -d Ubuntu -- docker`，
 *    挂载路径要转成 WSL 能识别的 /mnt/c/...
 */
@Slf4j
@Service
public class JudgeServiceImpl implements JudgeService {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase().contains("win");
    private static final String FILE_NAME = "Main.java";
    private static final String CLASS_NAME = "Main";
    private static final String OUTPUT_FILE = "out.txt";

    /** 判题镜像：带 javac 的 JDK */
    private static final String IMAGE = "eclipse-temurin:21-jdk";
    /** WSL 发行版名 */
    private static final String WSL_DISTRO = "Ubuntu";

    /** 编译超时（秒）—— 比运行宽松得多 */
    private static final long COMPILE_TIMEOUT_SECONDS = 30;
    /** 运行超时（秒） */
    private static final long TIME_LIMIT_SECONDS = 2;

    @Override
    public JudgeResult judge(String code, String expected, String input) {
        if (code == null || code.isBlank()) {
            return new JudgeResult("CE", "代码不能为空", 0);
        }

        try {
            return doJudge(code, expected, input);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("判题被中断", e);
        } catch (IOException e) {
            throw new RuntimeException("判题机内部错误", e);
        }
    }

    private JudgeResult doJudge(String code, String expected, String input)
            throws IOException, InterruptedException {

        // 每次提交一个独立目录，避免并发覆盖
        Path workDir = Path.of(System.getProperty("java.io.tmpdir"),
                "oj-judge", UUID.randomUUID().toString());
        Files.createDirectories(workDir);

        File inputFile = null;
        if (input != null && !input.isEmpty()) {
            inputFile = workDir.resolve("input.txt").toFile();
            Files.writeString(inputFile.toPath(), input);
        }

        try {
            return runInDir(workDir, inputFile, code, expected);
        } finally {
            deleteDir(workDir);
        }
    }

    private JudgeResult runInDir(Path workDir,File inputFile, String code, String expected)
            throws IOException, InterruptedException {

        Files.writeString(workDir.resolve(FILE_NAME), code);
        // ---------- ① 编译 ----------

        String compileContainerName = "oj-compile-" + UUID.randomUUID().toString().substring(0, 8);
        File compileOutFile = workDir.resolve("compile_out.txt").toFile();

        Process compile = startContainer(workDir, compileContainerName, compileOutFile,
                inputFile, List.of("javac",
                        "-J-Duser.language=en",
                        "-J-Duser.country=US",
                        FILE_NAME));


        if (!compile.waitFor(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            compile.destroyForcibly();
            killContainer(compileContainerName);
            return new JudgeResult("SE", "编译超时", 0);
        }
        int compileExitValue = compile.exitValue();
        if (compileExitValue != 0) {
            // 【修复】从 compile_out.txt 中读取真正的编译错误信息
            String compileOut = Files.exists(compileOutFile.toPath())
                    ? Files.readString(compileOutFile.toPath())
                    : "编译失败，但无错误输出";
            if (compileExitValue == 125) {
                return new JudgeResult("SE", "判题机环境异常：" + compileOut, 0);   // ⭐ 系统问题
            }
            return new JudgeResult("CE", compileOut, 0);   // 用户代码问题
        }

        // ---------- ② 运行 ----------
        String runContainerName = "oj-" + UUID.randomUUID().toString().substring(0, 8);
        File outFile = workDir.resolve(OUTPUT_FILE).toFile();
        Process run = startContainer(workDir, runContainerName, outFile, inputFile,
                List.of("java",
                        "-Xmx128m",
                        "-Duser.language=en",
                        "-Duser.country=US",
                        CLASS_NAME));

        long start = System.currentTimeMillis();

        if (!run.waitFor(TIME_LIMIT_SECONDS + 3, TimeUnit.SECONDS)) {
            // ⚠️ 关键：杀 docker CLI 进程不会停容器，必须 docker kill
            run.destroyForcibly();
            killContainer(runContainerName);
            long cost = System.currentTimeMillis() - start;
            return new JudgeResult("TLE", null, cost);
        }

        long cost = System.currentTimeMillis() - start;
        int runExitCode = run.exitValue();

        Path outPath = workDir.resolve(OUTPUT_FILE);
        String runOutput = Files.exists(outPath) ? Files.readString(outPath) : "";

        if (runExitCode == 137) {
            return new JudgeResult("MLE", "内存超限", cost);        // ⭐ OOM Killer
        }

        if (runExitCode != 0) {
            if (runOutput.contains("OutOfMemoryError")) {
                return new JudgeResult("MLE", "内存超限", cost);    // ⭐ 堆溢出
            }
            return new JudgeResult("RE", runOutput, cost);
        }

        String actual = runOutput.trim();
        if (actual.equals(expected == null ? null : expected.trim())) {
            return new JudgeResult("AC", null, cost);
        }
        String diff = "actual : " + actual + "\n" + "expected : " + expected;
        return new JudgeResult("WA", diff, cost);
    }

    /**
     * 启动一个判题容器。
     *
     * @param workDir       宿主机工作目录（会被挂到容器 /work）
     * @param containerName 容器名（超时时用来 kill）
     * @param outputFile    非空则把容器输出重定向到该文件，为空则走管道由调用方读取
     * @param cmd           容器内执行的命令
     * @param inputFile     输入文件
     */
    private Process startContainer(Path workDir, String containerName, File outputFile,
                                   File inputFile, List<String> cmd) throws IOException {

        List<String> command = new ArrayList<>();

        // ① 命令前缀
        if (IS_WINDOWS) {
            command.addAll(List.of("wsl", "-d", WSL_DISTRO, "--"));
        }
        command.add("docker");

        // ② 路径（Windows 要转 /mnt/c/...）
        String hostPath = IS_WINDOWS ? toWslPath(workDir) : workDir.toString();

        // ③ docker run 的参数
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

        // ④ 容器内要执行的命令
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

    /** docker kill 兜底：万一 CLI 被强杀，容器还在跑 */
    private void killContainer(String containerName) {
        try {
            List<String> command = new ArrayList<>();
            // ① 命令前缀
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

    private static void deleteDir(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    tryDelete(p);
            });
        } catch (IOException ignored) {
            // 同上
        }
    }

    private static boolean tryDelete(Path p) {
        for (int i = 0; i < 3; i++) {
            try {
                Files.deleteIfExists(p);
                return true;
            } catch (IOException e) {
                try { Thread.sleep(100); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }
}
