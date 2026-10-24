package com.yzj;

import com.yzj.pojo.JudgeResult;
import com.yzj.pojo.TestCase;
import com.yzj.service.impl.JudgeServiceImpl;
import com.yzj.service.JudgeService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeServiceTest {

    private final JudgeService judgeService = new JudgeServiceImpl();

    /** 构造一道「只有一个测试点、没有输入」的题 */
    private static List<TestCase> noInput(String expected) {
        return List.of(new TestCase(null, 1, null, expected, 100, 0));
    }

    /** 构造一道「只有一个测试点、带输入」的题 */
    private static List<TestCase> withInput(String input, String expected) {
        return List.of(new TestCase(null, 1, input, expected, 100, 0));
    }

    // ==================== ACM 模式 ====================

    @Test
    void shouldReturnACWhenOutputMatches() {
        String code = """
                public class Main {
                    public static void main(String[] args) { System.out.println("hello judge"); }
                }
                """;
        JudgeResult r = judgeService.judge(code, noInput("hello judge"), false);
        assertEquals("AC", r.verdict(), "AC 答案正确时测试失败");
    }

    @Test
    void shouldReturnWAWhenOutputDiffers() {
        String code = """
                public class Main {
                    public static void main(String[] args) { System.out.println("WA"); }
                }
                """;
        JudgeResult r = judgeService.judge(code, noInput("hello judge"), false);
        assertEquals("WA", r.verdict(), "WA 答案错误时测试失败");
    }

    @Test
    void shouldReturnCEWhenCodeHasSyntaxError() {
        String code = """
                public class Main {
                    public static void main(String[] args) { System.out.println("hello judge") }
                }
                """;
        JudgeResult r = judgeService.judge(code, noInput("hello judge"), false);
        assertEquals("CE", r.verdict(), "CE 编译时异常测试失败");
    }

    @Test
    void shouldReturnREWhenProgramThrows() {
        String code = """
                public class Main {
                    public static void main(String[] args) { System.out.println("hello judge");int i = 2 / 0;}
                }
                """;
        JudgeResult r = judgeService.judge(code, noInput("hello judge"), false);
        assertEquals("RE", r.verdict(), "RE 运行时异常测试失败");
    }

    @Test
    void shouldReturnTLEWhenProgramLoopsForever() {
        String code = """
                public class Main {
                    public static void main(String[] args) { while(true){} }
                }
                """;
        JudgeResult r = judgeService.judge(code, noInput("hello judge"), false);
        assertEquals("TLE", r.verdict(), "TLE 超时测试失败");
    }

    @Test
    void shouldReturnMLEWhenMemoryExceeded() {
        String code = """
                import java.util.*;
                public class Main {
                    public static void main(String[] args) {
                        List<byte[]> list = new ArrayList<>();
                        while (true) {
                            list.add(new byte[1024 * 1024]);
                        }
                    }
                }
                """;
        JudgeResult r = judgeService.judge(code, noInput(""), false);
        assertEquals("MLE", r.verdict(), "内存超限时应该判 MLE");
    }
    @Test
    void shouldReturnOLEWhenOutputTooLarge() {
        String code = """
            public class Main {
                public static void main(String[] args) {
                    while (true) System.out.println("HACK HACK HACK HACK HACK");
                }
            }
            """;
        JudgeResult r = judgeService.judge(code, noInput("x"), false);
        assertTrue(List.of("OLE","TLE").contains(r.verdict()));
    }

    // ==================== 输入 ====================

    @Test
    void shouldReadStdinInput() {
        String code = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner s = new Scanner(System.in);
                        System.out.println(s.nextInt() + s.nextInt());
                    }
                }
                """;
        JudgeResult r = judgeService.judge(code, withInput("1 2", "3"), false);
        assertEquals("AC", r.verdict(), "带输入的题应该能读到 stdin");
    }

    // ==================== 多测试点 ====================

    @Test
    void shouldPassAllTestCasesInAcmMode() {
        String code = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner s = new Scanner(System.in);
                        System.out.println(s.nextInt() + s.nextInt());
                    }
                }
                """;
        List<TestCase> cases = List.of(
                new TestCase(null, 1, "1 2", "3", 100, 0),
                new TestCase(null, 1, "10 20", "30", 100, 1),
                new TestCase(null, 1, "-5 5", "0", 100, 2)
        );
        JudgeResult r = judgeService.judge(code, cases, false);
        assertEquals("AC", r.verdict(), "所有测试点都通过应该 AC");
    }

    @Test
    void shouldFailFastInAcmMode() {
        String code = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner s = new Scanner(System.in);
                        System.out.println(s.nextInt() - s.nextInt());   // 故意算错
                    }
                }
                """;
        List<TestCase> cases = List.of(
                new TestCase(null, 1, "1 2", "3", 100, 0),
                new TestCase(null, 1, "10 20", "30", 100, 1)
        );
        JudgeResult r = judgeService.judge(code, cases, false);
        assertEquals("WA", r.verdict(), "ACM 模式第一个点错就返回 WA");
    }

    // ==================== OI 模式 ====================

    @Test
    void shouldGivePartialScoreInOiMode() {
        // 只能算对和为 3 的情况，其余算错
        String code = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner s = new Scanner(System.in);
                        int a = s.nextInt(), b = s.nextInt();
                        System.out.println(a + b == 3 ? 3 : 999);
                    }
                }
                """;
        List<TestCase> cases = List.of(
                new TestCase(null, 1, "1 2", "3", 40, 0),
                new TestCase(null, 1, "10 20", "30", 30, 1),
                new TestCase(null, 1, "5 5", "10", 30, 2)
        );
        JudgeResult r = judgeService.judge(code, cases, true);

        assertEquals("PARTIAL", r.verdict(), "只过一部分应该返回 PARTIAL");
        assertEquals(40, r.score(), "应该拿到第 1 个测试点的 40 分");
        assertEquals(1, r.passedCount(), "应该只通过 1 个测试点");
        assertEquals(3, r.totalCount(), "总共 3 个测试点");
    }

}
