/** 用户列表项（管理员视图） */
export interface AdminUser {
  id: string
  user_id: string
  role: 'admin' | 'inspector'
  display_name: string
  avatar_url: string | null
  created_at: string
  updated_at: string
  email: string
}

/** 用户详情（管理员视图） */
export interface AdminUserDetail extends AdminUser {
  detection_count: number
}

/** 系统统计概览 */
export interface SystemStats {
  total_users: number
  total_detections: number
  total_models: number
  active_models: number
  recent_detection_count: number
  avg_inference_time_ms: number
  avg_defect_count: number
}

/** 查询用户列表参数 */
export interface UserQueryParams {
  page?: number
  pageSize?: number
  search?: string
  role?: 'admin' | 'inspector'
}

/** 修改用户角色请求体 */
export interface UpdateRolePayload {
  role: 'admin' | 'inspector'
}
