/** Tag 颜色类型 */
export type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

/** 操作日志类型定义 */
export interface LogActionDef {
  readonly value: string
  readonly label: string
  readonly tagType: TagType
}

/** 操作日志类型列表（合并了标签颜色映射） */
export const LOG_ACTIONS: LogActionDef[] = [
  { value: 'login', label: '用户登录', tagType: 'success' },
  { value: 'logout', label: '用户登出', tagType: 'info' },
  { value: 'register', label: '用户注册', tagType: 'success' },
  { value: 'detect', label: '单张图片检测', tagType: 'primary' },
  { value: 'batch_detect', label: '批量图片检测', tagType: 'primary' },
  { value: 'task_submit', label: '提交异步任务', tagType: 'primary' },
  { value: 'task_retry', label: '任务等待重试', tagType: 'warning' },
  { value: 'task_failed', label: '异步任务失败', tagType: 'danger' },
  { value: 'model_switch', label: '切换激活模型', tagType: 'warning' },
  { value: 'model_activate', label: '激活模型', tagType: 'warning' },
  { value: 'model_update', label: '修改模型', tagType: 'warning' },
  { value: 'refresh_reuse', label: '刷新令牌重放', tagType: 'danger' },
  { value: 'model_upload', label: '上传新模型', tagType: 'warning' },
  { value: 'model_delete', label: '删除模型', tagType: 'danger' },
  { value: 'export_records', label: '导出记录', tagType: 'info' },
  { value: 'password_change', label: '修改密码', tagType: 'warning' },
  { value: 'profile_update', label: '修改个人信息', tagType: 'info' },
  { value: 'user_role_change', label: '修改用户角色', tagType: 'danger' },
]

/** 预构建 Map，O(1) 查找 */
const ACTION_MAP = new Map(LOG_ACTIONS.map((a) => [a.value, a]))

/** 根据 action 值获取中文标签 */
export function getLogActionLabel(action: string): string {
  return ACTION_MAP.get(action)?.label ?? action
}

/** 根据 action 值获取 Tag 颜色类型 */
export function getLogActionTagType(action: string): TagType {
  return ACTION_MAP.get(action)?.tagType ?? 'info'
}
