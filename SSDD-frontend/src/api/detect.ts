import api from './index'
import type { ApiResponse, PaginatedData } from '@/types/api'
import type { DetectResult, BatchDetectData, DetectRecord } from '@/types/detect'

/** 单张图片检测 */
export function detectImage(file: File, signal?: AbortSignal) {
  const formData = new FormData()
  formData.append('image', file)
  return api.post<ApiResponse<DetectResult>>('/api/detect', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    signal,
  })
}

/**
 * 批量图片检测。
 * timeout 设为 120 s（覆盖全局 30 s），适配多张大图上传场景。
 * 支持通过 onProgress 回调获取上传进度百分比（0-100）。
 */
export function detectBatch(
  files: File[],
  options?: { signal?: AbortSignal; onProgress?: (percent: number) => void },
) {
  const formData = new FormData()
  files.forEach((file) => formData.append('images', file))
  return api.post<ApiResponse<BatchDetectData>>('/api/detect/batch', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120_000,
    signal: options?.signal,
    onUploadProgress: (e) => {
      if (e.total && options?.onProgress) {
        options.onProgress(Math.round((e.loaded / e.total) * 100))
      }
    },
  })
}

/** 获取检测历史记录（分页） */
export function getRecords(params?: {
  page?: number
  pageSize?: number
  search?: string
  userId?: string
  all?: boolean
}) {
  return api.get<ApiResponse<PaginatedData<DetectRecord>>>('/api/detect/records', { params })
}

/** 导出检测记录（CSV） */
export function exportRecords(params?: {
  userId?: string
  search?: string
  startDate?: string
  endDate?: string
}) {
  return api.get('/api/detect/records/export', {
    params,
    responseType: 'blob',
  })
}

/** 获取单条检测详情 */
export function getRecordDetail(id: string) {
  return api.get<ApiResponse<DetectRecord>>(`/api/detect/records/${encodeURIComponent(id)}`)
}
