# SSDD 后端

Java 17、Spring Boot 3.5.16、MySQL / Flyway、Redis、ONNX Runtime Java 1.31.1。仅支持 V2 四通道多标签模型。main 提供单图与同步批量；async-batch 另含 P5.5 持久异步批量。

克隆、数据库创建、前端启动和首次模型导入步骤见 [根 README](../README.md)。本文件中的命令均在后端根目录执行；IDEA 的 Project SDK、Maven runner/importer 也应选择 JDK 17，Maven 使用项目 Wrapper 3.9.16。

## 本地配置

复制 `application-local.example.yml` 为 `application-local.yml`。dev profile 读取当前工作目录中的该文件。首次可配置为：

```yaml
ssdd:
  database:
    username: ssdd_app
    password: '<自己的数据库密码>'
  auth:
    signing-key: '<至少32个随机字节的Base64密钥>'
  bootstrap-admin:
    enabled: true
    email: '<自己的管理员邮箱>'
    display-name: '<显示名>'
    password: '<自己的管理员密码>'
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
      # 有认证时填写 username/password
```

数据库默认连接本机 3306 的 `ssdd`。其他地址可设置 `SSDD_DB_URL`，或在上述 `ssdd.database` 下添加完整的 `url`。管理员仅在显式启用时创建；已有管理员不会被重置密码或被默认账号替换。成功创建后关闭 bootstrap 开关，再使用该账号登录。

