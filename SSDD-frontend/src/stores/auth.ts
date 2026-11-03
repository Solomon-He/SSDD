import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types/auth'
import {
  getAccessToken,
  getRefreshToken,
  getExpiresAt,
  saveAuth,
  clearAuth,
  isTokenExpired,
} from '@/utils/storage'
import * as authApi from '@/api/auth'
import { ElMessage } from 'element-plus'

export const useAuthStore = defineStore('auth', () => {
  // ---- State ----
  const user = ref<User | null>(null)
  const accessToken = ref<string | null>(getAccessToken())
  const refreshToken = ref<string | null>(getRefreshToken())
  const expiresAt = ref<number | null>(getExpiresAt())
  const loading = ref(false)

  // ---- Getters ----
  const isLoggedIn = computed(() => !!accessToken.value)
  const isAdmin = computed(() => user.value?.profile?.role === 'admin')
  const userRole = computed(() => user.value?.profile?.role ?? '')
  const displayName = computed(() => user.value?.profile?.display_name ?? '')
  const avatarUrl = computed(() => user.value?.profile?.avatar_url ?? '')

  // ---- Actions ----

  /** 设置认证信息（登录成功后调用） */
  function setAuth(userData: User, token: string, refresh: string, expires: number) {
    user.value = userData
    accessToken.value = token
    refreshToken.value = refresh
    expiresAt.value = expires
    saveAuth(token, refresh, expires)
  }

  /** 设置用户信息（刷新用户数据时调用） */
  function setUser(userData: User) {
    user.value = userData
  }

  /** 清除认证状态（退出登录时调用） */
  function clearAuthState() {
    user.value = null
    accessToken.value = null
    refreshToken.value = null
    expiresAt.value = null
    clearAuth()
  }

  /** 登录 */
  async function login(email: string, password: string) {
    loading.value = true
    try {
      const res = await authApi.login({ email, password })
      const { data } = res
      if (data.success && data.data) {
        const { user: userData, session } = data.data
        setAuth(userData, session.access_token, session.refresh_token, session.expires_at)
        return true
      }
      ElMessage.error(data.error || '登录失败')
      return false
    } catch {
      // 错误已在 Axios 拦截器中处理
      return false
    } finally {
      loading.value = false
    }
  }

  /** 注册 */
  async function register(email: string, password: string, displayName?: string) {
    loading.value = true
    try {
      const res = await authApi.register({ email, password, displayName })
      const { data } = res
      if (data.success) {
        ElMessage.success(data.message || '注册成功')
        return true
      }
      ElMessage.error(data.error || '注册失败')
      return false
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  /** 登出 */
  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // 即使登出 API 失败也清除本地状态
    } finally {
      clearAuthState()
    }
  }

  /** 刷新 Token（主动调用，如路由守卫中检测到 Token 即将过期） */
  async function refreshAuth() {
    const refresh = refreshToken.value
    if (!refresh) return false
    try {
      const res = await authApi.refreshToken(refresh)
      const { data } = res
      if (data.success && data.data) {
        const { user: userData, session } = data.data
        setAuth(userData, session.access_token, session.refresh_token, session.expires_at)
        return true
      }
      return false
    } catch {
      clearAuthState()
      return false
    }
  }

  /** 获取当前用户信息（用于页面刷新后恢复用户数据） */
  async function fetchCurrentUser() {
    if (!accessToken.value) return false

    // 如果 Token 已过期，先尝试刷新
    if (isTokenExpired()) {
      const refreshed = await refreshAuth()
      if (!refreshed) return false
    }

    try {
      const res = await authApi.getMe()
      const { data } = res
      if (data.success && data.data) {
        setUser(data.data)
        return true
      }
      return false
    } catch {
      // Token 无效，清除状态（拦截器已处理 401）
      clearAuthState()
      return false
    }
  }

  /** 修改个人信息（显示名称） */
  async function updateProfile(displayNameValue: string) {
    loading.value = true
    try {
      const res = await authApi.updateProfile({ displayName: displayNameValue })
      const { data } = res
      if (data.success && data.data && user.value) {
        user.value = { ...user.value, profile: data.data }
        ElMessage.success('个人信息更新成功')
        return true
      }
      ElMessage.error(data.error || '更新失败')
      return false
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  /** 修改密码 */
  async function changePassword(oldPassword: string, newPassword: string) {
    loading.value = true
    try {
      const res = await authApi.changePassword({ oldPassword, newPassword })
      const { data } = res
      if (data.success) {
        ElMessage.success('密码修改成功')
        return true
      }
      ElMessage.error(data.error || '密码修改失败')
      return false
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  /** 上传头像 */
  async function uploadAvatar(file: File) {
    loading.value = true
    try {
      const res = await authApi.uploadAvatar(file)
      const { data } = res
      if (data.success && data.data && user.value) {
        user.value = { ...user.value, profile: data.data.profile }
        ElMessage.success('头像上传成功')
        return true
      }
      ElMessage.error(data.error || '头像上传失败')
      return false
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  return {
    // state
    user,
    accessToken,
    refreshToken,
    expiresAt,
    loading,
    // getters
    isLoggedIn,
    isAdmin,
    userRole,
    displayName,
    avatarUrl,
    // actions
    setAuth,
    setUser,
    clearAuthState,
    login,
    register,
    logout,
    refreshAuth,
    fetchCurrentUser,
    updateProfile,
    changePassword,
    uploadAvatar,
  }
})
