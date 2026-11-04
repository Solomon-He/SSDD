/** 日志中的用户信息 */
export interface LogUser {
  display_name: string
  email: string
}

/** 操作日志项 */
export interface LogEntry {
  id: string
  user_id: string | null
  action: string
  details: Record<string, unknown>
  ip_address: string | null
  user_agent: string | null
  request_id: string
  target_id: string | null
  status: 'success' | 'failure'
  created_at: string
  user: LogUser
}

/** 查询操作日志参数 */
export interface LogQueryParams {
  page?: number
  pageSize?: number
  action?: string
  userId?: string
  search?: string
  role?: 'admin' | 'inspector'
  startDate?: string
  endDate?: string
}

/** 筛选参数（不含分页） */
export type LogFilterParams = Omit<LogQueryParams, 'page' | 'pageSize'>

/** 导出操作日志参数 */
export type LogExportParams = LogFilterParams