PowerShell 生成随机签名密钥：

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()
```

将输出保存到本地配置，勿提交或发送给他人。数据默认写入 `./var/ssdd`；该目录在首次写入时创建，不需要从仓库获取。

## 启动与入口

```powershell
.\mvnw.cmd spring-boot:run
# 或先打包再运行：
.\mvnw.cmd -DskipTests package
java -jar target/SSDD-backend-0.0.1-SNAPSHOT.jar
```

Linux/macOS 将 `mvnw.cmd` 换成 `./mvnw`。`-DskipTests package` 只表示生成工件，不是完成测试。默认端口 3000，开发环境允许前端 localhost/127.0.0.1:5173。

| 入口 | 用途 |
| --- | --- |
| `/health` | 基础存活 |
| `/actuator/health/readiness` | MySQL 与 Redis 就绪状态 |
| `/actuator/health/inference` | 活动模型就绪状态；未激活模型时为 503 |
| `/swagger-ui.html`、`/v3/api-docs` | dev 下的接口文档 |
| `/api/auth`、`/api/admin/users` | 账号、会话、权限和用户管理 |
| `/api/models`、`/api/models/runtime` | 模型上传、激活、元信息与运行状态 |
| `/api/detect`、`/api/detect/batch` | 单图、最多20张的同步批量检测 |
| `/api/detect/records`、`/api/logs` | 权限内的历史与日志，支持 CSV 导出 |
| `/api/admin/stats` | 管理员统计 |
| `/api/detect/tasks` | 仅 async-batch：POST 202 受理，GET /{UUID} 查询任务 |

## CPU、GPU 与模型

默认 CPU 工件不依赖 CUDA。两个原始模型必须通过 Git LFS 获取，再由管理员从模型管理页面上传和激活；模型、测试图片都不打入业务 JAR。运行权重存于数据目录的 models 子目录，不提供公开下载。

GPU 环境须安装与 ORT GPU 1.31.1 匹配的驱动、CUDA/cuDNN。本机已验证 Windows / RTX 5080 / CUDA 13 / cuDNN 9.20；其他组合需要独立验证。构建和启动示例：

```powershell
.\mvnw.cmd -Pgpu -DskipTests package
$env:PATH = '<已验证 CUDA/cuDNN DLL 目录>;' + $env:PATH
$env:SSDD_INFERENCE_DEVICE = 'cuda'
java -jar target/SSDD-backend-0.0.1-SNAPSHOT-gpu.jar
```

设备支持 cpu/cuda/auto，强制 cuda 初始化失败不回退；auto 仅在初始化或预热失败时回退并记录原因。给 CPU 工件设置 cuda 不会增加 CUDA 能力。不要在同一工件中混用 CPU/GPU 两种 ORT 依赖。

async-batch 的异步任务固定提交时模型、参数与实际设备。MySQL 保存状态和恢复依据，Redis 缓存进度，丢失可回查数据库；认证及检测限流仍保留对 Redis 的依赖。默认待处理容量 8、最多尝试3次、单次预算5分钟、总期限1小时；成功后整批提交，暂停浏览器查询不取消服务器任务。`SSDD_TASK_WORKER_ENABLED=false` 可暂停消费，仍可按容量受理。当前为单实例受管文件方案，不提供分布式 GPU 调度或计算恰好一次保证。

## 迁移、数据与部署

Flyway 从 `src/main/resources/db/migration/` 自动执行迁移；main 为 V5 / 11 张业务表，async-batch 为 V6 / 13 张，另有 flyway_schema_history。迁移不导入业务账号、模型或旧结果，clean 与自动 baseline 关闭。

两个分支部署到不同数据库及数据目录。已经执行 V6 的数据库不能直接由只有 V5 迁移的 main 使用；切换分支验证时用独立空库，勿删除迁移历史来规避校验。

prod 不读取开发本地配置，需要在进程环境中提供：

| 变量 | 要求 / 默认值 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | prod |
| `SSDD_DB_URL` | 完整 MySQL JDBC URL，建议包含 connectionTimeZone=UTC、forceConnectionTimeZoneToSession=true |
| `SSDD_DB_USERNAME`、`SSDD_DB_PASSWORD` | 自己的数据库账号与密码 |
| `SSDD_JWT_SIGNING_KEY` | 至少32随机字节的 Base64，稳定保存 |
| `SSDD_DATA_DIR` | 可写、持久的数据绝对路径 |
| `SSDD_REDIS_HOST/PORT/DATABASE` | 默认 127.0.0.1 / 6379 / 0 |
| `SSDD_REDIS_USERNAME/PASSWORD` | 有认证时填写 |
| `SSDD_REDIS_NAMESPACE` | 不同应用/环境使用不同值 |
| `SSDD_PORT` | 默认 3000 |
| `SSDD_CORS_ALLOWED_ORIGINS` | 逗号分隔的明确前端 origin |
| `SSDD_BOOTSTRAP_ADMIN_ENABLED/EMAIL/NAME/PASSWORD` | 首次初始化需要时配置，完成后关闭 |
| `SSDD_INFERENCE_DEVICE`、`SSDD_GPU_DEVICE_ID` | 默认 cpu / 0 |

prod 默认关闭 Swagger/OpenAPI，仅暴露受限 health。前端构建前配置 `VITE_API_BASE_URL` 指向后端，静态服务器需要支持 SPA 路由回退，后端 CORS 配置为该前端 origin。直接连接 IP 的限流策略未配置可信代理，不信任任意客户端转发头。

头像、权重副本、检测原图及 PNG、导出临时文件和异步原始输入都在数据目录内。结果图片通过授权后签发的短期 URL 读取，URL 过期后重新查询详情；模型文件没有公开下载路由。数据库与数据目录应配套备份和恢复。

## 构建与测试

JDK17 执行 `mvnw.cmd verify`。完整测试需要 MySQL 和 Redis；默认 MySQL 通过 Testcontainers 创建临时容器，也可显式使用本地 MySQL：

```powershell
$env:SSDD_TEST_MYSQL_ENABLED = 'true'
$env:SSDD_TEST_MYSQL_USERNAME = '<允许创建测试库的账号>'
$env:SSDD_TEST_MYSQL_PASSWORD = '<测试账号密码>'
$env:SSDD_REDIS_HOST = '127.0.0.1'
$env:SSDD_REDIS_PORT = '6379'
.\mvnw.cmd verify
```

可选 `SSDD_TEST_MYSQL_ADMIN_URL` 需指向服务根 URL，不包含已有库名。测试只创建并删除随机 `ssdd_p1_test_*` 数据库，Redis 使用独立命名空间，不清空用户服务。由于环境不可用而跳过的集成测试不视为验证通过。

原始模型回归需要 LFS 权重及 `test-images/`，固定 CPU 基准和 GPU 容差保留在 `src/test/resources/inference/`，不依赖 docs/tools。已配置 GPU 环境可额外运行 `mvnw.cmd -Pgpu -Dtest=DetectionGpuVerification verify`。

前端测试在前端目录执行 `pnpm test:p5`；async-batch 另执行 `pnpm test:p55`，然后 `pnpm build`。测试入口保存在 tests 下，不依赖 scripts 目录。独立模型诊断工具、个人开发计划和历史验收资料仅在本地保留，不是克隆后构建和运行的依赖。
