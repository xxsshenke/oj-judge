-- 题解 + 评论区（回复 / 点赞）三张表
--
-- ⚠️ 必须在 3308 的 oj_judge 里执行（application.yaml 连的是 3308，Docker mysql84）。
--    3306 上也有一个同名库，但只有 problem / submission 两张表、1 题 4 提交 ——
--    在那边执行不会报错，但程序连的是 3308，等于白做。
--
-- 说明：
--   1. 本机 MySQL 服务器时区为 UTC，Java 侧用东八区，
--      脚本内统一按 +08:00 写入，避免 create_time 偏差 8 小时
--   2. 新表统一 utf8mb4_0900_ai_ci（MySQL 8 默认）。
--      注意 problem 表是老表，用的是 utf8mb4_unicode_ci；
--      本模块 JOIN 只用 int 的 id，不比较字符串，所以不会触发
--      "Illegal mix of collations"，但这条要记着
--   3. 不加外键：项目现有表都没有，保持一致；跨表一致性靠代码保证
SET time_zone = '+08:00';


-- ============================================================
-- 1. 题解
-- ============================================================
CREATE TABLE IF NOT EXISTS `solution` (
  `id`            int unsigned NOT NULL AUTO_INCREMENT COMMENT '题解ID',
  `problem_id`    int unsigned NOT NULL                COMMENT '题目ID',
  `user_id`       int unsigned NOT NULL                COMMENT '作者ID',
  `title`         varchar(100) NOT NULL                COMMENT '标题',
  `content`       text         NOT NULL                COMMENT '正文（一期纯文本，保留换行）',
  `like_count`    int          NOT NULL DEFAULT 0      COMMENT '点赞数（冗余，列表页要用）',
  `comment_count` int          NOT NULL DEFAULT 0      COMMENT '评论总数（含回复，增量维护）',
  `status`        tinyint      NOT NULL DEFAULT 0      COMMENT '0 正常 / 1 已删除（软删）',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 一人一题一解：双击提交、脚本刷帖都会被这个唯一索引挡住。
  -- 若以后要放开成"一人多篇解法"，删掉这个索引 + Service 里那段校验即可
  UNIQUE KEY `uk_solution_problem_user` (`problem_id`, `user_id`),
  -- 列表页主查询：WHERE problem_id=? AND status=0 ORDER BY create_time
  KEY `idx_solution_list` (`problem_id`, `status`, `create_time`),
  -- 热榜排序：WHERE problem_id=? AND status=0 ORDER BY like_count
  KEY `idx_solution_hot` (`problem_id`, `status`, `like_count`),
  -- 个人主页"我发过哪些题解"（现在没用，但索引很便宜）
  KEY `idx_solution_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题解';


-- ============================================================
-- 2. 评论（主评论 + 回复，同一张表）
-- ============================================================
--
-- 两层结构的存法：
--   root_id   = 所属的顶层主评论 id；主评论自己为 NULL
--   parent_id = 直接回复的那条评论 id；主评论为 NULL
--
--   id  parent  root
--   1   NULL    NULL     ← 主评论
--   2   1       1        ← 回复主评论
--   3   2       1        ← 回复回复：root 仍是 1，parent 是 2
--
-- 为什么冗余 root_id：这样"查一页主评论的全部回复"只要一条
--   WHERE root_id IN (...)
-- 若只有 parent_id，就得递归展开，分页几乎做不对。
-- 代价是插入时要算对（只有一处），换取所有读取路径都变简单。
-- ============================================================
CREATE TABLE IF NOT EXISTS `solution_comment` (
  `id`            int unsigned NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `solution_id`   int unsigned NOT NULL                COMMENT '所属题解ID',
  `user_id`       int unsigned NOT NULL                COMMENT '作者ID',
  `parent_id`     int unsigned          DEFAULT NULL   COMMENT '直接回复的评论ID；主评论为 NULL',
  `root_id`       int unsigned          DEFAULT NULL   COMMENT '所属顶层主评论ID；主评论为 NULL',
  `reply_to_user` int unsigned          DEFAULT NULL   COMMENT '被回复者ID（= parent 的 user_id，冗余免 JOIN）',
  `content`       varchar(500) NOT NULL                COMMENT '内容',
  `like_count`    int          NOT NULL DEFAULT 0      COMMENT '点赞数（冗余）',
  `status`        tinyint      NOT NULL DEFAULT 0      COMMENT '0 正常 / 1 已删除（软删）',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 查主评论：WHERE solution_id=? AND root_id IS NULL AND status=0 ORDER BY id DESC
  KEY `idx_comment_solution` (`solution_id`, `root_id`, `status`),
  -- 查某批主评论下的全部回复：WHERE root_id IN (...) AND status=0
  KEY `idx_comment_root` (`root_id`, `status`),
  KEY `idx_comment_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题解评论（主评论+回复同表）';


-- ============================================================
-- 3. 点赞记录
-- ============================================================
--
-- 为什么用 target_type + target_id 而不是两张表：
--   题解点赞和评论点赞的逻辑完全一样，拆两张表就要写两遍。
--
-- Redis 里同时有一份 Set（oj:like:solution:{id}），作为"快闸"：
--   SADD 天然幂等 + 原子，同一个人狂点只有第一次返回 1，
--   不需要锁、不需要事务、不需要先查后写。
--
-- 但 Redis 不能是唯一真相源：一旦 FLUSHALL / 卷被删 / 迁移机器，
-- 点赞关系全丢而 like_count 还在 → 显示 87 赞实际 0 条记录，
-- 用户连"取消赞"都取消不了。
-- 所以 MySQL 这张表才是真相源，Redis 只是闸门。
-- ============================================================
CREATE TABLE IF NOT EXISTS `like_record` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `target_type` tinyint  NOT NULL COMMENT '1 题解 / 2 评论',
  `target_id`   int unsigned NOT NULL COMMENT '目标ID',
  `user_id`     int unsigned NOT NULL COMMENT '点赞人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  -- 一人一赞的数据库级保证：就算 Redis 出问题，这里也挡得住重复
  UNIQUE KEY `uk_like` (`target_type`, `target_id`, `user_id`),
  -- 重算某目标的点赞数
  KEY `idx_like_target` (`target_type`, `target_id`),
  -- 查某人赞过哪些
  KEY `idx_like_user` (`user_id`, `target_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点赞记录（题解+评论共用）';
