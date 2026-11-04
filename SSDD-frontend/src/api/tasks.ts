import api from './index'
import type { ApiResponse } from '@/types/api'
import type { DetectionTask } from '@/types/task'

const attempts = new WeakMap<File, { files: File[]; key: string }>()

export function submitTask(files: File[], options?: { signal?: AbortSignal; onProgress?: (value: number) => void }) {
  let attempt = files[0] ? attempts.get(files[0]) : undefined
  if (!attempt || attempt.files.length !== files.length || !attempt.files.every((file, i) => file === files[i])) {
    attempt = { files: [...files], key: crypto.randomUUID() }
    if (files[0]) attempts.set(files[0], attempt)
  }
  const body = new FormData()
  files.forEach((file) => body.append('images', file))
  return api.post<ApiResponse<DetectionTask>>('/api/detect/tasks', body, {
    headers: { 'Content-Type': 'multipart/form-data', 'Idempotency-Key': attempt.key },
    signal: options?.signal,
    timeout: 60_000,
    onUploadProgress: (event) => {
      if (event.total) options?.onProgress?.(Math.round(event.loaded / event.total * 100))
    },
  }).then((response) => {
    if (response.data.success && files[0] && attempts.get(files[0]) === attempt) attempts.delete(files[0])
    return response
  })
}

export function getTask(id: string, signal?: AbortSignal) {
  return api.get<ApiResponse<DetectionTask>>(`/api/detect/tasks/${encodeURIComponent(id)}`, { signal })
}
