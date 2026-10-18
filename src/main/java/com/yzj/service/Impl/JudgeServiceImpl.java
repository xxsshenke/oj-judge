package com.yzj.service.Impl;

import com.yzj.pojo.JudgeResult;

import com.yzj.service.JudgeService;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class JudgeServiceImpl implements JudgeService {

    private static final String FILE_NAME = "Main.java";
    private static final String CLASS_NAME = "Main";
    private static final String OUTPUT_FILE = "out.txt";
    private static final long TIME_LIMIT_SECONDS = 2;

    public JudgeResult judge(String code, String expected) {
        if (code == null || code.isBlank()) {
            return new JudgeResult("CE", "代码不能为空");
        }
        try {
            return doJudge(code, expected);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("判题被中断", e);
        } catch (IOException e) {
            throw new RuntimeException("判题机内部错误", e);
        }
    }

    private JudgeResult doJudge(String code, String expected)
            throws IOException, InterruptedException {

        Path workDir = Path.of(System.getProperty("java.io.tmpdir"), "oj-judge");
        Files.createDirectories(workDir);
        Path file = workDir.resolve(FILE_NAME);
        Files.writeString(file, code);     // ← 程序生成源码文件

        // 然后照旧用 ProcessBuilder 编译它
        ProcessBuilder pb = new ProcessBuilder(
                "javac",
                "-J-Duser.language=en",     // 让 javac 说英文（避免乱码）
                "-J-Duser.country=US",
                "Main.java");
        pb.directory(workDir.toFile());   // 子进程的工作目录
        pb.redirectErrorStream(true);
        Process ps = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader rf = new BufferedReader(
                new InputStreamReader(ps.getInputStream()))) {
            String line;
            while ((line = rf.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        int exitCode = ps.waitFor();
        if (exitCode != 0) {
            return new JudgeResult("CE", output.toString());
        } else {
            ProcessBuilder run = new ProcessBuilder("java",
                    "-Duser.language=en",
                    "-Duser.country=US",
                    CLASS_NAME);
            run.directory(workDir.toFile());
            run.redirectErrorStream(true);
            run.redirectOutput(workDir.resolve(OUTPUT_FILE).toFile());
            Process runStart = run.start();
            // 现在没人读流了，主线程可以安全地在这里等
            if (!runStart.waitFor(TIME_LIMIT_SECONDS, TimeUnit.SECONDS)) {
                runStart.destroyForcibly();                        // ← 超时，杀！
                return new JudgeResult("TLE", null);
            }
            String runOutput = Files.readString(workDir.resolve(OUTPUT_FILE));
            int runExitCode = runStart.exitValue();   // 进程已结束，直接读退出码
            if (runExitCode != 0) {
                return new JudgeResult("RE", runOutput);
            } else {// 标准答案
                String actual = runOutput.trim();              // 用户程序输出
                if (actual.equals(expected)) {
                    return new JudgeResult("AC", null);
                } else {
                    String out = "actual : " + actual + "\n" + "expected : " + expected;
                    return new JudgeResult("WA", out);
                }
            }
        }
    }
}
