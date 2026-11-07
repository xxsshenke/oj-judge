-- 手机号注册 / 验证码登录 —— 表结构增量
--
-- 用法：
--   D:\mysql\mysql-8.4.11-winx64\bin\mysql.exe --host=localhost --port=3308 --user=root
--     -e "use oj_judge; source D:/javaProject/oj-judge/sql/alter_user_phone.sql"
--
-- 说明：
--   1. 不改原 user.sql。ALTER 脚本单独放，重跑 user.sql 不会把 phone 列冲掉。
--   2. phone 必须允许 NULL：老用户 admin 没有手机号，
--      未来"只想用密码登录"的账号也不该被迫填手机号。
--      MySQL 的 UNIQUE 允许多个 NULL，所以这些行不会互相冲突（已实测）。
--   3. 手机号注册的账号 username 也是手机号（见 UserServiceImpl），
--      所以 uk_user_username 其实已经能拦住重复注册；
--      uk_user_phone 是第二道防线 —— 以后支持自定义用户名时它就变成唯一防线。
--   4. password 列是 NOT NULL DEFAULT ''，手机号账号落库是空串而不是 NULL。
--      实测显式写 NULL 会报 ERROR 1048，所以 Service 里必须 setPassword("")。
SET time_zone = '+08:00';

ALTER TABLE `user`
  ADD COLUMN `phone` varchar(20) DEFAULT NULL COMMENT '手机号（注册 / 验证码登录用）' AFTER `username`;

ALTER TABLE `user`
  ADD UNIQUE KEY `uk_user_phone` (`phone`);

-- 验证：两个 NULL 应该能共存
-- insert into `user`(username, password) values ('t_null_1', '');
-- insert into `user`(username, password) values ('t_null_2', '');
-- delete from `user` where username like 't_null_%';
