# 钢铁表面缺陷检测系统 — API 接口文档

> 基础地址：`http://localhost:3000`
> 最后更新：2026-11-04（P5：批量、CSV、统计缓存及日志）
>
> 已实现认证、管理员用户、V2 模型、单图/同步批量检测、历史列表/详情、签名图片、CSV、管理员统计缓存、日志及健康检查。异步任务尚未启用。

---

## 目录

- [通用说明](#通用说明)
- [认证接口](#一认证接口-apiauth)
- [检测接口](#二检测接口-apidetect)
- [模型管理](#三模型管理接口-apimodels)
- [管理员接口](#四管理员接口-apiadmin)
- [操作日志](#五操作日志接口-apilogs)
- [其他接口](#六其他接口)
- [附录：缺陷类别定义](#附录a缺陷类别定义)
- [附录：操作日志类型](#附录b操作日志类型-logaction)

---

## 通用说明

### 统一响应格式

所有接口均返回以下 JSON 结构：

```json
{
  "success": true,           // 请求是否成功
  "data": { ... },           // 业务数据（成功时）
  "message": "操作成功",      // 提示消息（可选）
  "error": "错误描述"         // 错误说明（失败时）
}
```

### 分页响应格式

所有分页列表接口的 `data` 字段统一为：

```json
{
  "items": [ ... ],       // 当前页数据列表
  "total": 100,           // 总记录数
  "page": 1,              // 当前页码
  "pageSize": 10,         // 每页条数
  "totalPages": 5         // 总页数
}
```

### 认证方式

除公开接口外，所有接口需在请求头中携带 JWT Token：

```
Authorization: Bearer <access_token>
```

`access_token` 从登录接口获取。

### 角色权限

| 角色 | 标识 | 权限范围 |
|------|------|----------|
| 管理员 | `admin` | 所有接口（含用户管理、模型管理、系统统计） |
| 质检员 | `inspector` | 检测操作、历史查询、模型列表查看 |

### 错误状态码

| 状态码 | 含义 |
|--------|------|
| 400 | 请求参数错误 |
| 401 | 未认证 / Token 无效或过期 |
| 403 | 权限不足（角色不满足要求） |
| 404 | 资源不存在 |
| 409 | 资源冲突（例如邮箱已注册） |
| 429 | 认证请求超频或账号处于冷却，响应头 `Retry-After` 为等待秒数 |
| 503 | 服务不可用（如推理引擎未就绪） |
| 500 | 服务器内部错误 |

P2 认证默认限制（均可配置）：15 分钟内每 IP 登录 60 次、注册 10 次、刷新 120 次，每账号登录 20 次；累计 5 次凭据失败冷却 15 分钟，成功登录清除失败计数。仅使用直接连接 IP，忽略客户端 Forwarded/X-Forwarded-For。Redis 不可用时上述入口返回 503 `限流服务暂不可用`，基础存活不受影响。

### 图片 URL 说明

检测结果中返回的图片 URL 均为**相对路径**，前端需拼接基础地址访问：

```
完整URL = 基础地址 + 图片路径
示例: http://localhost:3000/uploads/results/1708675200000/sample_overlay.png
```

---

## 一、认证接口 (`/api/auth`)

### 1.1 用户注册

```
POST /api/auth/register
```

**权限**: 公开

**请求体** (`application/json`):

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | string | ✅ | 用户邮箱 |
| password | string | ✅ | 至少 6 个字符，UTF-8 编码不超过 72 字节 |
| displayName | string | ❌ | 显示名称（默认取邮箱 @ 前缀） |

**成功响应** `201`:

```json
{
  "success": true,
  "data": {
    "userId": "uuid-string"
  },
  "message": "注册成功，请登录"
}
```

> 本地账号体系，注册后可直接登录，不发送验证邮件。邮箱会去除首尾空白并转为小写；只能注册 `inspector`，客户端不能指定管理员角色。重复邮箱返回 409。

**失败响应** `400`:

```json
{
  "success": false,
  "error": "邮箱和密码为必填项"
}
```

---

### 1.2 用户登录

```
POST /api/auth/login
```

**权限**: 公开

**请求体** (`application/json`):

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | string | ✅ | 用户邮箱 |
| password | string | ✅ | 密码 |

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "user": {
      "id": "auth-user-uuid",
      "email": "user@example.com",
      "profile": {
        "id": "profile-uuid",
        "user_id": "auth-user-uuid",
        "role": "inspector",
        "display_name": "张三",
        "avatar_url": null,
        "created_at": "2026-02-20T10:00:00Z",
        "updated_at": "2026-02-20T10:00:00Z"
      }
    },
    "session": {
      "access_token": "eyJhbGciOi...",
      "refresh_token": "refresh-token-string",
      "expires_at": 1708675200
    }
  }
}
```

> **前端须知**：
> - 将 `access_token` 保存（localStorage / cookie），后续请求放入 `Authorization: Bearer <token>` 头
> - `expires_at` 为 Unix 时间戳（秒），可据此判断 Token 是否过期
> - `profile.role` 决定用户的前端权限显示（路由守卫、菜单隐藏等）

**失败响应** `401`:

```json
{
  "success": false,
  "error": "邮箱或密码错误"
}
```

> 停用账号也返回相同的凭据错误。登录、注册、刷新为公开接口，不使用传入的 Bearer Token。

---

### 1.3 刷新 Token

```
POST /api/auth/refresh
```

**权限**: 公开（无需 Authorization 头）

**请求体** (`application/json`):

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| refresh_token | string | ✅ | 登录时返回的 `refresh_token` |

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "user": {
      "id": "auth-user-uuid",
      "email": "user@example.com",
      "profile": {
        "id": "profile-uuid",
        "user_id": "auth-user-uuid",
        "role": "inspector",
        "display_name": "张三",
        "avatar_url": null,
        "created_at": "2026-02-20T10:00:00Z",
        "updated_at": "2026-02-20T10:00:00Z"
      }
    },
    "session": {
      "access_token": "eyJhbGciOi...(新 Token)",
      "refresh_token": "new-refresh-token-string",
      "expires_at": 1708678800
    }
  }
}
```

> **前端须知**：
> - 刷新成功后需同时更新本地存储的 `access_token` 和 `refresh_token`（每次刷新会签发新的 refresh_token，旧的失效）
> - 当 `access_token` 过期（通过 `expires_at` 判断）时调用此接口，实现静默续期
> - 若 `refresh_token` 也已失效，返回 401，前端应跳转到登录页
> - 默认访问令牌 15 分钟、稳定会话 7 天；刷新轮换不延长稳定会话总寿命
> - 旧刷新令牌重复使用会撤销整个所属会话，已经签发的访问令牌也随之失效；同一令牌的并发请求只允许一次轮换，另一请求触发撤销。前端同一页面共用进行中的刷新请求；多标签页并发刷新仍需避免，跨标签协调暂未实现

**失败响应** `400`:

```json
{
  "success": false,
  "error": "refresh_token 为必填项"
}
```

**失败响应** `401`:

```json
{
  "success": false,
  "error": "refresh_token 无效或已过期，请重新登录"
}
```

---

### 1.4 修改密码

```
PUT /api/auth/password
```

**权限**: 已认证

**请求头**: `Authorization: Bearer <token>`

**请求体** (`application/json`):

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| oldPassword | string | ✅ | 当前密码 |
| newPassword | string | ✅ | 新密码（≥6 个字符，UTF-8 ≤72 字节） |

**成功响应** `200`:

```json
{
  "success": true,
  "message": "密码修改成功，请重新登录"
}
```

**失败响应**:

| 状态码 | 原因 |
|--------|------|
| 400 | 参数错误 / 当前密码错误 / 新旧密码相同 / UTF-8 超过 72 字节 |
| 401 | 未认证或会话已过期 |
| 500 | 服务器内部错误 |

---

### 1.5 用户登出

撤销当前稳定会话，其他设备会话保留；改密则撤销该用户全部会话。受保护请求重新查询数据库中的账号状态、会话及角色，权限变更在后续请求中生效。

```
POST /api/auth/logout
```

**权限**: 已认证

**请求头**: `Authorization: Bearer <token>`

**成功响应** `200`:

```json
{
  "success": true,
  "message": "登出成功"
}
```

---

### 1.6 修改个人信息

```
PUT /api/auth/profile
```

**权限**: 已认证

**请求头**: `Authorization: Bearer <token>`

**请求体** (`application/json`):

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| displayName | string | ❌ | 新的显示名称（不能为空字符串） |
| avatarUrl | string \| null | ❌ | http/https 外部 URL 或自己的当前受管 URL；`null`/空字符串清除，缺省保持原值 |

> 至少提供一个字段，仅更新传入的字段，未传入的字段保持不变。

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "id": "profile-uuid",
    "user_id": "auth-user-uuid",
    "role": "inspector",
    "display_name": "新名称",
    "avatar_url": "https://example.com/avatar.png",
    "created_at": "2026-02-20T10:00:00Z",
    "updated_at": "2026-02-24T10:00:00Z"
  },
  "message": "个人信息修改成功"
}
```

**失败响应**:

| 状态码 | 原因 |
|--------|------|
| 400 | 未提供任何修改字段 / 显示名称为空 |
| 404 | 用户资料不存在 |
| 500 | 服务器内部错误 |

---

### 1.7 上传头像

```
POST /api/auth/avatar
```

**权限**: 已认证

**请求头**: `Authorization: Bearer <token>`

**请求体** (`multipart/form-data`):

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| avatar | File | ✅ | 头像图片文件（支持 JPEG/PNG/WebP/GIF） |

**文件限制**:
- 最大文件大小：2MB
- 支持格式：`image/jpeg`, `image/png`, `image/webp`, `image/gif`

> **行为说明**：
> - 校验实际格式和解码内容；最大 2 MiB，边长 ≤4096、总像素 ≤1600 万
> - GIF/WebP 只取首帧，统一缩放至最长边 512 的静态 PNG，存储于 `SSDD_DATA_DIR/avatars/`
> - 公开 URL `/uploads/avatars/{服务端生成的文件名}.png`；数据库提交后删除自己的旧头像，数据库更新失败补偿删除新文件
> - 自动更新 `profiles.avatar_url` 字段

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "avatar_url": "/uploads/avatars/user-uuid_1708675200000.png",
    "profile": {
      "id": "profile-uuid",
      "user_id": "auth-user-uuid",
      "role": "inspector",
      "display_name": "张三",
      "avatar_url": "/uploads/avatars/user-uuid_1708675200000.png",
      "created_at": "2026-02-20T10:00:00Z",
      "updated_at": "2026-02-27T10:00:00Z"
    }
  },
  "message": "头像上传成功"
}
```

**失败响应**:

| 状态码 | 原因 |
|--------|------|
| 400 | 未上传头像文件 / 不支持的图片格式 / 文件超过 2MB |
| 404 | 用户资料不存在 |
| 500 | 服务器内部错误 |

> **前端须知**：头像 URL 为相对路径，需拼接基础地址访问，如：`http://localhost:3000/uploads/avatars/xxx.png`

---

### 1.8 获取当前用户信息

```
GET /api/auth/me
```

**权限**: 已认证

**请求头**: `Authorization: Bearer <token>`

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "id": "auth-user-uuid",
    "email": "user@example.com",
    "profile": {
      "id": "profile-uuid",
      "user_id": "auth-user-uuid",
      "role": "inspector",
      "display_name": "张三",
      "avatar_url": null,
      "created_at": "2026-02-20T10:00:00Z",
      "updated_at": "2026-02-20T10:00:00Z"
    }
  }
}
```

> **用途**：页面刷新时用已存储的 Token 重新获取用户信息，验证 Token 是否仍然有效。

---

## 二、检测接口 (`/api/detect`)

> 所有检测接口需要认证（`Authorization: Bearer <token>`）

### 2.1 单张图片检测

```
POST /api/detect
```

**权限**: 已认证（admin / inspector）

**请求体** (`multipart/form-data`):

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| image | File | ✅ | 图片文件（支持 JPEG/PNG/BMP） |

**文件限制**:
- 最大文件大小：由环境变量 `MAX_FILE_SIZE` 控制（字节，默认 10485760，即 10 MiB，只可调小）；默认最多 800 万像素、单边 8192。
- 支持实际解码格式 JPEG/PNG/BMP；扩展名及 MIME 不是格式证据。JPEG 应用 EXIF 方向，PNG/BMP 按编码方向，透明像素在白底合成；保存的原图为去元数据 RGB PNG。
- 可选请求头 `Idempotency-Key`：1～128 位 ASCII 字母、数字、点、下划线、冒号、短横线；按用户及原始上传字节 SHA-256 比较。同内容重试返回同一记录与新签名，不同内容或同键处理中返回 409。前端对同一 File 对象失败/取消后的重试保留键，确认成功后下次操作生成新键。
- 每用户默认每分钟 10 次；429 带 Retry-After，Redis 故障 503。流水线默认并发 1，繁忙立即 503；总预算 30 秒，超时 504，资源等待实际原生调用结束后释放。

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "record_id": "detection-record-uuid",
    "model": {
      "id": "model-uuid",
      "name": "UNet-ResNet34",
      "version": "1.0.0"
    },
    "original_image_url": "/uploads/results/1708675200000/sample_original.png",
    "mask_image_url": "/uploads/results/1708675200000/sample_mask.png",
    "overlay_image_url": "/uploads/results/1708675200000/sample_overlay.png",
    "defect_count": 3,
    "defect_class_counts": {
      "1": 2,
      "2": 0,
      "3": 1,
      "4": 0
    },
    "defect_info": {
      "total_pixels": 65536,
      "defect_pixels": 1200,
      "defect_ratio": 0.0183,
      "class_distribution": {
        "1": 500,
        "2": 300,
        "3": 250,
        "4": 150
      }
    },
    "inference_time_ms": 52.3
  }
}
```

**字段说明**:

| 字段 | 说明 |
|------|------|
| `record_id` | 检测记录 ID，可用于后续查询详情 |
| `original_image_url` | 原图的访问路径 |
| `mask_image_url` | 类别索引预览图（灰度值 0～4）；多标签重叠信息以服务端保存的四张二值掩码为准 |
| `overlay_image_url` | 掩码叠加原图的可视化路径（彩色半透明） |
| `defect_count` | 过滤后各类别 8 邻域连通区域数之和；不等同于物理实例数 |
| `defect_class_counts` | 各类别缺陷区域数量（key=类别索引，见附录A） |
| `defect_info.total_pixels` | 图片总像素数 |
| `defect_info.defect_pixels` | 四类二值掩码并集的像素数（重叠位置只计一次） |
| `defect_info.defect_ratio` | 缺陷面积占比 |
| `defect_info.class_distribution` | 各类别像素数分布 |
| `inference_time_ms` | 模型推理耗时（毫秒） |
| `channel_mask_urls` | 新增：key=1～4 的无损二值 PNG 短期签名链接，像素为 0/255 |
| `inference_config_snapshot` | 新增：模型契约、SHA、预处理、原始方向、设备/provider/回退、运行时、FP32 配置快照 |

图片 URL 实际形如 `/uploads/results/{record UUID}/mask.png?expires=...&signature=...`。详情和列表先检查记录权限，再生成最长 15 分钟签名；图片路由验证签名及数据库资源引用，无需 Bearer。返回 image/png 与 Cache-Control:no-store，链接过期后重新查询详情。数据库只存储稳定存储键，日志不记录完整签名 URL。

> **V2 推理契约**：输入为 RGB、float32、NCHW、`[1,3,256,1600]`，数值范围 `[0,255]`。现有模型内置归一化，后端不可再次 `/255` 或 mean/std 标准化。输出为四通道 logits，图外执行 sigmoid → `>=0.5` → 8 邻域小区域过滤（面积至少 150 像素）→ 类别总面积过滤（至少 `[600,600,900,2000]`）。面积阈值在 256×1600 模型空间执行，随后用最近邻恢复到原图；返回像素数按原图空间统计，区域数量沿用过滤后的模型空间计数。
>
> 四类掩码可以重叠，因此 `class_distribution` 的和可能大于 `defect_pixels`。类别预览和叠加图在重叠位置选择最高 logit（与数学概率单调等价），相同值时选择较小类别索引；叠加透明度 0.45，不能用于反推完整多标签结果。非原尺寸使用 half-pixel RGB float32 双线性拉伸，掩码使用 nearest-floor 恢复。当前基线为单次推理，不包含 TTA。

**失败响应**:

| 状态码 | 原因 |
|--------|------|
| 400 | 缺少/非法图片、格式/尺寸/大小不符或幂等键非法 |
| 409 | 幂等内容冲突或同键处理中 |
| 429 | 超过用户提交频率 |
| 503 | 推理引擎未就绪（未激活模型） |
| 504 | 原生推理或总处理预算超时 |
| 500 | 检测或保存过程中出现异常 |

---

### 2.2 批量图片检测

```
POST /api/detect/batch
```

**权限**: 已认证（admin / inspector）

**请求体** (`multipart/form-data`):

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| images | File[] | ✅ | 多个图片文件（最多 20 张） |

每张沿用单图格式/10 MiB/像素限制。先验证全部图片，再用同一模型及设备快照逐张处理；批次、记录、分类统计和成功审计一次事务提交，失败不发布部分成功结果。默认总预算 120 秒，既有原生预算仍按单次调用执行；共用单图用户限流与流水线容量，一批计一次提交。400/409/429/503/504 语义与单图一致。

可选 Idempotency-Key 按用户与接口隔离。批量摘要包含图片顺序、清理后的显示文件名和每张原字节哈希；同键改变任一项返回 409，在途相同键也返回 409，已提交同内容重试返回原批次及新签名 URL。前端同一有序 File 列表失败/取消后保留键，确认成功后生成新键。新增 batch_id，results 顺序保持请求顺序，file_name 仅用于展示，不决定存储路径。

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "model": {
      "id": "model-uuid",
      "name": "UNet-ResNet34",
      "version": "1.0.0"
    },
    "total": 3,
    "results": [
      {
        "record_id": "uuid-1",
        "file_name": "image1.jpg",
        "original_image_url": "/uploads/results/1708675200000/image1_original.png",
        "mask_image_url": "/uploads/results/1708675200000/image1_mask.png",
        "overlay_image_url": "/uploads/results/1708675200000/image1_overlay.png",
        "defect_count": 2,
        "defect_class_counts": { "1": 1, "2": 0, "3": 1, "4": 0 },
        "defect_info": { "total_pixels": 65536, "defect_pixels": 800, "defect_ratio": 0.0122, "class_distribution": { "1": 500, "2": 0, "3": 300, "4": 0 } },
        "inference_time_ms": 48.5
      }
    ]
  }
}
```

---

### 2.3 获取检测历史记录（分页 / 全量）

```
GET /api/detect/records
```

**权限**: 已认证

- **管理员 (admin)**：默认返回所有用户的检测记录，可通过 `userId` 精确筛选或 `search` 按用户名模糊搜索
- **质检员 (inspector)**：仅返回当前用户自己的记录

**Query 参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | number | ❌ | 1 | 页码（≥1） |
| pageSize | number | ❌ | 10 | 每页条数（1-100） |
| all | string | ❌ | - | 传 `true` 时返回全量数据（忽略分页参数，上限 10000 条） |
| userId | string (UUID) | ❌ | - | 按用户 ID 精确筛选（auth user_id，非 profile id；仅管理员可用，质检员传此参数无效） |
| search | string | ❌ | - | 按用户显示名称模糊搜索（仅管理员可用，与 `userId` 互斥，`userId` 优先） |
| startDate / endDate | string | ❌ | - | 带时区 ISO 8601，起止两端包含；与 CSV 复用相同范围 |

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "record-uuid",
        "user_id": "auth-user-uuid",
        "model_id": "model-uuid",
        "original_image_url": "/uploads/results/..._original.png",
        "mask_image_url": "/uploads/results/..._mask.png",
        "overlay_image_url": "/uploads/results/..._overlay.png",
        "defect_count": 3,
        "defect_class_counts": { "1": 2, "2": 0, "3": 1, "4": 0 },
        "defect_info": { ... },
        "inference_time_ms": 52.3,
        "created_at": "2026-02-22T14:30:00Z",
        "models": {
          "name": "UNet-ResNet34",
          "version": "1.0.0"
        },
        "profiles": {
          "role": "inspector",
          "display_name": "张三",
          "avatar_url": null
        }
      }
    ],
    "total": 42,
    "page": 1,
    "pageSize": 10,
    "totalPages": 3
  }
}
```

> **注意**：
> - `user_id` 为本地 `users.id`，而非 `user_profiles.id`。
> - `models` 是关联查询的模型信息子对象，`profiles` 是关联查询的用户资料子对象（含 `role`、`display_name`、`avatar_url`）。

---

### 2.4 导出检测记录

> P5 已实现；最大 10000 条，超过返回 400，先完成生成及导出审计再发送 CSV。

```
GET /api/detect/records/export
```

**权限**: 已认证（admin / inspector）

- **管理员 (admin)**：导出全部检测记录（可筛选），CSV 中包含操作者邮箱列
- **质检员 (inspector)**：仅导出自己的检测记录，CSV 中不含邮箱列

**Query 参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| userId | string (UUID) | ❌ | - | 按用户 ID 精确筛选（仅管理员可用） |
| search | string | ❌ | - | 按用户显示名称模糊搜索（仅管理员可用，与 `userId` 互斥） |
| startDate | string | ❌ | - | 起始日期（ISO 8601，如 `2026-02-20T00:00:00Z`） |
| endDate | string | ❌ | - | 结束日期（ISO 8601） |

> **限制**：单次导出最多 10000 条记录。

**成功响应** `200`:

- `Content-Type: text/csv; charset=utf-8`
- `Content-Disposition: attachment; filename="detection_records_2026-04-08.csv"`

CSV 文件内容（管理员）：

```csv
检测时间,操作者,邮箱,使用模型,模型版本,缺陷区域数,凹坑,边缘裂纹,划痕刮伤,氧化铁皮,缺陷面积占比,推理耗时(ms),原图存储键
2026-04-01T14:30:00Z,张三,zhangsan@example.com,UNet-ResNet34,1.0.0,3,2,0,1,0,1.830000%,52.3,results/record-uuid/original.png
```

CSV 文件内容（质检员）：

```csv
检测时间,操作者,使用模型,模型版本,缺陷区域数,凹坑,边缘裂纹,划痕刮伤,氧化铁皮,缺陷面积占比,推理耗时(ms),原图存储键
2026-04-01T14:30:00Z,张三,UNet-ResNet34,1.0.0,3,2,0,1,0,1.830000%,52.3,results/record-uuid/original.png
```

> **前端须知**：
> - 响应不是 JSON，而是 CSV 文件流，前端应使用 `Blob` + `URL.createObjectURL` 或 `<a download>` 方式触发下载
> - 文件以 UTF-8 BOM 开头，确保 Excel 正确识别中文
> - 导出操作本身会记录一条 `export_records` 日志，details 包含 export_type=detections 和 row_count；文本防公式注入。原图列为稳定逻辑存储键，查询记录详情后获取签名资源，不作为公开下载链接。

**失败响应**:

| 状态码 | 原因 |
|--------|------|
| 401 | 未认证 / Token 无效 |
| 500 | 导出服务异常 |

---

### 2.5 获取单条检测详情

```
GET /api/detect/records/:id
```

**权限**: 已认证

- **管理员 (admin)**：可查看任意用户的检测记录
- **质检员 (inspector)**：仅限查看自己的记录

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| id | string (UUID) | 检测记录 ID |

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "id": "record-uuid",
    "user_id": "auth-user-uuid",
    "model_id": "model-uuid",
    "original_image_url": "/uploads/results/..._original.png",
    "mask_image_url": "/uploads/results/..._mask.png",
    "overlay_image_url": "/uploads/results/..._overlay.png",
    "defect_count": 3,
    "defect_class_counts": { "1": 2, "2": 0, "3": 1, "4": 0 },
    "defect_info": {
      "total_pixels": 65536,
      "defect_pixels": 1200,
      "defect_ratio": 0.0183,
      "class_distribution": { "1": 500, "2": 300, "3": 250, "4": 150 }
    },
    "inference_time_ms": 52.3,
    "created_at": "2026-02-22T14:30:00Z",
    "models": {
      "name": "UNet-ResNet34",
      "version": "1.0.0"
    },
    "profiles": {
      "role": "inspector",
      "display_name": "张三",
      "avatar_url": null
    }
  }
}
```

**失败响应** `400`（无效 ID 格式）:

```json
{
  "success": false,
  "error": "无效的记录 ID 格式"
}
```

**失败响应** `404`:

```json
{
  "success": false,
  "error": "检测记录不存在"
}
```

---

## 三、模型管理接口 (`/api/models`)

> 所有接口需要认证。上传/激活/删除操作仅管理员可用。

### 3.1 获取模型列表

```
GET /api/models
```

**权限**: 已认证（admin / inspector）

**成功响应** `200`:

```json
{
  "success": true,
  "data": [
    {
      "id": "model-uuid",
      "name": "UNet-ResNet34",
      "version": "1.0.0",
      "arch_version": "v2",
      "file_name": "UnetPlusPlus_resnet34_v2_cleaned.onnx",
      "file_path": "models/model-uuid.onnx",
      "num_classes": 4,
      "description": "基于 ResNet34 编码器的 UNet 语义分割模型",
      "status": "active",
      "uploaded_by": "admin-user-uuid",
      "miou": 0.782,
      "dice": 0.801,
      "precision": 0.864,
      "recall": 0.823,
      "fps": 32.5,
      "metric_source": "管理员提供的验证集评估记录",
      "file_size": 89.2,
      "created_at": "2026-02-20T10:00:00Z",
      "updated_at": "2026-02-22T14:00:00Z"
    }
  ]
}
```

> **前端提示**：`status` 为 `"active"` 的模型是当前正在使用的推理模型，同一时刻只有一个 active 模型。
>
> **`arch_version` 字段说明**：后端只支持 `"v2"`，固定 `num_classes=4`，输出 `[B,4,H,W]` logits，无背景通道，使用 sigmoid 多标签后处理。模型元信息声明和实际图结构必须一致。
>
> `file_path` 是逻辑存储键，不能作为公开下载 URL。另返回 `file_sha256`、`file_size_bytes`、`metric_source` 与 `inference_config`。未评估的质量指标为 `null`，文件大小按实际字节数 / 1024² 返回 MiB。

---

### 3.2 上传新模型

```
POST /api/models
```

**权限**: 管理员

**请求体** (`multipart/form-data`):

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| model | File (.onnx) | ✅ | ONNX 模型文件（最大 500 MiB） |
| name | string | ✅ | 模型名称 |
| version | string | ❌ | 版本号（默认 `"1.0.0"`） |
| arch_version | string | ❌ | 仅允许 `"v2"`，省略时仍按 V2 校验，其他值拒绝；`num_classes` 固定为 4 |
| description | string | ❌ | 模型描述 |

**成功响应** `201`:

```json
{
  "success": true,
  "data": {
    "id": "new-model-uuid",
    "name": "UNet-ResNet50",
    "version": "2.0.0",
    "arch_version": "v2",
    "file_name": "unet_resnet50_v2.onnx",
    "num_classes": 4,
    "status": "inactive",
    "description": "升级版模型",
    "uploaded_by": "admin-uuid",
    "miou": null,
    "dice": null,
    "precision": null,
    "recall": null,
    "fps": null,
    "metric_source": null,
    "file_size": 102.5,
    "created_at": "2026-02-23T10:00:00Z",
    "updated_at": "2026-02-23T10:00:00Z"
  },
  "message": "模型上传成功"
}
```

> **注意**：新上传的模型默认为 `inactive` 状态，需单独调用激活接口。
>
> 上传会校验实际 protobuf 图，拒绝外部权重引用、自定义算子域、训练图/本地函数及不兼容的 float32/NCHW 输入输出；在原生 256×1600 尺寸进行 CPU 预热并检查输出有限。最大 500 MiB，权重必须内嵌。已有两个模型按 P0 SHA-256 识别；其他模型需在 ONNX `metadata_props` 中声明 `ssdd.contract=ssdd-v2-2026-11-03`，并符合 RGB [0,255]、内置归一化、四通道 logits 契约。输出端 Sigmoid/Softmax 会被拒绝。声明与结构校验用于工程兼容，模型质量仍需独立评估。
>
> 上传不会自动生成 mIoU、Dice 或 FPS；这些字段初始均为 null。

---

### 3.3 激活模型

```
PUT /api/models/:id/activate
```

**权限**: 管理员

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| id | string (UUID) | 模型 ID |

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "id": "model-uuid",
    "name": "UNet-ResNet34",
    "status": "active",
    ...
  },
  "message": "模型 \"UNet-ResNet34\" 已激活并加载"
}
```

