<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DetectRecord } from '@/types/detect'
import { getRecords, exportRecords } from '@/api/detect'
import { useAuthStore } from '@/stores/auth'
import { useImageUrl } from '@/composables/useImageUrl'
import { formatDateTime } from '@/utils/format'
import { downloadBlob } from '@/utils/download'
import DefectTag from '@/components/common/DefectTag.vue'
import { SsSearch, useSsSearch } from '@/components/SsSearch'
import { SsPagination, useSsPagination } from '@/components/SsPagination'
import { getSearchConfig } from './form.config'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { toFullUrl } = useImageUrl()
const isAdmin = computed(() => authStore.isAdmin)

const records = ref<DetectRecord[]>([])

// ---- SsSearch 配置 ----
const searchConfig = computed(() => getSearchConfig({ isAdmin: isAdmin.value }))
const { searchProps, searchModel, getSearchParams, handleSearch, handleReset } = useSsSearch(
  searchConfig,
  {
    onSearch: () => {
      reset()
      syncQuery()
      fetchRecords()
    },
    onReset: () => {
      reset()
      syncQuery()
      fetchRecords()
    },
  },
)

// 从 URL query 恢复搜索词
if (route.query.search) {
  searchModel.search = route.query.search as string
}

/** 将当前状态同步到 URL query（replace 不产生新历史记录） */
function syncQuery() {
  const query: Record<string, string> = {}
  if (current.value > 1) query.page = String(current.value)
  if (pageSize.value !== 10) query.pageSize = String(pageSize.value)
  const params = getSearchParams()
  if (params.search) query.search = params.search as string
  router.replace({ query })
}

// ---- SsPagination 配置 ----
const {
  current,
  pageSize,
  total,
  loading,
  paginationProps,
  reset,
} = useSsPagination({
  defaultPageSize: Number(route.query.pageSize) || 10,
  onChange: () => {
    syncQuery()
    fetchRecords()
  },
})

// 从 URL query 恢复页码
current.value = Number(route.query.page) || 1

async function fetchRecords() {
  loading.value = true
  try {
    const searchParams = getSearchParams()
    const params: { page?: number; pageSize?: number; search?: string } = {
      page: current.value,
      pageSize: pageSize.value,
    }
    if (isAdmin.value && searchParams.search) {
      params.search = searchParams.search as string
    }
    const { data: res } = await getRecords(params)
    if (res.success && res.data) {
      records.value = res.data.items
      total.value = res.data.total
    }
  } catch {
    ElMessage.error('获取检测记录失败')
  } finally {
    loading.value = false
  }
}

function goDetail(record: DetectRecord) {
  router.push({ name: 'record-detail', params: { id: record.id } })
}

const exporting = ref(false)
async function handleExport() {
  exporting.value = true
  try {
    const searchParams = getSearchParams()
    const params: Record<string, string> = {}
    if (searchParams.search) params.search = searchParams.search as string
    const { data } = await exportRecords(params)
    downloadBlob(data, `检测记录_${new Date().toISOString().slice(0, 10)}.csv`)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

/** 获取缺陷类别索引列表（有数量 > 0 的） */
function getActiveDefects(record: DetectRecord): number[] {
  return Object.entries(record.defect_class_counts)
    .filter(([, count]) => count > 0)
    .map(([key]) => Number(key))
}

fetchRecords()
</script>

<template>
  <div class="detect-records-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">检测历史</h2>
        <p class="page-desc">查看所有检测记录</p>
      </div>
      <ElButton :loading="exporting" :disabled="exporting" @click="handleExport">
        <template #icon><ElIcon><Download /></ElIcon></template>
        导出记录
      </ElButton>
    </div>

    <!-- 搜索栏（管理员可见，由 SsSearch 的 isHide 控制） -->
    <SsSearch
      v-model="searchModel"
      v-bind="searchProps"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 表格 -->
    <ElTable
      :data="records"
      v-loading="loading"
      height="100%"
      flexible
      class="records-table"
    >
      <!-- 缩略图 -->
      <ElTableColumn label="原图" width="100" align="center">
        <template #default="{ row }">
          <img
            :src="toFullUrl(row.original_image_url)"
            alt="缩略图"
            loading="lazy"
            class="thumb-img"
          />
        </template>
      </ElTableColumn>

      <!-- 检测时间 -->
      <ElTableColumn label="检测时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.created_at) }}
        </template>
      </ElTableColumn>

      <!-- 缺陷数量 -->
      <ElTableColumn label="缺陷数" width="90" align="center">
        <template #default="{ row }">
          <span :class="['defect-count', { 'has-defect': row.defect_count > 0 }]">
            {{ row.defect_count }}
          </span>
        </template>
      </ElTableColumn>

      <!-- 缺陷类型 -->
      <ElTableColumn label="缺陷类型" min-width="200">
        <template #default="{ row }">
          <div class="defect-tags">
            <DefectTag
              v-for="idx in getActiveDefects(row)"
              :key="idx"
              :class-index="idx"
            />
            <span v-if="row.defect_count === 0" class="no-defect">无缺陷</span>
          </div>
        </template>
      </ElTableColumn>

      <!-- 模型 -->
      <ElTableColumn label="使用模型" width="180">
        <template #default="{ row }">
          {{ row.models.name }} <span class="model-version">v{{ row.models.version }}</span>
        </template>
      </ElTableColumn>

      <!-- 检测人（管理员可见） -->
      <ElTableColumn v-if="authStore.isAdmin" label="检测人" width="120">
        <template #default="{ row }">
          {{ row.profiles.display_name }}
        </template>
      </ElTableColumn>

      <!-- 操作 -->
      <ElTableColumn label="操作" width="90" align="center" fixed="right">
        <template #default="{ row }">
          <ElButton type="primary" text size="small" @click.stop="goDetail(row)">
            详情
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <!-- 空状态 -->
    <ElEmpty v-if="!loading && records.length === 0" description="暂无检测记录" />

    <!-- 分页 -->
    <SsPagination v-bind="paginationProps"/>
  </div>
</template>

<style scoped lang="scss">
.detect-records-page {
  height: 100%;
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  overflow: hidden;
  box-sizing: border-box;

  .page-header,
  :deep(.ss-search),
  :deep(.ss-pagination),
  .el-empty {
    flex-shrink: 0;
  }

  .records-table {
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

.thumb-img {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid var(--border-color);
}

.defect-count {
  font-weight: 600;

  &.has-defect {
    color: var(--color-danger);
  }
}

.defect-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;

  .no-defect {
    font-size: 12px;
    color: var(--text-secondary);
  }
}

.model-version {
  font-size: 12px;
  color: var(--text-secondary);
}

</style>
