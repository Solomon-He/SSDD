import { ref, watch, onScopeDispose } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { submitTask, getTask } from '@/api/tasks'
import { invalidateHomeData } from '@/composables/useHomeData'
import type { DetectionTask } from '@/types/task'

const uuid = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i

export function useAsyncDetection() {
  const auth = useAuthStore()
  const task = ref<DetectionTask | null>(null)
  const taskId = ref('')
  const submitting = ref(false)
  const polling = ref(false)
  const uploadProgress = ref<number | null>(null)
  const error = ref<string | null>(null)
  let generation = 0
  let timer: ReturnType<typeof setTimeout> | undefined
  let request: AbortController | null = null
  let failures = 0
  let published = ''
  const storageKey = () => `ssdd:last-task:${auth.user?.id ?? ''}`
  function stop() {
    generation++
    if (timer !== undefined) clearTimeout(timer)
    timer = undefined
    request?.abort();request = null
    polling.value = false
    submitting.value = false
    uploadProgress.value = null
  }
  function remember(id: string) { try { localStorage.setItem(storageKey(), id) } catch { /* optional browser storage */ } }
  function apply(value: DetectionTask) {
    task.value = value;taskId.value = value.task_id;remember(value.task_id)
    if (value.status === 'succeeded' && published !== value.task_id) {
      published = value.task_id;invalidateHomeData()
    }
  }
  async function poll(id: string, current: number) {
    if (current !== generation) return
    polling.value = true;request = new AbortController()
    try {
      const { data } = await getTask(id, request.signal)
      if (current !== generation) return
      if (!data.success || !data.data) throw new Error(data.error || '任务查询失败')
      apply(data.data);failures = 0;error.value = null
      if (data.data.status === 'succeeded' || data.data.status === 'failed') { polling.value = false;return }
      timer = setTimeout(() => { void poll(id, current) }, 1000)
    } catch (failure) {
      if (current !== generation) return
      failures++
      if (failures < 3) timer = setTimeout(() => { void poll(id, current) }, 2000)
      else { polling.value = false;error.value = '任务查询暂不可用，请使用任务号恢复查询。后台任务不会因此取消。' }
    }
  }
  async function load(id = taskId.value) {
    stop();error.value = null
    id = id.trim()
    if (!uuid.test(id)) { error.value = '请输入有效的任务号';return }
    taskId.value = id;task.value = null;failures = 0
    await poll(id, generation)
  }
  async function submit(files: File[]) {
    stop();const current = generation
    task.value = null;error.value = null;submitting.value = true;uploadProgress.value = 0;request = new AbortController()
    try {
      const { data } = await submitTask(files, { signal: request.signal, onProgress: (value) => { if (current === generation) uploadProgress.value = value } })
      if (current !== generation) return
      if (!data.success || !data.data) throw new Error(data.error || '任务提交失败')
      apply(data.data);failures = 0
      if (data.data.status !== 'succeeded' && data.data.status !== 'failed') void poll(data.data.task_id, current)
    } catch (failure) {
      if (current === generation) error.value = failure instanceof Error ? failure.message : '任务提交失败，可以重试相同图片'
    } finally {
      if (current === generation) { submitting.value = false;uploadProgress.value = null }
    }
  }
  function reset() { stop();task.value = null;taskId.value = '';error.value = null }
  watch(() => `${auth.user?.id ?? ''}:${auth.userRole}`, () => {
    reset();published = ''
    try { const saved = localStorage.getItem(storageKey());if (saved && uuid.test(saved)) taskId.value = saved } catch { /* optional storage */ }
  }, { immediate: true, flush: 'sync' })
  onScopeDispose(stop)
  return { task, taskId, submitting, polling, uploadProgress, error, submit, load, stop, reset }
}
