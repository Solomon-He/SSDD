import type { BatchDetectData, DetectModelInfo } from './detect'

export type TaskStatus = 'queued' | 'running' | 'retry_wait' | 'succeeded' | 'failed'
export interface DetectionTask {
  task_id: string
  status: TaskStatus
  image_count: number
  completed_images: number
  progress_percent: number
  attempts: number
  max_attempts: number
  model: DetectModelInfo
  requested_device: 'cpu' | 'cuda' | 'auto'
  actual_device: 'cpu' | 'cuda'
  fallback_reason: string | null
  error_code: string | null
  created_at: string
  updated_at: string
  next_attempt_at: string
  expires_at: string
  progress_source: 'redis' | 'mysql'
  result: BatchDetectData | null
}
