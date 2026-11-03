/** 用户资料 */
export interface UserProfile {
  id: string
  user_id: string
  role: 'admin' | 'inspector'
  display_name: string
  avatar_url: string | null
  created_at: string
  updated_at: string
}

/** 用户信息（含 profile） */
export interface User {
  id: string
  email: string
  profile: UserProfile
}

/** 登录会话信息 */
export interface Session {
  access_token: string
  refresh_token: string
  expires_at: number
}

/** 登录响应 data */
export interface LoginData {
  user: User
  session: Session
}

/** 注册响应 data */
export interface RegisterData {
  userId: string
}

/** 登录请求体 */
export interface LoginPayload {
  email: string
  password: string
}

/** 注册请求体 */
export interface RegisterPayload {
  email: string
  password: string
  displayName?: string
}

/** 修改密码请求体 */
export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

/** 修改个人信息请求体 */
export interface UpdateProfilePayload {
  displayName?: string
  avatarUrl?: string | null
}
