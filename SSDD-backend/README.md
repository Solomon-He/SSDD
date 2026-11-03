# SSDD 后端

Java 17、Spring Boot 3.5.16，钢铁表面缺陷检测系统的后端工程。目前完成 P0 模型验证和 P1 工程/数据库基础设施，检测模型仅支持 V2。账号、权限及检测业务接口按后续阶段开发。

## 本机启动

工作目录：`D:\开发\SSDD\SSDD-backend`。

1. 使用 JDK 17，IDEA 的 Project SDK、Maven runner/importer 也选择 17；Maven home 选择项目 Wrapper（3.9.16）。
2. 确认 MySQL 运行。本机已创建 `ssdd`，无需重复建库；新环境可执行 `scripts/sql/create-database.sql`。
3. 本机连接信息已放在根目录 `application-local.yml`，该文件被 `.gitignore` 排除，不会打进 JAR。新环境复制 `application-local.example.yml` 并填写，或设置 `SSDD_DB_USERNAME`、`SSDD_DB_PASSWORD`。
4. 在后端根目录运行：

```powershell
.\mvnw.cmd spring-boot:run
```

默认使用 `dev` profile、端口 3000、数据库 `127.0.0.1:3306/ssdd`，兼容现有前端。Flyway 会自动校验并执行尚未运行的迁移。当前本机 MySQL 为 **8.0.34**，本轮在此版本验证；MySQL 8.4 容器路径尚未在本机执行。

若系统默认仍是 JDK 25，需先将当前终端的 `JAVA_HOME`/`PATH` 指向 JDK 17。本轮临时验证 JDK 放在 `target/p1-tools/jdk17/`；日常 IDEA SDK 应使用稳定安装目录，避免清理 target 后失效。

## 可用入口

| 入口 | 用途 |
| --- | --- |
| `http://localhost:3000/health` | 基础存活，返回 `{status,timestamp,version}` |
| `http://localhost:3000/actuator/health/readiness` | 数据库 readiness，只返回状态，不返回连接详情 |
| `http://localhost:3000/actuator/health/liveness` | 进程 liveness |
| `http://localhost:3000/swagger-ui.html` | 开发环境 API 文档页面 |
| `http://localhost:3000/v3/api-docs` | 开发环境 OpenAPI JSON |

当前 API 文档只承诺已实现的健康接口。`/api/auth`、`/api/detect` 等业务接口尚未实现，返回统一 404；表已建成不表示业务功能已完成。

## 配置

| 环境变量 | 默认/要求 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | 默认 dev；部署时设 prod |
| `SSDD_PORT` | 3000 |
| `SSDD_DB_URL` | dev 默认本机 ssdd；prod 必填完整 MySQL JDBC URL |
| `SSDD_DB_USERNAME`、`SSDD_DB_PASSWORD` | 环境变量或 dev 的 application-local.yml；缺失时启动失败并给出配置提示 |
| `SSDD_DATA_DIR` | dev 默认 ./var/ssdd；prod 必填，P3/P4 再实现实际存储操作 |
| `SSDD_CORS_ALLOWED_ORIGINS` | prod 可设逗号分隔的明确 origin；默认不开放跨域 |

开发环境默认允许 `http://localhost:5173`、`http://127.0.0.1:5173`。若 Vite 使用其他端口，在本地配置中覆盖 `ssdd.cors.allowed-origins`。CORS 仅作用于 `/api/**`，允许 Bearer/请求 ID/幂等键请求头，暴露下载文件名和请求 ID 响应头。

生产 profile 不读取开发凭据文件，Swagger/OpenAPI 默认关闭，Actuator 只暴露 health。部署时显式提供数据库 URL、凭据和数据目录。数据库连接采用 UTC 会话时间，业务时间对外使用 ISO 8601。

## 数据库迁移

- `V1__create_users_and_sessions.sql`：用户、资料、稳定登录会话、刷新令牌历史。
- `V2__create_v2_models.sql`：V2 模型元信息和唯一活动模型指针。
- `V3__create_detections_and_audit.sql`：检测批次、检测记录、类别统计、操作日志。

共 10 张业务表，另有 `flyway_schema_history`。初始只插入一行空活动模型指针，不创建业务账号，不自动导入模型。已有迁移应用后不要改内容；后续变更新增 `V4__...sql`。Flyway clean 和自动 baseline 已关闭，不能用开启 baseline 掩盖非空库的结构差异。

## 构建与测试

```powershell
.\mvnw.cmd verify
```

Web/配置测试无需数据库。真实 MySQL 集成测试支持两种方式：本地显式配置，或可用 Docker 下由 Testcontainers 启动 MySQL；两者均不可用时集成测试会跳过，不表示数据库已通过验收。

本地集成测试配置（凭据通过本机环境注入）：

```powershell
$env:SSDD_TEST_MYSQL_ENABLED='true'
$env:SSDD_TEST_MYSQL_USERNAME='root'
$env:SSDD_TEST_MYSQL_PASSWORD='<本机密码>'
.\mvnw.cmd verify
```

可选 `SSDD_TEST_MYSQL_ADMIN_URL` 指向 MySQL 服务根 URL，不能包含业务库名。测试创建随机 `ssdd_p1_test_<32位hex>` 库，结束后仅清理自己创建的库；不向 `ssdd` 写测试数据。Docker 默认镜像 `mysql:8.4.8`，可用 `SSDD_TEST_MYSQL_IMAGE` 覆盖。

完整验收还可运行 `scripts/Verify-P1.ps1 -JavaExecutable '<JDK17>/bin/java.exe'`，需为 prod 子进程提供 `SSDD_DB_URL`、`SSDD_DB_USERNAME`、`SSDD_DB_PASSWORD`、`SSDD_DATA_DIR`。脚本在端口 3000 临时启动 dev、dev 重启和 prod，验证后停止自身启动的进程；不会停止其他已占用端口的服务。首次运行会应用 `ssdd` 的迁移。

## 目录与文档

```text
src/main/java/org/ssdd/
  common/api/          统一响应与分页
  common/exception/    统一错误映射
  common/web/          请求 ID 与 Servlet 错误兜底
  config/              数据库、跨域、分页、OpenAPI 配置
  health/              存活检查
src/main/resources/
  db/migration/        Flyway SQL
models/                原始 V2 ONNX，不公开、不打入 JAR
test-images/           11 张 P0 验证样例，不打入 JAR
tools/p0-model-validation/
scripts/               建库及打包烟雾验证
docs/p0/               模型契约及验证结果
docs/p1/               工程和数据库验收结果
```

- [P0 验证报告](docs/p0/P0契约与模型验证报告.md)
- [P1 实施与验收报告](docs/p1/P1工程与数据库验收报告.md)
- [总体技术选型与开发计划](../docs/后端技术选型与开发计划.md)
