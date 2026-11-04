# SSDD 前端 · 钢铁表面缺陷检测系统

基于 Vue 3 和 TypeScript 的前后端分离应用，提供登录注册、单张与同步批量缺陷检测、检测历史、统计图表、模型管理和用户管理等页面。`main` 保持 P0～P5 功能范围，异步批量检测与任务进度查询在 `async-batch` 分支提供。

前端通过 HTTP 接口调用 `SSDD-backend`，模型推理、数据库和文件存储均由后端负责。完整系统的初始化步骤见[根目录 README](../README.md)，接口约定见 [API.md](./API.md)。

## 技术栈

以下版本范围来自 `package.json`，实际安装版本由 `pnpm-lock.yaml` 锁定。

| 技术 | 版本范围 | 用途 |
| --- | --- | --- |
| Vue | `^3.5.29` | 页面与组件开发，使用 Composition API |
| TypeScript | `~5.9.3` | 类型约束与开发提示 |
| Vite | `^7.3.1` | 开发服务器、热更新与构建 |
| Vue Router | `^5.0.3` | 页面路由、登录校验和管理员页面访问控制 |
| Pinia | `^3.0.4` | 用户会话、检测与模型状态管理 |
| Element Plus / Icons Vue | `^2.13.2` / `^2.3.2` | 表单、表格、弹窗、上传与图标组件 |
| Axios | `^1.13.6` | 接口请求、令牌注入、会话续期与错误提示 |
| ECharts / Vue ECharts | `^6.0.0` / `^8.0.1` | 缺陷统计与图表展示 |
| Tailwind CSS / Sass | `^4.2.2` / `^1.97.3` | 工具类样式、主题变量和组件样式 |
| Day.js | `^1.11.19` | 日期与时间格式化 |

工程工具包括 `vue-tsc` 类型检查、ESLint、Oxlint 和 Prettier；通过 `unplugin-auto-import` 与 `unplugin-vue-components` 配置常用 API 和组件的自动导入，支持 Vue SFC 与 JSX/TSX。

## 本地环境

- Node.js **24**，与项目现有开发环境保持一致。
- pnpm **11.8.0**，由 `package.json` 的 `packageManager` 字段指定。
- Chrome、Edge 等现代浏览器。
- 联调时需要运行本项目后端，默认地址为 `http://localhost:3000`。

可选编辑器：VS Code + Vue (Official) 扩展。

```bash
node --version
npm install -g pnpm@11.8.0
pnpm --version
```

## 本地开发部署

### 1. 获取代码并安装依赖

首次获取项目：

```bash
git clone https://github.com/Solomon-He/SSDD.git
cd SSDD/SSDD-frontend
```

如果已经克隆仓库，直接进入仓库中的 `SSDD-frontend` 目录。以下 pnpm 命令均在该目录执行：

```bash
pnpm install --frozen-lockfile
```

### 2. 启动后端

按[后端 README](../SSDD-backend/README.md) 配置 MySQL、Redis、本地配置及首次管理员初始化，再启动后端。可访问 `http://localhost:3000/health` 检查服务是否存活；数据库和 Redis 的就绪检查为 `/actuator/health/readiness`。

前端可以独立启动查看登录页面；登录、数据查询与检测等功能需要可用的后端。实际检测还需要管理员在“模型管理”中上传并激活 V2 四通道模型。

### 3. 配置接口地址

仓库中的 `.env.development` 已提供默认配置：

```dotenv
VITE_API_BASE_URL=http://localhost:3000
```

该值为后端服务根地址，不要附加 `/api`，因为接口代码已经包含 `/api/...` 路径。使用默认后端地址时无需修改。

若需要覆盖配置，在前端目录创建被 Git 忽略的 `.env.development.local`：

```dotenv
VITE_API_BASE_URL=http://localhost:3001
```

同时将 `vite.config.ts` 中 `/api` 和 `/uploads` 的代理 `target` 改为实际后端地址。当前接口请求通过完整后端地址直连，开发环境的相对图片地址通过 Vite 代理访问，因此需要同时检查接口地址和代理目标。

