import { ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { getRecords } from '@/api/detect'
import { getAdminStats } from '@/api/admin'
import { getModels } from '@/api/models'
import type { DetectRecord } from '@/types/detect'
import type { SystemStats } from '@/types/admin'
import type { Model } from '@/types/model'

/** 缓存有效期（毫秒） */
const CACHE_TTL = 30 * 1000

// ---- 模块级缓存（跨组件实例共享） ----
let lastFetchTime = 0
let cachedScope = ''
let fetchGeneration = 0

export function invalidateHomeData() {
  lastFetchTime = 0
  fetchGeneration++
}

const loading = ref(true)
const myRecords = ref<DetectRecord[]>([])
const myTotal = ref(0)
const chartRecords = ref<DetectRecord[]>([])
const adminStats = ref<SystemStats | null>(null)
const activeModel = ref<Model | null>(null)

/**
 * 首页数据获取 composable
 * - 模块级缓存：多次调用在 TTL 内复用已有数据
 * - refresh(force) 可强制刷新
 */
export function useHomeData() {
  const auth = useAuthStore()
  watch(() => `${auth.user?.id ?? ''}:${auth.userRole}`, (scope) => {
    if (scope === cachedScope) return
    cachedScope = scope
    invalidateHomeData()
    myRecords.value = []
    myTotal.value = 0
    chartRecords.value = []
    adminStats.value = null
    activeModel.value = null
    loading.value = false
  }, { immediate: true })

  /** 基于 chartRecords（全量）计算的平均缺陷数 */
  function avgDefectCount(): number {
    if (!chartRecords.value.length) return 0
    return chartRecords.value.reduce((s, r) => s + r.defect_count, 0) / chartRecords.value.length
  }

  /** 基于 chartRecords（全量）计算的平均推理耗时 */
  function avgInferenceTime(): number {
    if (!chartRecords.value.length) return 0
    return (
      chartRecords.value.reduce((s, r) => s + r.inference_time_ms, 0) / chartRecords.value.length
    )
  }

  async function refresh(force = false) {
    // TTL 内且非强制 → 跳过
    if (!force && Date.now() - lastFetchTime < CACHE_TTL && lastFetchTime > 0) return

    const generation = ++fetchGeneration

    loading.value = true
    try {
      const myParams: Record<string, unknown> = { page: 1, pageSize: 50 }
      if (auth.isAdmin && auth.user?.id) {
        myParams.userId = auth.user.id
      }

      const myReq = getRecords(myParams)
      const chartReq = getRecords({ all: true })
      const modelsReq = getModels()
      const statsReq = auth.isAdmin ? getAdminStats() : null

      const [myRes, chartRes, modelsRes, statsRes] = await Promise.all([
        myReq.catch(() => null),
        chartReq.catch(() => null),
        modelsReq.catch(() => null),
        statsReq?.catch(() => null) ?? Promise.resolve(null),
      ])
      if (generation !== fetchGeneration) return

      if (myRes?.data?.success && myRes.data.data) {
        myRecords.value = myRes.data.data.items
        myTotal.value = myRes.data.data.total
      }
      if (chartRes?.data?.success && chartRes.data.data) {
        chartRecords.value = chartRes.data.data.items
      }
      if (modelsRes?.data?.success && modelsRes.data.data) {
        activeModel.value = modelsRes.data.data.find((m) => m.status === 'active') || null
      }
      if (statsRes?.data?.success && statsRes.data.data) {
        adminStats.value = statsRes.data.data
      }

      lastFetchTime = Date.now()
    } finally {
      if (generation === fetchGeneration) loading.value = false
    }
  }

  return {
    loading,
    myRecords,
    myTotal,
    chartRecords,
    adminStats,
    activeModel,
    avgDefectCount,
    avgInferenceTime,
    refresh,
  }
}
