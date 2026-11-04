import api from './index'
import axios from 'axios'
import type { AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import type {
  LoginPayload,
  LoginData,
  RegisterPayload,
  RegisterData,
  ChangePasswordPayload,
  UpdateProfilePayload,
  UserProfile,
  User,
} from '@/types/auth'

/** 独立 axios 实例，用于不需要 Authorization 头的公开接口（如刷新 Token），避免拦截器死循环 */
const publicApi = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:3000',
  timeout: 30000,
})

// 登录、注册的 401 是凭据错误，不应触发当前会话的自动续期。
async function publicPost<T>(path: string, data: unknown) {
  try {
    return await publicApi.post<ApiResponse<T>>(path, data)
  } catch (error) {
    ElMessage.error(
      axios.isAxiosError(error)
        ? error.response?.data?.error || '请求失败，请稍后重试'
        : '网络异常，请检查网络连接',
    )
    throw error
  }
}

const refreshing = new Map<string, Promise<AxiosResponse<ApiResponse<LoginData>>>>()

/** 用户登录 */
export function login(data: LoginPayload) {
  return publicPost<LoginData>('/api/auth/login', data)
}

/** 刷新 Token */
export function refreshToken(refresh_token: string) {
  // 路由守卫与响应拦截器可能同时续期；同一令牌只能消费一次。
  const pending = refreshing.get(refresh_token)
  if (pending) return pending
  const request = publicApi
    .post<ApiResponse<LoginData>>('/api/auth/refresh', { refresh_token })
    .finally(() => refreshing.delete(refresh_token))
  refreshing.set(refresh_token, request)
  return request
}

/** 用户注册 */
export function register(data: RegisterPayload) {
  return publicPost<RegisterData>('/api/auth/register', data)
}

/** 用户登出 */
export function logout() {
  return api.post<ApiResponse<null>>('/api/auth/logout')
}

/** 获取当前用户信息 */
export function getMe() {
  return api.get<ApiResponse<User>>('/api/auth/me')
}

/** 修改个人信息 */
export function updateProfile(data: UpdateProfilePayload) {
  return api.put<ApiResponse<UserProfile>>('/api/auth/profile', data)
}

/** 修改密码 */
export function changePassword(data: ChangePasswordPayload) {
  return api.put<ApiResponse<null>>('/api/auth/password', data)
}

/** 上传头像 */
export function uploadAvatar(file: File) {
  const formData = new FormData()
  formData.append('avatar', file)
  return api.post<ApiResponse<{ avatar_url: string; profile: UserProfile }>>(
    '/api/auth/avatar',
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
    },
  )
}