> **行为说明**：
> - 唯一 `model_runtime_state` 指针派生 active/inactive，不使用触发器维护多个状态字段
> - 校验文件哈希、加载及预热候选后，短事务提交活动指针和审计，再发布内存句柄；失败保留原句柄
> - 已开始的请求保留原 Session/元信息快照，使用结束后释放；P4 检测接口将复用该租约机制
> - 激活可能需要加载百兆文件，前端超时为 300 秒；重复管理任务或候选数量达到上限返回 503

**失败响应**:

| 状态码 | 原因 |
|--------|------|
| 404 | 模型不存在 |
| 400 | 模型文件缺失/哈希变化/契约无效 |
| 409 | 活动状态被并发修改，需刷新重试 |
| 503 | 管理资源忙或指定设备初始化/预热失败 |

---

### 3.4 修改模型信息

```
PUT /api/models/:id
```

**权限**: 管理员

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| id | string (UUID) | 模型 ID |

**请求体** (`application/json`):

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | ❌ | 模型名称 |
| version | string | ❌ | 版本号 |
| description | string | ❌ | 模型描述（传 `null` 可清空） |
| miou | number | ❌ | 平均交并比 mIoU（0.0~1.0，传 `null` 清空） |
| dice | number | ❌ | Dice 系数（0.0~1.0，传 `null` 清空） |
| precision | number | ❌ | 精确率 Precision（0.0~1.0，传 `null` 清空） |
| recall | number | ❌ | 召回率 Recall（0.0~1.0，传 `null` 清空） |
| fps | number | ❌ | 推理帧率（帧/秒，传 `null` 清空） |
| metric_source | string \| null | ❌ | 指标来源说明，填写非空质量指标或 FPS 时必须提供 |
| file_size | number \| null | ❌ | 兼容旧客户端接收，但不覆盖服务端测量的真实文件大小；新前端只读 |

