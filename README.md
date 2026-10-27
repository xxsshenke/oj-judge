# 在线判题系统（OJ）

用户提交 Java 代码，系统在隔离容器里编译运行，与标准答案比对，
返回 AC / WA / CE / RE / TLE / MLE / OLE 等判定结果。

## 技术栈

- **后端**：Java 21 / Spring Boot 4 / MyBatis / MySQL 8
- **沙箱**：Docker（`eclipse-temurin:21-jdk`）
- **队列**：BlockingQueue + 线程池
- **前端**：Vue 3 / Vite / ElementPlus

## 项目结构

```
com.yzj
├── controller/     接口层（5 个接口）
├── service/        业务层（接口 + impl）
├── mapper/         数据层（MyBatis）
├── pojo/           实体
├── queue/          判题队列 + 判题机池
├── config/         配置
├── exception/      全局异常处理
└── utils/
```

## 判题沙箱

用户代码在独立容器内运行，限制：

| 参数 | 作用 |
|---|---|
| `--network=none` | 断网，防止用户代码外联 |
| `--memory=256m` | 内存上限（超限被 OOM Killer 杀，退出码 137 → 判 MLE） |
| `--memory-swap=256m` | 禁用 swap，让超限立刻被杀而不是变慢 |
| `--cpus=1` | CPU 上限 |
| `--pids-limit=100` | 防止 fork bomb 耗尽宿主机 PID |
| `-v UUID目录:/work` | 每次提交一个独立目录，并发不互相覆盖 |
| `--rm` | 容器退出即删，不留垃圾 |

## 为什么分两个容器

编译失败（CE）和运行失败（RE）是两种反馈。

一次 `bash -c "javac && java"` 只能拿到一个退出码，无法区分，
所以拆成**编译容器** + **运行容器**。

代价是慢一倍，取舍是**正确性 > 性能**。

## 异步判题

接口只做三件事：校验 → 落库（PENDING）→ 入队 → 返回。

判题由后台线程池异步执行，接口响应从 **1500ms 降到 34ms**（实测）。

判题机池的四个设计点：

- **有界队列**（`ArrayBlockingQueue(100)`）：满了 `offer` 返回 false，
  接口直接提示"系统繁忙"，不阻塞请求
- **`take()` + `interrupt()`**：`@PreDestroy` 里打断阻塞的 worker
- **每个任务独立 try-catch**：单个提交失败不会让 worker 线程死掉
- **`@PostConstruct` 恢复**：服务重启时把 `JUDGING` 的置 SE、`PENDING` 的重新入队

## 多测试点

编译一次，运行 N 次（复用 `Main.class`）。

- **ACM 模式**：遇错即停，全过才 AC
- **OI 模式**：跑完所有测试点，按 `score` 累加，返回 PARTIAL

判定顺序：**超时 → 退出码(137/MLE) → 输出超限(OLE) → 退出码非 0(RE) → 输出比对**。

## 踩过的坑

1. **`wsl.exe` 往 stderr 打 UTF-16 警告，混进 out.txt 后
   `Files.readString` 抛 `MalformedInputException` → 所有提交变 SE。**
   修复：分离 stdout/stderr + 容错解码。

2. **`destroyForcibly()` 只杀 docker 客户端，容器仍在跑。**
   修复：`--name` + 超时后 `docker kill`。

3. **`--memory-swap` 不配 `--memory` → docker 直接拒绝启动（exit 125），
   被当成编译失败。**
   修复：两个参数成对出现；退出码 125 判 SE（系统错误）而不是 CE（用户错误）。

4. **并发提交共用一个工作目录 → 结果串号。**
   修复：每次提交 UUID 独立目录。

5. **`redirectOutput` 每次都会创建文件，导致"文件是否存在"无法区分
   "程序有没有输出"，RE 被误判成 WA。**
   修复：先判退出码，再读输出。

## 如何运行

```bash
# 1. 启动 MySQL（Docker）
docker run -d --name mysql84 -p 3308:3306 \
  -e MYSQL_ROOT_PASSWORD=123456 \
  -v mysql84_data:/var/lib/mysql mysql:8.4

# 2. 建库建表（见 src/main/resources/schema.sql）

# 3. 启动后端
mvn spring-boot:run

# 4. 访问（前端已打包进 static/）
open http://localhost:8080
```
