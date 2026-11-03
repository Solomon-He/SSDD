<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getLogs, exportLogs } from '@/api/logs'
import { useAuthStore } from '@/stores/auth'
import { SsSearch, useSsSearch } from '@/components/SsSearch'
import { SsTable, useSsTable } from '@/components/SsTable'
import { SsPagination, useSsPagination } from '@/components/SsPagination'
import { downloadBlob } from '@/utils/download'
import { getTableConfig } from './table.config'
import { getSearchConfig } from './form.config'
import type { LogEntry, LogFilterParams } from '@/types/logs'

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isAdmin)

const logs = ref<LogEntry[]>([])
const exporting = ref(false)

// ---- SsSearch 配置 ----
const searchConfig = computed(() => getSearchConfig({ isAdmin: isAdmin.value }))
const { searchProps, searchModel, getSearchParams, handleSearch, handleReset } = useSsSearch(
  searchConfig,
  {
    onSearch: () => debouncedSearch(),
    onReset: () => {
      reset()
      fetchLogs()
    },
  },
)

/** 构建筛选参数（去除分页），fetchLogs 和 handleExport 共用 */
function buildFilterParams(): LogFilterParams {
  const params = getSearchParams()
  return {
    action: params.action || undefined,
    search: isAdmin.value ? params.search || undefined : undefined,
    role: isAdmin.value ? (params.role as LogFilterParams['role']) || undefined : undefined,
    startDate: params.dateRange?.[0] || undefined,
    endDate: params.dateRange?.[1] || undefined,
  }
}

// ---- SsPagination 配置 ----
const {
  current,
  pageSize,
  total,
  loading,
  paginationProps,
  reset,
} = useSsPagination({ onChange: () => fetchLogs() })

/** 请求序号，用于丢弃过期响应 */
let fetchId = 0

/** 拉取日志列表 */
async function fetchLogs() {
  const id = ++fetchId
  loading.value = true
  try {
    const { data: res } = await getLogs({
      ...buildFilterParams(),
      page: current.value,
      pageSize: pageSize.value,
    })
    if (id !== fetchId) return // 丢弃过期响应
    if (res.success && res.data) {
      logs.value = res.data.items
      total.value = res.data.total
    }
  } finally {
    if (id === fetchId) loading.value = false
  }
}

/** 防抖搜索（300ms），避免筛选项快速切换时发出大量请求 */
let searchTimer: ReturnType<typeof setTimeout> | null = null
function debouncedSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    reset()
    fetchLogs()
  }, 300)
}

/** 导出操作日志 CSV */
async function handleExport() {
  exporting.value = true
  try {
    const { data: blob } = await exportLogs(buildFilterParams())
    const date = new Date().toISOString().slice(0, 10)
    downloadBlob(blob, `operation_logs_${date}.csv`)
    ElMessage.success('日志导出成功')
  } catch {
    ElMessage.error('日志导出失败，请重试')
  } finally {
    exporting.value = false
  }
}

// ---- SsTable 配置（响应式，跟随 isAdmin 变化） ----
const columns = computed(() => getTableConfig({ isAdmin: isAdmin.value }))
const { tableConfig, tableInstance } = useSsTable(columns, {
  tableAttrs: { stripe: true, height: '100%', flexible: true },
})

onMounted(fetchLogs)
</script>

<template>
  <div class="logs-page">
    <!-- Header -->
    <div class="page-header">
      <div>
        <h2 class="page-title">操作日志</h2>
        <p class="page-desc">
          {{ isAdmin ? '查看系统所有用户的操作记录' : '查看您的操作记录' }}
        </p>
      </div>
      <ElButton :loading="exporting" :disabled="exporting" @click="handleExport">
        <template #icon><ElIcon><Download /></ElIcon></template>
        导出日志
      </ElButton>
    </div>

    <!-- Filter Bar -->
    <SsSearch
      v-model="searchModel"
      v-bind="searchProps"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- Log Table -->
    <SsTable
      v-model="logs"
      v-model:table-instance="tableInstance"
      v-bind="tableConfig"
      v-loading="loading"
    />

    <!-- Pagination -->
    <SsPagination v-bind="paginationProps" />
  </div>
</template>

<style scoped lang="scss">
.logs-page {
  height: 100%;
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  overflow: hidden;
  box-sizing: border-box;

  .page-header,
  :deep(.ss-search),
  :deep(.ss-pagination) {
    flex-shrink: 0;
  }

  :deep(.ss-table) {
    flex: 1;
    min-height: 0;
  }
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;

  .page-title {
    font-size: 20px;
    font-weight: 700;
    color: var(--text-primary);
    margin: 0;
  }

  .page-desc {
    font-size: 13px;
    color: var(--text-secondary);
    margin: 4px 0 0;
  }
}
</style>
