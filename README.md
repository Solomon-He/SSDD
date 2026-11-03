# SSDD · 钢铁表面缺陷检测系统

钢铁表面缺陷分割与质检管理系统，采用 Vue 前端与 Java 后端，模型仅支持 V2 四通道多标签语义分割。

## 当前进度

- **前端**：已有登录注册、检测任务、检测历史、模型管理、用户管理、日志及统计页面。
- **P0 已完成**：两个 ONNX 模型通过 Java 17 CPU 试跑，核对训练源码契约，并完成参考样例 Java/Python 数值和掩码对齐。
- **P1 已完成**：后端工程、配置、MySQL/Flyway、统一响应与异常、分页、OpenAPI、健康检查；16 个测试通过。
- **后续阶段**：P2 账号与权限、P3 模型管理、P4 单张检测、P5 批量与数据功能、P6 联调部署。

前端页面完整度不代表后端业务接口已经实现；当前后端仅提供基础设施和健康入口。

## 技术栈

| 部分 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia、ECharts、pnpm |
| 后端 | Java 17、Spring Boot 3.5.16、Maven Wrapper 3.9.16 |
| 数据库 | MySQL、MyBatis-Plus 3.5.18、Flyway；本机验收版本 MySQL 8.0.34 |
| 接口文档 | springdoc-openapi 2.8.17 |
| 模型验证 | ONNX Runtime Java 1.31.1、CPU、V2 四通道 logits |
| 权重管理 | Git LFS |

## 获取项目和模型

安装 Git LFS 后克隆：

```bash
git lfs install
git clone https://github.com/Solomon-He/SSDD.git
cd SSDD
git lfs pull
```

两个模型位于 `SSDD-backend/models/`。若未安装 Git LFS，这些文件可能只是小型指针文本，不能用于推理。文件校验清单见后端 P0 报告目录。

## 启动

后端需要 JDK 17 和 MySQL。在 `SSDD-backend` 工作目录中：

1. 新环境执行 `scripts/sql/create-database.sql` 创建独立 `ssdd` 数据库。
2. 复制 `application-local.example.yml` 为 `application-local.yml`，填写自己的连接信息，或设置环境变量。
3. 执行 `mvnw.cmd spring-boot:run`（Windows）或 `./mvnw spring-boot:run`。

默认端口 3000。开发入口为 `/health`、`/actuator/health/readiness`、`/swagger-ui.html`，具体说明见 [后端 README](SSDD-backend/README.md)。不要提交本地连接配置。

前端需要符合 `package.json` 要求的 Node.js，在 `SSDD-frontend` 中执行：

```bash
pnpm install
pnpm dev
```

开发环境请求后端 `http://localhost:3000`。已在 `pnpm-workspace.yaml` 中声明所需依赖的构建脚本许可。

## 目录

```text
SSDD-frontend/          Vue 前端及接口约定
SSDD-backend/
  src/                 Java 业务工程和测试
  models/              两个 V2 ONNX 原件（LFS）
  test-images/         11 张模型验证图片
  tools/               P0 诊断工具
  scripts/             建库与验收脚本
  docs/                P0/P1 报告和验证证据
docs/                  总体技术方案与开发计划
AGENTS.md              协作与模块提交约定
```

## 文档

- [技术选型与开发计划](docs/后端技术选型与开发计划.md)
- [前端 API 约定](SSDD-frontend/API.md)
- [P0 契约与模型验证](SSDD-backend/docs/p0/P0契约与模型验证报告.md)
- [P1 工程与数据库验收](SSDD-backend/docs/p1/P1工程与数据库验收报告.md)

后续每个模块在对应验证通过后进行一次 Git 提交。当前统一仓库从前后端现有代码及 P0/P1 成果建立初始快照；原前端独立仓库的历史保留在原远程及本地元数据备份中。
