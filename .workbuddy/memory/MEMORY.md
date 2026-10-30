# 项目长期记忆 — oj-judge

## 技术栈
- 后端 Spring Boot 4.0.8 + Java 21 + MyBatis + MySQL(3308) + Redis(6379) + RabbitMQ(5672)；判题仅支持 Java（`Main.java`，javac/java 子进程，2s 限制，容器 256MB）。
- 前端 Vue3 + Element Plus + Pinia + Vue Router（hash 模式），源码在 `src/main/resources/static/vue3`，构建产物输出到 `src/main/resources/static`。

## 前端约定
- 开发：在 `static/vue3` 下 `npm run dev`（默认 5173，proxy `/problems`、`/problem-sets`、`/submissions`、`/users` 到 8080）。
- 发布：`npm run build`（outDir 为 `../`，即 static 根目录）。
- 解题页布局参考牛客：左侧题面 + 右侧深色 Monaco 风格编辑器；编辑器内核为 CodeMirror 6。
- 代码草稿存 localStorage：`oj:draft:<problemId>`。

## 用户模块
- 表 `user`（SQL 在 `sql/user.sql`），admin 固定 id=1，rating 默认 1000；登录未实现前 `/users/current` 返回 id=1。
- 分层：UserController → UserService/Impl → UserMapper(+XML)；仅 current / byId 两个查询接口，password 不序列化。
- 前端个人主页 `/user`（UserView.vue）；解题页与提交页右上角头像可进入。

## 题库模块
- 表 `problem` 增加 `tags`（英文逗号分隔），示例题数据在 `sql/problemset.sql`；id 1–11，简单 7 / 中等 3 / 困难 1。
- 分层：ProblemSetController → ProblemSetService/Impl → ProblemSetMapper(+XML)；仅 overview 与 problems 两个查询接口。
- 题库页 `/problemset` 是默认页；支持标签、难度、关键字筛选，搜索框输入题号回车直达；行点击进解题页。
- 完成状态与通过率按全部 submission 聚合（登录前视为 admin）；`user.solved_count` 尚未联动。
- `ProblemMapper.findAll/findDetailById` 必须查询 `input/expected`，否则解题页示例区为空。

## 测试点与题目描述
- `test_case` 已落 147 组：`sql/testcase.sql` 为数据，`sql/gen_testcases.py` 为生成器（含参考实现与校验）。
- 1 号题 1 组，其余每题 12–17 组；题内测试点分数合计 100（OI 用，ACM 忽略）。
- 外键 `fk_test_case_problem`（`problem_id` → `problem.id`，ON DELETE CASCADE）；`test_case.input/expected` 为 MEDIUMTEXT。
- `problem.input/expected` 是页面样例，必须与第 1 组测试点一致；重新生成测试点时同步更新（生成器内置三者一致性断言）。
- `JudgeWorker` 不再在测试点为空时退化判样例；新题必须先配 test_case，否则判 SE。
- 两数之和必须保证唯一解且输出 i<j；二分查找必须严格递增；空串不纳入用例（无输入与空输入不可区分）。

## 已知限制（改功能需后端配合）
- 无 `/run` 自测运行接口；无多语言判题；Problem 无时间/空间字段；submission 无内存统计、无 user_id。
- 判题名义 2s 时限包含 Docker 启动，`RUN_TIMEOUT_SECONDS=5`：慢启动可能误判 TLE，长程序也可能拖到 5s；每个测试点单独起容器，未批量复用。
- 题库页的「已通过」与通过率为全站聚合，未按用户隔离；个人主页的 solved_count 仍是静态种子值。
- 排名、个人全部提交、成就、Rating 曲线均因缺少对应数据/接口，前端为预留占位。
- MySQL 时区为 UTC，Java 为 +08；跨端写时间字段注意 8 小时偏差。