> **注意**：至少提供一个字段，未传的字段保持不变。

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "id": "model-uuid",
    "name": "UNet-ResNet34-v2",
    "version": "1.1.0",
    "description": "改进版模型",
    "miou": 0.812,
    "dice": 0.835,
    "precision": 0.882,
    "recall": 0.846,
    "fps": 30.2,
    "file_size": 89.2,
    "status": "active",
    ...
  },
  "message": "模型信息已更新"
}
```

**失败响应**:

| 状态码 | 原因 |
|--------|------|
| 400 | 未提供任何需要更新的字段 |
| 404 | 模型不存在 |

---

### 3.5 删除模型

```
DELETE /api/models/:id
```

**权限**: 管理员

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| id | string (UUID) | 模型 ID |

**成功响应** `200`:

```json
{
  "success": true,
  "message": "模型 \"UNet-ResNet34\" 已删除"
}
```

**失败响应**:

| 状态码 | 原因 |
|--------|------|
| 404 | 模型不存在 |
| 400 | 不能删除当前 active 的模型 |

> 删除采用软删除，查询列表隐藏该记录。历史检测引用及快照保留；有历史引用的权重保留，无历史引用的权重在没有活动/退休 Session 租约后清理。活动模型禁止删除。

### 3.6 模型详情与推理状态

`GET /api/models/:id` 返回同列表结构的模型详情；`GET /api/models/runtime` 返回以下状态，均需要认证：

```json
{
  "success": true,
  "data": {
    "ready": true,
    "active_model_id": "model-uuid",
    "state_version": 1,
    "requested_device": "auto",
    "actual_device": "cpu",
    "fallback_reason": "CUDA_INITIALIZATION_FAILED",
    "runtime_version": "1.31.1",
    "error_code": null,
    "retired_sessions": 0,
    "active_leases": 0
  }
}
```

设备配置由服务器控制，普通请求不指定 GPU 编号。CPU 构建支持 cpu；GPU 构建支持 cpu/cuda/auto。强制 cuda 失败时不回退，auto 仅在创建/预热阶段回退 CPU 并记录原因。推理运行错误不会悄然重跑 CPU。

`status=active` 表示持久活动指针；`ready=true` 还要求本进程恢复了对应版本的 Session。启动恢复失败保留数据库指针，`/actuator/health/inference` 为 503，`/health` 与账号 readiness 仍独立。当前部署按单实例协调租约；发现其他实例改写指针后，旧实例拒绝新推理租约并等待恢复/重新激活。

---

## 四、管理员接口 (`/api/admin`)

> 所有管理员接口需要 `admin` 角色。

### 4.1 获取用户列表

```
GET /api/admin/users
```

**权限**: 管理员

**Query 参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | number | ❌ | 1 | 页码 |
| pageSize | number | ❌ | 10 | 每页条数（1-100） |
| search | string | ❌ | - | 搜索关键字（匹配显示名称或邮箱） |
| role | string | ❌ | - | 按角色筛选：`admin` / `inspector` |

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "profile-uuid",
        "user_id": "auth-user-uuid",
        "role": "inspector",
        "display_name": "张三",
        "avatar_url": null,
        "created_at": "2026-02-20T10:00:00Z",
        "updated_at": "2026-02-20T10:00:00Z",
        "email": "zhangsan@example.com"
      }
    ],
    "total": 15,
    "page": 1,
    "pageSize": 10,
    "totalPages": 1
  }
}
```

