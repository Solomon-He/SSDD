import axios from 'axios'
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { DetectResult, BatchDetectData, DetectModelInfo } from '@/types/detect'
import { detectImage, detectBatch } from '@/api/detect'
import { ElMessage } from 'element-plus'
import { invalidateHomeData } from '@/composables/useHomeData'

export const useDetectStore = defineStore('detect', () => {
  // ---- State ----
  const currentResult = ref<DetectResult | null>(null)
  const batchData = ref<BatchDetectData | null>(null)
  const loading = ref(false)
  /** 最近一次检测的错误信息，成功时重置为 null */
  const error = ref<string | null>(null)
  /** 批量上传进度（0-100），非上传状态时为 null */
  const uploadProgress = ref<number | null>(null)

  /**
   * 当前正在进行的检测请求的 AbortController。
   * 用于在切换模式或发起新请求时取消上一次未完成的请求，
   * 避免旧请求的响应覆盖新状态（竞态条件）。
   */
  let abortController: AbortController | null = null

  // ---- Actions ----

  /** 单张检测 */
  async function runDetect(file: File) {
    // 取消上一次未完成的请求
    abortController?.abort()
    abortController = new AbortController()

    loading.value = true
    error.value = null
    try {
      const { data: res } = await detectImage(file, abortController.signal)
      if (res.success && res.data) {
        currentResult.value = res.data
        batchData.value = null
        invalidateHomeData()
        ElMessage.success('检测完成')
      }
    } catch (e: unknown) {
      // 主动取消的请求不视为错误
      if (axios.isCancel(e)) return
      // 网络/服务端错误：记录错误状态供视图层感知（如显示重试按钮）
      const msg = e instanceof Error ? e.message : '检测失败'
      error.value = msg
    } finally {
      loading.value = false
    }
  }

  /** 批量检测 */
  async function runBatchDetect(files: File[]) {
    abortController?.abort()
    abortController = new AbortController()

    loading.value = true
    error.value = null
    uploadProgress.value = 0
    try {
      const { data: res } = await detectBatch(files, {
        signal: abortController.signal,
        onProgress: (percent) => {
          uploadProgress.value = percent
        },
      })
      if (res.success && res.data) {
        batchData.value = res.data
        currentResult.value = null
        invalidateHomeData()
        ElMessage.success(`批量检测完成，共 ${res.data.total} 张`)
      }
    } catch (e: unknown) {
      if (axios.isCancel(e)) return
      const msg = e instanceof Error ? e.message : '批量检测失败'
      error.value = msg
    } finally {
      loading.value = false
      uploadProgress.value = null
    }
  }

  /** 清除所有检测结果及错误状态，同时取消进行中的请求 */
  function clearResults() {
    abortController?.abort()
    abortController = null
    currentResult.value = null
    batchData.value = null
    error.value = null
  }

  /** 当前检测使用的模型信息（响应式 computed，替代原普通函数） */
  const modelInfo = computed<DetectModelInfo | null>(() => {
    if (currentResult.value) return currentResult.value.model
    if (batchData.value) return batchData.value.model
    return null
  })

  return {
    currentResult,
    batchData,
    loading,
    error,
    uploadProgress,
    modelInfo,
    runDetect,
    runBatchDetect,
    clearResults,
  }
})
