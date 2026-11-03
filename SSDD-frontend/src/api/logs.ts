import api from './index'
import type { ApiResponse, PaginatedData } from '@/types/api'
import type { LogEntry, LogQueryParams, LogExportParams } from '@/types/logs'

/** 查询操作日志（分页） */
export function getLogs(params?: LogQueryParams) {
  return api.get<ApiResponse<PaginatedData<LogEntry>>>('/api/logs', { params })
}

/** 导出操作日志（CSV 文件流） */
export function exportLogs(params?: LogExportParams) {
  return api.get('/api/logs/export', {
    params,
    responseType: 'blob',
  })
}