> **注意**：`email` 来自本地 `users`；用户管理路径参数 `id` 是 `user_profiles.id`，与 `user_id` 不同。

---

### 4.2 获取用户详情

```
GET /api/admin/users/:id
```

**权限**: 管理员

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| id | string (UUID) | profile 表的 `id`（非 auth user_id） |

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "id": "profile-uuid",
    "user_id": "auth-user-uuid",
    "role": "inspector",
    "display_name": "张三",
    "avatar_url": null,
    "created_at": "2026-02-20T10:00:00Z",
    "updated_at": "2026-02-20T10:00:00Z",
    "email": "zhangsan@example.com",
    "detection_count": 42
  }
}
```

| 额外字段 | 说明 |
|----------|------|
| `email` | 用户邮箱（来自 Auth 系统） |
| `detection_count` | 该用户的历史检测总数 |

---

### 4.3 修改用户角色

```
PUT /api/admin/users/:id/role
```

**权限**: 管理员

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| id | string (UUID) | profile 表的 `id` |

**请求体** (`application/json`):

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| role | string | ✅ | 目标角色：`"admin"` 或 `"inspector"` |

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "id": "profile-uuid",
    "user_id": "auth-user-uuid",
    "role": "admin",
    "display_name": "张三",
    ...
  },
  "message": "角色修改成功"
}
```

