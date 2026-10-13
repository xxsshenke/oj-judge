package com.yzj;

import com.yzj.pojo.JudgeResult;
import com.yzj.service.JudgeService;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JudgeServiceTest {

    private final JudgeService judgeService = new JudgeService();   // ← 直接 new

    @Test
    void shouldReturnACWhenOutputMatches() throws Exception {
        String code = """
                public class Main {
                    public static void main(String[] args) { System.out.println("hello judge"); }
                }
                """;
        JudgeResult r = judgeService.judge(code, "hello judge");
        assertEquals("AC", r.verdict(),"AC 答案正确时测试成功");
    }

    @Test
    void shouldReturnWAWhenOutputDiffers() throws IOException, InterruptedException {
        String code = """
                public class Main {
                    public static void main(String[] args) { System.out.println("WA"); }
                }
                """;
        JudgeResult r = judgeService.judge(code, "hello judge");
        assertEquals("WA", r.verdict(),"WA 答案错误时测试成功");
    }

    @Test
    void shouldReturnCEWhenCodeHasSyntaxError() throws IOException, InterruptedException {
        String code = """
                public class Main {
                    public static void main(String[] args) { System.out.println("hello judge") }
                }
                """;
        JudgeResult r = judgeService.judge(code, "hello judge");
        assertEquals("CE", r.verdict(),"CE 编译时异常测试成功");
    }

    @Test
    void shouldReturnREWhenProgramThrows() throws IOException, InterruptedException {
        String code = """
                public class Main {
                    public static void main(String[] args) { System.out.println("hello judge");int i = 2 / 0;}
                }
                """;
        JudgeResult r = judgeService.judge(code, "hello judge");
        assertEquals("RE", r.verdict(),"RE 运行时异常测试成功");
    }

    @Test
    void shouldReturnTLEWhenProgramLoopsForever() throws IOException, InterruptedException {
        String code = """
                public class Main {
                    public static void main(String[] args) { while(true){} }
                }
                """;
        JudgeResult r = judgeService.judge(code, "hello judge");
        assertEquals("TLE", r.verdict(),"TLE 超时测试成功");
    }
}