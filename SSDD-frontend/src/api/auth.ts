import api from './index'
import axios from 'axios'
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

/** 用户登录 */
export function login(data: LoginPayload) {
  return api.post<ApiResponse<LoginData>>('/api/auth/login', data)
}

/** 刷新 Token */
export function refreshToken(refresh_token: string) {
  return publicApi.post<ApiResponse<LoginData>>('/api/auth/refresh', { refresh_token })
}

/** 用户注册 */
export function register(data: RegisterPayload) {
  return api.post<ApiResponse<RegisterData>>('/api/auth/register', data)
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
