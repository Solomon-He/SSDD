import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Model, ModelRuntime, UploadModelParams, UpdateModelParams } from '@/types/model'
import { getModels, getModelRuntime, uploadModel, updateModel, activateModel, deleteModel } from '@/api/models'
import { ElMessage } from 'element-plus'
import { invalidateHomeData } from '@/composables/useHomeData'

export const useModelsStore = defineStore('models', () => {
  // ---- State ----
  const models = ref<Model[]>([])
  const loading = ref(false)
  const runtime = ref<ModelRuntime | null>(null)

  async function fetchRuntime() {
    const { data: res } = await getModelRuntime()
    runtime.value = res.success && res.data ? res.data : null
  }

  // ---- Getters ----
  const activeModel = computed(() => models.value.find((m) => m.status === 'active'))

  // ---- Actions ----

  /** 设置模型列表 */
  function setModels(list: Model[]) {
    models.value = list
  }

  /**
   * 拉取模型列表（带缓存：已有数据时跳过请求）。
   * 传 force = true 可强制刷新。
   */
  async function fetchModels(force = false) {
    if (!force && models.value.length) return
    loading.value = true
    try {
      const [{ data: res }] = await Promise.all([getModels(), fetchRuntime()])
      if (res.success && res.data) {
        models.value = res.data
      }
    } finally {
      loading.value = false
    }
  }

  /** 上传新模型 */
  async function handleUploadModel(params: UploadModelParams) {
    loading.value = true
    try {
      const { data: res } = await uploadModel(params)
      if (res.success && res.data) {
        models.value.push(res.data)
        invalidateHomeData()
        ElMessage.success(res.message || '模型上传成功')
        return true
      }
      ElMessage.error(res.error || '模型上传失败')
      return false
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  /** 激活模型 */
  async function handleActivateModel(id: string) {
    loading.value = true
    try {
      const { data: res } = await activateModel(id)
      if (res.success && res.data) {
        // 将所有模型设为 inactive，再把目标模型设为 active
        models.value.forEach((m) => (m.status = 'inactive'))
        const target = models.value.find((m) => m.id === id)
        if (target) Object.assign(target, res.data)
        await fetchRuntime().catch(() => { runtime.value = null })
        invalidateHomeData()
        ElMessage.success(res.message || '模型已激活')
        return true
      }
      ElMessage.error(res.error || '激活失败')
      return false
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  /** 修改模型信息 */
  async function handleUpdateModel(id: string, params: UpdateModelParams) {
    loading.value = true
    try {
      const { data: res } = await updateModel(id, params)
      if (res.success && res.data) {
        const target = models.value.find((m) => m.id === id)
        if (target) Object.assign(target, res.data)
        invalidateHomeData()
        ElMessage.success(res.message || '模型信息已更新')
        return true
      }
      ElMessage.error(res.error || '更新失败')
      return false
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  /** 删除模型 */
  async function handleDeleteModel(id: string) {
    loading.value = true
    try {
      const { data: res } = await deleteModel(id)
      if (res.success) {
        models.value = models.value.filter((m) => m.id !== id)
        invalidateHomeData()
        ElMessage.success(res.message || '模型已删除')
        return true
      }
      ElMessage.error(res.error || '删除失败')
      return false
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  return {
    models,
    loading,
    runtime,
    activeModel,
    setModels,
    fetchModels,
    handleUploadModel,
    handleActivateModel,
    handleUpdateModel,
    handleDeleteModel,
  }
})
