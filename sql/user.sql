-- 用户信息表（登录功能接入前，暂以 id=1 的 admin 作为默认当前用户）
-- 说明：本机 MySQL 服务器时区为 UTC，而 Java 侧使用东八区；脚本内统一按 +08:00 写入，避免注册时间偏差 8 小时
SET time_zone = '+08:00';

CREATE TABLE IF NOT EXISTS `user` (
  `id`           int unsigned NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`     varchar(50)  NOT NULL                COMMENT '登录用户名',
  `nickname`     varchar(50)           DEFAULT NULL   COMMENT '昵称',
  `password`     varchar(100) NOT NULL DEFAULT ''     COMMENT '密码（登录功能后续实现，暂为空）',
  `email`        varchar(100)          DEFAULT NULL   COMMENT '邮箱',
  `avatar`       varchar(255)          DEFAULT NULL   COMMENT '头像地址',
  `bio`          varchar(255)          DEFAULT NULL   COMMENT '个人简介',
  `school`       varchar(100)          DEFAULT NULL   COMMENT '学校',
  `rating`       int          NOT NULL DEFAULT 1000   COMMENT 'Rating 积分',
  `solved_count` int          NOT NULL DEFAULT 0      COMMENT '通过题目数',
  `submit_count` int          NOT NULL DEFAULT 0      COMMENT '提交次数',
  `role`         varchar(16)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN / USER',
  `status`       tinyint      NOT NULL DEFAULT 1      COMMENT '状态：1 正常，0 禁用',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户信息表';

-- 第一个用户固定为 admin（id=1），也是登录功能上线前的默认当前用户
INSERT IGNORE INTO `user`
  (`id`, `username`, `nickname`, `password`, `bio`, `rating`, `solved_count`, `submit_count`, `role`, `status`)
VALUES
  (1, 'admin', '管理员', '', '这个人很懒，什么都没有留下', 1000, 0, 0, 'ADMIN', 1);
