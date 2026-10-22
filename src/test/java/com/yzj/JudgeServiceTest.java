package com.yzj;

import com.yzj.pojo.JudgeResult;
import com.yzj.service.impl.JudgeServiceImpl;
import com.yzj.service.JudgeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JudgeServiceTest {

    private final JudgeService judgeService = new JudgeServiceImpl();

    @Test
    void shouldReturnACWhenOutputMatches() {
        String code = """
                public class Main {
                    public static void main(String[] args) { System.out.println("hello judge"); }
                }
                """;
        JudgeResult r = judgeService.judge(code, "hello judge");
        assertEquals("AC", r.verdict(), "AC 答案正确时测试失败");
    }

    @Test
    void shouldReturnWAWhenOutputDiffers() {
        String code = """
                public class Main {
                    public static void main(String[] args) { System.out.println("WA"); }
                }
                """;
        JudgeResult r = judgeService.judge(code, "hello judge");
        assertEquals("WA", r.verdict(), "WA 答案错误时测试失败");
    }

    @Test
    void shouldReturnCEWhenCodeHasSyntaxError() {
        String code = """
                public class Main {
                    public static void main(String[] args) { System.out.println("hello judge") }
                }
                """;
        JudgeResult r = judgeService.judge(code, "hello judge");
        assertEquals("CE", r.verdict(), "CE 编译时异常测试失败");
    }

    @Test
    void shouldReturnREWhenProgramThrows() {
        String code = """
                public class Main {
                    public static void main(String[] args) { System.out.println("hello judge");int i = 2 / 0;}
                }
                """;
        JudgeResult r = judgeService.judge(code, "hello judge");
        assertEquals("RE", r.verdict(), "RE 运行时异常测试失败");
    }

    @Test
    void shouldReturnTLEWhenProgramLoopsForever() {
        String code = """
                public class Main {
                    public static void main(String[] args) { while(true){} }
                }
                """;
        JudgeResult r = judgeService.judge(code, "hello judge");
        assertEquals("TLE", r.verdict(), "TLE 超时测试失败");
    }
}