**失败响应**:

| 状态码 | 原因 |
|--------|------|
| 400 | 无效的角色值 / 不能修改自己的角色 |
| 404 | 用户不存在 |

> **业务规则**：管理员不能修改自己的角色，不能移除最后一个活动管理员。变更使用数据库锁串行化，并在锁内重新检查操作者权限；并发互相降级时，后获得锁且已被降级的一方返回 403。

---

### 4.4 获取系统统计概览

```
GET /api/admin/stats
```

**权限**: 管理员

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "total_users": 15,
    "total_detections": 1234,
    "total_models": 3,
    "active_models": 1,
    "recent_detection_count": 100,
    "avg_inference_time_ms": 52.35,
    "avg_defect_count": 2.8
  }
}
```

**字段说明**:

| 字段 | 说明 |
|------|------|
| `total_users` | 系统总用户数 |
| `total_detections` | 已成功提交的图片记录数，批量按每张计数 |
| `total_models` | 未软删除的模型总数 |
| `active_models` | 当前激活的模型数量（应为 0 或 1） |
| `recent_detection_count` | 最近统计的检测样本数（最多 100） |
| `avg_inference_time_ms` | 最近检测的平均推理耗时（ms） |
| `avg_defect_count` | 最近最多 100 条成功记录的平均区域数；空集为 0 |

统计聚合使用同一数据库快照。Redis 仅缓存管理员全局聚合 DTO，默认 30 秒；成功数据库变更提交后失效，回滚不失效。回填通过 generation 标记比较，防止旧查询覆盖提交后的失效结果。Redis 异常回源 MySQL，不影响已提交的检测；失效故障时允许 TTL 内陈旧。首页本地缓存按账号/角色隔离，30 秒 TTL，检测/模型操作后失效。

---

## 五、操作日志接口 (`/api/logs`)

> 所有用户均可访问。管理员查看全部日志，质检员仅查看自己的日志。

### 5.1 查询操作日志

```
GET /api/logs
```

**权限**: 已认证（admin / inspector）

- **管理员 (admin)**：默认返回所有用户的操作日志，支持 `userId` 精确筛选、`search` 按显示名称或邮箱模糊搜索、`role` 按用户角色筛选
- **质检员 (inspector)**：仅返回当前用户自己的操作日志（`userId` / `search` / `role` 参数被忽略）

**Query 参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | number | ❌ | 1 | 页码 |
| pageSize | number | ❌ | 10 | 每页条数（1-100） |
| action | string | ❌ | - | 按操作类型筛选（见附录B） |
| userId | string | ❌ | - | 按用户 ID 精确筛选（auth user_id；仅管理员可用） |
| search | string | ❌ | - | 搜索关键字，匹配显示名称或邮箱（仅管理员可用，与 `userId` 互斥，`userId` 优先） |
| role | string | ❌ | - | 按用户角色筛选：`admin` / `inspector`（仅管理员可用，可与 `search` 组合） |
| startDate | string | ❌ | - | 起始日期（ISO 8601，如 `2026-02-20T00:00:00Z`） |
| endDate | string | ❌ | - | 结束日期（ISO 8601） |

**成功响应** `200`:

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "log-uuid",
        "user_id": "auth-user-uuid",
        "action": "detect",
        "details": {
          "model_id": "model-uuid",
          "model_name": "UNet-ResNet34",
          "defect_count": 3,
          "inference_time_ms": 52.3
        },
        "ip_address": "127.0.0.1",
        "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...",
        "status": "success",
        "created_at": "2026-02-22T14:30:00Z",
        "user": {
          "display_name": "张三",
          "email": "zhangsan@example.com"
        }
      }
    ],
    "total": 150,
    "page": 1,
    "pageSize": 10,
    "totalPages": 8
  }
}
```