后端开发配置默认允许 `http://localhost:5173` 和 `http://127.0.0.1:5173` 跨域访问。若使用其他前端地址或端口，应将实际 origin 加入后端本地配置的 `ssdd.cors.allowed-origins`。

修改环境文件后重启开发服务器。`VITE_` 变量会暴露给浏览器，仅用于公开配置，不要填写数据库密码或后端签名密钥。环境文件加载规则见 [Vite 文档](https://github.com/vitejs/vite/blob/v7.3.5/docs/guide/env-and-mode.md)。

### 4. 启动前端

```bash
pnpm dev --host localhost --port 5173 --strictPort
```

打开 [http://localhost:5173](http://localhost:5173)，使用后端初始化的管理员账号登录，或注册普通用户账号。系统不提供内置默认账号。

`--strictPort` 会在端口被占用时直接报错，避免自动切换端口后与后端跨域配置不一致。也可以直接运行 `pnpm dev`，以终端显示的实际访问地址为准。

## 构建与本地预览

开发环境的 `.env.development` 不会用于默认的生产构建。需要指定构建后的接口地址时，在前端目录创建 `.env.production.local`：

```dotenv
VITE_API_BASE_URL=http://localhost:3000
```

未设置时，请求代码仍回退到 `http://localhost:3000`。然后执行：

```bash
pnpm build
pnpm preview --host localhost --port 5173 --strictPort
```

`pnpm build` 同时进行类型检查和生产构建，输出目录为 `dist/`。预览前先停止占用 5173 端口的开发服务器，随后访问 [http://localhost:5173](http://localhost:5173)。后端需要继续运行，并允许该前端 origin。

接口地址在构建时写入产物；修改 `.env.production.local` 后需要重新构建。生产构建中的相对图片地址会拼接后端根地址，`vite.config.ts` 的开发代理不会被打入静态文件。

`pnpm preview` 用于本地检查构建结果。正式部署时将 `dist/` 交给静态服务器托管；本项目使用 HTML5 History 路由，需要将未命中的前端页面路径回退到 `index.html`，并配置浏览器可访问的后端地址与跨域规则。

## 常用命令

| 命令 | 说明 |
| --- | --- |
| `pnpm dev` | 启动开发服务器 |
| `pnpm build` | 类型检查并生成构建产物 |
| `pnpm preview` | 本地预览已生成的 `dist/` |
| `pnpm type-check` | 单独执行 Vue / TypeScript 类型检查 |
| `pnpm test:p5` | 验证同步批量等前端状态行为 |
| `pnpm lint` | 执行 Oxlint 与 ESLint，包含自动修复，会修改文件 |
| `pnpm format` | 使用 Prettier 格式化 `src/`，会修改文件 |

## 目录结构

```text
src/
├── api/           # HTTP 请求封装与业务接口
├── assets/        # 图片、主题和全局样式
├── components/    # 通用组件、图表和布局
├── composables/   # 可复用页面逻辑
├── constants/     # 缺陷类型、日志动作等常量
├── router/        # 路由配置与访问控制
├── stores/        # Pinia 状态管理
├── types/         # 接口与业务类型
├── utils/         # 存储、图片、下载等工具
└── views/         # 业务页面
tests/             # 前端状态验证
```

## 常见问题

- **页面能打开，但登录或查询报网络错误**：检查后端是否启动、`VITE_API_BASE_URL` 是否正确，以及后端是否允许当前前端 origin。
- **接口正常，但图片不显示**：开发模式检查 `vite.config.ts` 的 `/uploads` 代理；构建预览检查构建时的后端地址，并确认后端图片资源仍有效。
- **提示没有可用模型**：使用管理员账号上传并激活模型，具体步骤见[根目录 README](../README.md)。
- **刷新子页面返回 404**：检查静态服务器是否为 History 路由配置了 `index.html` 回退。
