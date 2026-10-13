package com.yzj;

import com.yzj.pojo.JudgeResult;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;


public class JudgeService {
    @Test
    public void judge() throws IOException, InterruptedException {

        Path workDir = Path.of(System.getProperty("java.io.tmpdir"), "oj-judge");
        Files.createDirectories(workDir);
        Path file = workDir.resolve("Main.java");
    // ← 程序生成源码文件
        System.out.println(file);
    }
}
