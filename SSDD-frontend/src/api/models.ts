import api from './index'
import type { ApiResponse } from '@/types/api'
import type { Model, UploadModelParams, UpdateModelParams } from '@/types/model'

/** 获取模型列表 */
export function getModels() {
  return api.get<ApiResponse<Model[]>>('/api/models')
}

/** 上传新模型 */
export function uploadModel(params: UploadModelParams) {
  const formData = new FormData()
  formData.append('model', params.model)
  formData.append('name', params.name)
  if (params.version) formData.append('version', params.version)
  if (params.description) formData.append('description', params.description)
  return api.post<ApiResponse<Model>>('/api/models', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300_000, // 模型文件可能较大，超时设为 5 分钟
  })
}

/** 激活模型 */
export function activateModel(id: string) {
  return api.put<ApiResponse<Model>>(`/api/models/${encodeURIComponent(id)}/activate`)
}

/** 修改模型信息 */
export function updateModel(id: string, params: UpdateModelParams) {
  return api.put<ApiResponse<Model>>(`/api/models/${encodeURIComponent(id)}`, params)
}

/** 删除模型 */
export function deleteModel(id: string) {
  return api.delete<ApiResponse<void>>(`/api/models/${encodeURIComponent(id)}`)
}
