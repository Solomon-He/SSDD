import axios from 'axios'
import type { InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { getAccessToken, getRefreshToken, saveAuth, clearAuth } from '@/utils/storage'
import { ElMessage } from 'element-plus'
import router from '@/router'

/** 创建 Axios 实例 */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:3000',
  timeout: 30000,
})

/** Token 刷新状态管理 */
let isRefreshing = false
let pendingRequests: Array<{
  resolve: (token: string) => void
  reject: (error: unknown) => void
}> = []

function onRefreshSuccess(newToken: string) {
  pendingRequests.forEach(({ resolve }) => resolve(newToken))
  pendingRequests = []
}

function onRefreshFailure(error: unknown) {
  pendingRequests.forEach(({ reject }) => reject(error))
  pendingRequests = []
}

/** 请求拦截器：注入 Authorization 头 */
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getAccessToken()
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

/** 响应拦截器：统一错误处理 + Token 静默续期 */
api.interceptors.response.use(
  (response: AxiosResponse) => {
    return response
  },
  async (error) => {
    const originalRequest = error.config

    // 401 且未重试过 → 尝试刷新 Token
    if (error.response?.status === 401 && !originalRequest._retried) {
      originalRequest._retried = true

      const refresh = getRefreshToken()
      if (!refresh) {
        // 没有 refresh_token，直接跳登录
        clearAuth()
        ElMessage.error('登录已过期，请重新登录')
        router.push('/login')
        return Promise.reject(error)
      }

      // 如果已经在刷新中，排队等待
      if (isRefreshing) {
        return new Promise<string>((resolve, reject) => {
          pendingRequests.push({ resolve, reject })
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`
          return api(originalRequest)
        })
      }

      isRefreshing = true
      try {
        // 动态导入避免循环依赖
        const { refreshToken } = await import('@/api/auth')
        const res = await refreshToken(refresh)
        const { data } = res

        if (data.success && data.data) {
          const { session, user } = data.data
          // 更新本地存储
          saveAuth(session.access_token, session.refresh_token, session.expires_at)

          // 更新 Pinia store（延迟导入避免循环依赖）
          const { useAuthStore } = await import('@/stores/auth')
          const authStore = useAuthStore()
          authStore.setAuth(user, session.access_token, session.refresh_token, session.expires_at)

          // 通知排队的请求
          onRefreshSuccess(session.access_token)

          // 用新 Token 重试原请求
          originalRequest.headers.Authorization = `Bearer ${session.access_token}`
          return api(originalRequest)
        }

        // 刷新接口返回 success: false
        throw new Error('refresh failed')
      } catch (refreshError) {
        onRefreshFailure(refreshError)
        clearAuth()
        ElMessage.error('登录已过期，请重新登录')
        router.push('/login')
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    // 非 401 错误或已重试过的 401
    if (error.response) {
      const { status, data } = error.response
      const message = data?.error || data?.message || '请求失败'

      switch (status) {
        case 401:
          // 刷新也失败的 401，已在上面处理
          clearAuth()
          ElMessage.error(message || '登录已过期，请重新登录')
          router.push('/login')
          break
        case 403:
          ElMessage.error('权限不足')
          break
        case 404:
          ElMessage.error('资源不存在')
          break
        case 503:
          ElMessage.error('服务不可用，请稍后重试')
          break
        default:
          ElMessage.error(message)
      }
    } else {
      ElMessage.error('网络异常，请检查网络连接')
    }
    return Promise.reject(error)
  },
)

export default api