**日志条目字段说明**:

| 字段 | 说明 |
|------|------|
| `user_agent` | 客户端浏览器 User-Agent 字符串，可用于识别设备和浏览器 |
| `status` | 操作结果：`success`（成功）/ `failure`（失败） |

> **说明**：
> - 每条日志附带 `user` 子对象，包含操作者的显示名称和邮箱，方便前端直接展示。
> - 管理员查询时 `user.email` 字段有值；质检员查询时 `user.email` 为空字符串。
> - 系统/历史日志的 user_id、ip_address、user_agent 可为空。新增 request_id 和 target_id；新 HTTP 审计使用直接连接 IP、有界 User-Agent，既有空字段不回填。日期均为带时区 ISO 8601，起止两端包含；列表和导出复用同一权限/筛选条件，前端日期选择的结束日期展开至当日末尾。

---

### 5.2 导出操作日志

```
GET /api/logs/export
```

**权限**: 已认证（admin / inspector）

- **管理员 (admin)**：导出全部日志（可筛选），CSV 中包含邮箱列
- **质检员 (inspector)**：仅导出自己的操作日志，CSV 中不含邮箱列

**Query 参数**:

与 [5.1 查询操作日志](#51-查询操作日志) 相同的筛选参数（`action` / `userId` / `search` / `role` / `startDate` / `endDate`），不需要分页参数。

> **限制**：单次导出最多 10000 条记录。

**成功响应** `200`:

- `Content-Type: text/csv; charset=utf-8`
- `Content-Disposition: attachment; filename="operation_logs_2026-04-01.csv"`

CSV 文件内容（管理员）：

```csv
操作时间,操作者,邮箱,操作类型,操作状态,IP 地址,客户端,操作详情,请求 ID
2026-04-01T14:30:00Z,张三,zhangsan@example.com,单张检测,成功,127.0.0.1,Mozilla/5.0 ...,"{""model_id"":""uuid"",""defect_count"":3}",request-uuid
```

CSV 文件内容（质检员）：

```csv
操作时间,操作者,操作类型,操作状态,IP 地址,客户端,操作详情,请求 ID
2026-04-01T14:30:00Z,张三,单张检测,成功,127.0.0.1,Mozilla/5.0 ...,"{""model_id"":""uuid"",""defect_count"":3}",request-uuid
```

> **前端须知**：
> - 响应不是 JSON，而是 CSV 文件流，前端应使用 `Blob` + `URL.createObjectURL` 或 `<a download>` 方式触发下载
> - 文件以 UTF-8 BOM 开头，确保 Excel 正确识别中文
> - 操作类型已翻译为中文（如 `detect` → `单张检测`）
> - 导出操作本身会记录一条 `export_records` 类型的操作日志

**失败响应**:

| 状态码 | 原因 |
|--------|------|
| 401 | 未认证 / Token 无效 |
| 500 | 导出服务异常 |

---

## 六、其他接口

### 6.1 健康检查

```
GET /health
```

**权限**: 公开

**成功响应** `200`:

```json
{
  "status": "ok",
  "timestamp": "2026-02-23T10:00:00.000Z",
  "version": "1.0.0"
}
```

### 6.2 静态资源

| 路径 | 说明 |
|------|------|
| `/static/models/` | 模型文件目录（不建议前端直接访问） |
| `/static/test-images/` | 测试图片目录 |
| `/uploads/results/` | 检测结果图片目录（原图/掩码/叠加图） |

---

## 附录A：缺陷类别定义

| 索引 | 英文标识 | 中文名称 | 可视化颜色 | 说明 |
|------|----------|----------|------------|------|
| 0 | Background | 背景 | 黑色 `#000000` | 无缺陷区域 |
| 1 | Pit | 凹坑 | 红色 `#FF0000` | 钢铁表面凹坑 |
| 2 | EdgeCrack | 边缘裂纹 | 绿色 `#00FF00` | 边缘位置裂纹 |
| 3 | Scratch | 划痕刮伤 | 蓝色 `#0000FF` | 表面划痕或刮伤 |
| 4 | IronOxideScale | 氧化铁皮 | 橙色 `#FFA500` | 氧化铁皮区域 |

> `defect_class_counts` 和 `class_distribution` 中的 key 均为上表索引值（1-4，不含背景）。

---

## 附录B：操作日志类型 (`LogAction`)

| 值 | 说明 | details 字段示例 |
|----|------|------------------|
| `login` | 用户登录 | `{}` |
| `logout` | 用户登出 | `{}` |
| `register` | 用户注册 | `{ "role": "inspector" }` |
| `detect` | 单张图片检测 | `{ "record_id": "...", "model_id": "...", "defect_count": 3, "actual_device": "cpu" }` |
| `batch_detect` | 批量图片检测 | `{ "batch_id": "...", "model_id": "...", "image_count": 5, "actual_device": "cuda" }` |
| `model_switch` | 兼容历史筛选值；当前改用 model_activate | `{ "model_id": "..." }` |
| `model_activate` | 激活模型 | `{ "model_id": "...", "actual_device": "cpu" }` |
| `model_update` | 修改模型 | `{ "model_id": "..." }` |
| `model_upload` | 上传新模型 | `{ "model_id": "...", "sha256": "...", "bytes": 123 }` |
| `model_delete` | 删除模型 | `{ "model_id": "...", "historical_weights_retained": true }` |
| `export_records` | 导出检测或日志 | `{ "export_type": "logs", "row_count": 100 }` |
| `refresh_reuse` | 刷新令牌重放 | `{ "session_id": "..." }` |
| `password_change` | 修改密码 | `{}` |
| `profile_update` | 修改个人信息 | `{}` 或 `{ "avatar_updated": true }` |
| `user_role_change` | 修改用户角色 | `{ "target_user_id": "...", "old_role": "inspector", "new_role": "admin" }` |

失败日志 details 仅保存安全错误状态；未认证失败的 user_id 可为空。邮箱按权限关联查询，不在普通操作 details 中重复保存。

---

## 附录C：前端开发快速参考

### 请求工具函数建议

```typescript
// api.ts — 基础请求封装示例

const BASE_URL = 'http://localhost:3000';

async function request<T>(url: string, options?: RequestInit): Promise<ApiResponse<T>> {
  const token = localStorage.getItem('access_token');
  const res = await fetch(`${BASE_URL}${url}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  });
  return res.json();
}

// 登录
const loginRes = await request('/api/auth/login', {
  method: 'POST',
  body: JSON.stringify({ email: 'user@example.com', password: 'Example-password-2026!' }),
});
if (loginRes.success) {
  localStorage.setItem('access_token', loginRes.data.session.access_token);
}

// 检测（需要 FormData）
async function detectImage(file: File) {
  const token = localStorage.getItem('access_token');
  const formData = new FormData();
  formData.append('image', file);
  const res = await fetch(`${BASE_URL}/api/detect`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: formData,  // 不要手动设置 Content-Type，浏览器会自动添加 boundary
  });
  return res.json();
}

// 获取图片完整 URL
function getImageUrl(relativePath: string): string {
  return `${BASE_URL}${relativePath}`;
}
```

### 路由守卫建议

```
公开页面：  /login, /register
质检员页面：/detect, /records, /records/:id
管理员页面：/admin/users, /admin/stats, /admin/models
通用页面：  /logs（管理员看全部，质检员看自己的）
```

根据 `profile.role` 字段控制菜单显示和路由访问权限。
