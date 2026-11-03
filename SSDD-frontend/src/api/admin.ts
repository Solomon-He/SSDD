import api from './index'
import type { ApiResponse, PaginatedData } from '@/types/api'
import type {
  AdminUser,
  AdminUserDetail,
  UserQueryParams,
  UpdateRolePayload,
  SystemStats,
} from '@/types/admin'

/** 获取用户列表（分页） */
export function getUsers(params: UserQueryParams) {
  return api.get<ApiResponse<PaginatedData<AdminUser>>>('/api/admin/users', { params })
}

/** 获取系统统计概览 */
export function getAdminStats() {
  return api.get<ApiResponse<SystemStats>>('/api/admin/stats')
}

/** 获取用户详情 */
export function getUserDetail(id: string) {
  return api.get<ApiResponse<AdminUserDetail>>(`/api/admin/users/${encodeURIComponent(id)}`)
}

/** 修改用户角色 */
export function updateUserRole(id: string, payload: UpdateRolePayload) {
  return api.put<ApiResponse<AdminUser>>(`/api/admin/users/${encodeURIComponent(id)}/role`, payload)
}
