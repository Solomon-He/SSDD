# SSDD · 钢铁表面缺陷检测系统

Vue 3 + Java 17 / Spring Boot 3.5.16，提供钢铁表面缺陷分割、账号权限、模型管理、历史记录、CSV、操作日志与管理员统计。仅支持 V2 四通道多标签模型，普通构建使用 CPU，也可单独构建 CUDA 工件。

| 分支 | 功能范围 |
| --- | --- |
| `main` | P0～P5：单张与同步批量检测，批量最多 20 张、整批提交 |
| `async-batch` | 包含 main 的功能，并增加 P5.5 异步批量、持久任务、进度查询、受限重试与重启恢复 |

## 环境要求

- Git 和 Git LFS，用于获取两个原始 ONNX 权重。
- JDK 17，使用仓库自带 Maven Wrapper，无需单独安装 Maven。
- MySQL 8.0 或 8.4；Redis 服务。已执行验证的数据库版本为 8.0.34 / 8.4.8，Redis 为 8.10.2。
- Node.js 24、pnpm 11.8.0。前端 `package.json` 固定包管理器，`pnpm-lock.yaml` 固定依赖。
- CPU 部署不需要 Python、CUDA 或 GPU。

## 克隆与获取权重

```bash
git lfs install
git clone https://github.com/Solomon-He/SSDD.git
cd SSDD
# 需要异步批量时，切换到 async-batch 分支
# git switch async-batch
git lfs pull
git lfs fsck
```

两个权重在 `SSDD-backend/models/`，分别约 104 MB、115 MB。若文件只有几行指针文本，先完成 LFS 下载，不能直接上传指针文件。

## 初始化数据库

连接自己的 MySQL 服务，用具备建库和授权权限的账号执行以下 SQL；将密码占位符替换为自己的随机密码：

```sql
CREATE DATABASE IF NOT EXISTS ssdd
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'ssdd_app'@'localhost' IDENTIFIED BY '<自己的数据库密码>';
GRANT ALL PRIVILEGES ON ssdd.* TO 'ssdd_app'@'localhost';
```

该示例用于本地 MySQL。容器或其他主机上的数据库应按实际客户端来源配置 MySQL 账号授权。无需手工建表，后端首次启动通过 `src/main/resources/db/migration/` 中的 Flyway 迁移创建表。

## 启动后端

1. 在 `SSDD-backend` 中将 `application-local.example.yml` 复制为 `application-local.yml`。
2. 填写数据库账号、密码和 JWT 签名密钥，配置自己的 Redis 地址、端口和密码。示例 Redis 端口是 **6380**，若服务使用默认端口请改为 **6379**。
3. 首次启动在本地配置中将 `ssdd.bootstrap-admin.enabled` 设为 true，并填写自己的管理员邮箱、显示名和密码。初始化成功后改回 false。
4. 确认 `java -version` 为 17，在后端目录执行：

```powershell
# Windows
.\mvnw.cmd spring-boot:run
```

```bash
# Linux / macOS
./mvnw spring-boot:run
```

生成 JWT 密钥的方法、完整配置和打包运行见 [后端 README](SSDD-backend/README.md)。本地配置不入库，不能复用别人的密钥或账号。

后端默认 `http://localhost:3000`。`/health` 是存活检查，`/actuator/health/readiness` 检查数据库与 Redis；开发环境的 `/swagger-ui.html` 提供接口说明。首次尚未激活模型时推理健康为 503，账号与模型管理仍可使用。

## 启动前端并导入模型

在 `SSDD-frontend` 目录执行：

```bash
# 若尚未安装 pnpm：npm install -g pnpm@11.8.0
pnpm install --frozen-lockfile
pnpm dev
```

打开终端显示的前端地址，默认 `http://localhost:5173`。使用刚初始化的管理员登录，在“模型管理”上传 `SSDD-backend/models/` 中的真实 ONNX 文件，选择 V2 / 四通道，填写模型名称、版本并激活。首次启动不自动导入模型，也不携带任何历史结果或默认账号。

激活后到检测页上传 `SSDD-backend/test-images/` 中的图片即可检测。`async-batch` 分支额外提供“异步批量”；保留任务号可恢复查询，暂停页面轮询不会取消后台任务。

若前端端口不是 5173，在后端本地配置的 `ssdd.cors.allowed-origins` 中加入对应 origin；若后端地址变化，调整前端 `.env.development` 的 `VITE_API_BASE_URL`。详细接口约定见 [API.md](SSDD-frontend/API.md)。

## 构建与本地打包运行

```powershell
# SSDD-backend 工作目录；仅打包，完整测试另见后端 README
.\mvnw.cmd -DskipTests package
java -jar target/SSDD-backend-0.0.1-SNAPSHOT.jar
```

JAR 在后端目录启动时默认使用 dev profile，并读取该工作目录的本地配置。前端在其目录运行 `pnpm build`，然后 `pnpm preview --host 127.0.0.1 --port 5173` 可预览本地构建；正式部署应使用静态服务器并按 [后端 README](SSDD-backend/README.md) 配置 prod 环境。

## 仓库内容

保留前后端源码、数据库迁移、配置示例、构建锁文件、Wrapper、模型 LFS 指针、示例图片和必要测试资源。后端测试所需的固定基准数据放在 `src/test/resources/inference/`，前端状态测试放在 `tests/`。

任意层级的 `docs/`、`scripts/`、`tools/`、`var/` 均被 Git 忽略。它们可在开发者本机保留，克隆后无需这些目录即可构建和运行；运行时会按需创建数据目录。历史提交仍可能包含旧资料，本次清理不改写历史。

账号、上传模型副本、头像、检测结果及异步原始输入保存在配置的数据目录中；`application-local.yml`、环境凭据、依赖和构建产物不提交。日常部署须同时备份 MySQL 和数据目录。

## 干净检出验证

2026-11-04，在仅包含待提交跟踪文件的独立副本中验证；副本不携带上述四类目录、本地配置、依赖或旧构建，依赖按锁文件重新安装。

| 分支 | 后端 JDK17 verify | 前端状态测试与构建 | 独立空库打包启动 |
| --- | --- | --- | --- |
| main | 101 项通过，0 跳过 | 5 项通过，构建通过 | V5 迁移、临时非 root 数据库账号、管理员初始化、模型上传/激活、单图与同步批量通过 |
| async-batch | 123 项通过，0 跳过 | 13 项通过，构建通过 | V6 同流程，并验证异步任务成功发布 |

迁移后的 GPU 测试资源另通过两个模型 × 11 图的原生对照。以上执行环境为本机 Windows、MySQL 8.0.34 和 Redis 8.10.2；HTTP 启动与检测验证不等同于 Linux 部署或浏览器整页 E2E。
