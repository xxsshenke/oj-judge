-- 题库页数据准备：题目标签 + 示例题目
-- 说明：
--   1. 本机 MySQL 服务器时区为 UTC，脚本内按 +08:00 写入时间，避免与 Java 侧差 8 小时
--   2. tags 使用英文逗号分隔，题库页按标签统计与筛选
--   3. 新增题目仅为题库页提供可浏览的示例数据，可按需删除或替换
--   4. 题目描述、样例与测试点以 sql/testcase.sql 为准（可重复执行）
--
-- 设计取舍（为什么这样改库）：
--   - 标签没有单独建 tag / problem_tag 关联表。当前只有十余题、标签是展示和筛选用途，
--     一个 varchar(120) 逗号串足够；代价是无法建索引、无法做标签重命名，
--     标签统计只能在应用层拆分（ProblemSetServiceImpl.buildTagCounts）。
--     如果以后要做“按标签精确检索 + 标签管理”，再拆成关联表更合适。
--   - tags 列放在 difficulty 之后，纯字符串、可为 NULL，老数据不做强制回填，
--     避免 ALTER 时因历史脏数据失败；查询侧对 NULL 已做兼容。
--   - 整个脚本可重复执行：DDL 先查 information_schema 再决定是否执行，
--     补标签只更新空值，插入题目用 INSERT IGNORE，重跑不会报错也不会覆盖人工修改的数据。
SET time_zone = '+08:00';

-- 1. problem.tags 字段（可重复执行）
-- MySQL 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema + PREPARE 模拟：
-- 列不存在才拼出 ALTER，存在则执行 SELECT 1（空操作）。
SET @has_tags := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'problem'
    AND column_name = 'tags'
);
SET @ddl := IF(
  @has_tags = 0,
  'ALTER TABLE `problem` ADD COLUMN `tags` varchar(120) DEFAULT NULL COMMENT ''标签，英文逗号分隔'' AFTER `difficulty`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 现有题目补标签（只在为空时写入，不覆盖已有数据）
--    条件里的 tags IS NULL OR tags = '' 是幂等的关键：
--    重跑脚本不会把人工改过的标签冲掉。
UPDATE `problem` SET `tags` = '字符串,入门' WHERE `id` = 1 AND (`tags` IS NULL OR `tags` = '');
UPDATE `problem` SET `tags` = '数学,入门'   WHERE `id` = 2 AND (`tags` IS NULL OR `tags` = '');

-- 3. 示例题目（固定 id，重复执行不会覆盖已有题目）
--    INSERT IGNORE 遇到主键冲突时静默跳过，不会报错、也不会更新已有行；
--    这是为了“脚本可重复执行”，副作用是：如果库里已存在同 id 但内容不同的题，
--    本脚本不会帮你纠正，需要手工 UPDATE 或先删除该行。
--    description 里的样例与 test_case 第 1 组保持一致（见 sql/testcase.sql）。
INSERT IGNORE INTO `problem`
  (`id`, `title`, `description`, `expected`, `input`, `difficulty`, `tags`, `create_time`, `update_time`, `judge_mode`)
VALUES
  (3, '两数之和',
   '给定一个整数数组 nums 和一个目标值 target，请你在数组中找出和为目标值的两个整数，并返回它们的下标（下标从 0 开始）。\n\n输入格式：\n第一行两个整数 n 和 target，用空格分隔。\n第二行 n 个整数，表示数组 nums。\n\n输出格式：\n输出两个下标，用空格分隔。保证每组输入有且仅有一个答案。',
   '0 1', '4 9\n2 7 11 15', 1, '数组,哈希表', NOW(), NOW(), 'ACM'),
  (4, '回文数',
   '给你一个整数 x，如果 x 是一个回文整数，输出 true；否则输出 false。\n\n回文数是指正序（从左向右）和倒序（从右向左）读都是一样的整数。例如 121 是回文数，而 -121 不是。\n\n输入格式：\n一行一个整数 x。\n\n输出格式：\n输出 true 或 false。',
   'true', '121', 1, '数学', NOW(), NOW(), 'ACM'),
  (5, '有效的括号',
   '给定一个只包括 ''(''、'')''、''{''、''}''、''[''、'']'' 的字符串 s，判断字符串是否有效。\n\n有效字符串需满足：左括号必须用相同类型的右括号闭合；左括号必须以正确的顺序闭合。\n\n输入格式：\n一行一个字符串 s。\n\n输出格式：\n输出 true 或 false。',
   'true', '()[]{}', 1, '栈,字符串', NOW(), NOW(), 'ACM'),
  (6, '爬楼梯',
   '假设你正在爬楼梯，需要 n 阶才能到达楼顶。每次你可以爬 1 或 2 个台阶，问有多少种不同的方法可以爬到楼顶。\n\n输入格式：\n一行一个整数 n（1 ≤ n ≤ 45）。\n\n输出格式：\n输出爬到楼顶的方法数。',
   '8', '5', 1, '动态规划,数学', NOW(), NOW(), 'ACM'),
  (7, '最大子数组和',
   '给你一个整数数组 nums，请找出一个具有最大和的连续子数组（子数组最少包含一个元素），返回其最大和。\n\n输入格式：\n第一行一个整数 n，表示数组长度。\n第二行 n 个整数，表示数组 nums。\n\n输出格式：\n输出连续子数组的最大和。',
   '6', '9\n-2 1 -3 4 -1 2 1 -5 4', 2, '数组,动态规划', NOW(), NOW(), 'ACM'),
  (8, '无重复字符的最长子串',
   '给定一个字符串 s，请找出其中不含有重复字符的最长子串的长度。\n\n输入格式：\n一行一个字符串 s（由英文字母、数字、符号和空格组成）。\n\n输出格式：\n输出最长子串的长度。',
   '3', 'abcabcbb', 2, '字符串,滑动窗口', NOW(), NOW(), 'ACM'),
  (9, '二分查找',
   '给定一个升序排列的整数数组 nums 和一个目标值 target，请使用二分查找找出 target 在数组中的下标，不存在则返回 -1。\n\n输入格式：\n第一行两个整数 n 和 target。\n第二行 n 个升序排列的整数。\n\n输出格式：\n输出 target 的下标（从 0 开始），不存在输出 -1。',
   '4', '6 9\n-1 0 3 5 9 12', 1, '数组,二分查找', NOW(), NOW(), 'ACM'),
  (10, '岛屿数量',
   '给你一个由 ''1''（陆地）和 ''0''（水）组成的二维网格，请计算网格中岛屿的数量。岛屿总是被水包围，并且每座岛屿只能由水平或垂直方向上相邻的陆地连接形成。\n\n输入格式：\n第一行两个整数 m 和 n，表示网格的行数和列数。\n接下来 m 行，每行 n 个用空格分隔的字符（0 或 1）。\n\n输出格式：\n输出岛屿的数量。',
   '3', '4 5\n1 1 0 0 0\n1 1 0 0 0\n0 0 1 0 0\n0 0 0 1 1', 2, '图,深度优先搜索', NOW(), NOW(), 'ACM'),
  (11, '接雨水',
   '给定 n 个非负整数表示每个宽度为 1 的柱子的高度图，计算按此排列的柱子在下雨后能接多少雨水。\n\n输入格式：\n第一行一个整数 n。\n第二行 n 个非负整数，表示柱子的高度。\n\n输出格式：\n输出能接到的雨水总量。',
   '6', '12\n0 1 0 2 1 0 1 3 2 1 2 1', 3, '数组,双指针,单调栈', NOW(), NOW(), 'ACM');
