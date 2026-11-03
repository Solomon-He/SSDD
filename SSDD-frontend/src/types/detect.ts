/** 缺陷类别计数 (key 为类别索引 1-4) */
export interface DefectClassCounts {
  [key: string]: number
}

/** 缺陷详细信息 */
export interface DefectInfo {
  total_pixels: number
  defect_pixels: number
  defect_ratio: number
  class_distribution: DefectClassCounts
}

/** 模型简要信息（检测结果中返回） */
export interface DetectModelInfo {
  id: string
  name: string
  version: string
}

/**
 * 检测结果的公共基础字段。
 * DetectResult / BatchDetectItem / DetectRecord 共享这些字段，
 * 提取后避免重复定义，便于统一维护。
 */
export interface DetectResultBase {
  record_id: string
  original_image_url: string
  mask_image_url: string
  overlay_image_url: string
  defect_count: number
  defect_class_counts: DefectClassCounts
  defect_info: DefectInfo
  inference_time_ms: number
}

/** 单张检测结果 */
export interface DetectResult extends DetectResultBase {
  model: DetectModelInfo
}

/** 批量检测中的单个结果 */
export interface BatchDetectItem extends DetectResultBase {
  file_name: string
}

/** 批量检测响应 data */
export interface BatchDetectData {
  model: DetectModelInfo
  total: number
  results: BatchDetectItem[]
}

/** 检测记录（历史列表中的一条） */
export interface DetectRecord extends DetectResultBase {
  id: string
  user_id: string
  model_id: string
  created_at: string
  models: {
    name: string
    version: string
  }
  profiles: {
    role: string
    display_name: string
    avatar_url: string | null
  }
}
