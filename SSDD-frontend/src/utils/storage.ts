const TOKEN_KEY = 'access_token'
const REFRESH_TOKEN_KEY = 'refresh_token'
const EXPIRES_AT_KEY = 'expires_at'

/** 保存认证信息到 localStorage */
export function saveAuth(accessToken: string, refreshToken: string, expiresAt: number): void {
  localStorage.setItem(TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  localStorage.setItem(EXPIRES_AT_KEY, String(expiresAt))
}

/** 获取 access_token */
export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/** 获取 refresh_token */
export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

/** 获取 Token 过期时间戳（秒） */
export function getExpiresAt(): number | null {
  const val = localStorage.getItem(EXPIRES_AT_KEY)
  return val ? Number(val) : null
}

/** 清除所有认证信息 */
export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(EXPIRES_AT_KEY)
}

/** 判断 Token 是否已过期 */
export function isTokenExpired(): boolean {
  const expiresAt = getExpiresAt()
  if (!expiresAt) return true
  return Date.now() / 1000 > expiresAt
}
