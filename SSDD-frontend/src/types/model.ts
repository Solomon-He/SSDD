/** 模型状态 */
export type ModelStatus = 'active' | 'inactive'

/** 模型信息 */
export interface Model {
  id: string
  name: string
  version: string
  arch_version: 'v2'
  file_name: string
  file_path: string
  file_sha256: string
  file_size_bytes: number
  metric_source: string | null
  num_classes: number
  description: string
  status: ModelStatus
  uploaded_by: string
  /** mIoU 指标 */
  miou: number | null
  /** 精确率 Precision */
  precision: number | null
  /** 召回率 Recall */
  recall: number | null
  /** Dice 指标 */
  dice: number | null
  /** 推理帧率 (FPS) */
  fps: number | null
  /** 模型文件大小 (MB) */
  file_size: number | null
  created_at: string
  updated_at: string
}

/** 上传模型表单参数 */
export interface UploadModelParams {
  model: File
  name: string
  version?: string
  description?: string
}

/** 修改模型信息参数 */
export interface UpdateModelParams {
  name?: string
  version?: string
  description?: string | null
  miou?: number | null
  precision?: number | null
  recall?: number | null
  dice?: number | null
  fps?: number | null
  metric_source?: string | null
}

export interface ModelRuntime {
  ready: boolean
  active_model_id: string | null
  state_version: number
  requested_device: 'cpu' | 'cuda' | 'auto'
  actual_device: 'cpu' | 'cuda' | null
  fallback_reason: string | null
  error_code: string | null
}
