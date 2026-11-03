<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DetectRecord } from '@/types/detect'
import { getRecordDetail } from '@/api/detect'
import { formatDateTime } from '@/utils/format'
import SingleResult from '@/views/detect-task/components/SingleResult.vue'

const route = useRoute()
const router = useRouter()

const record = ref<DetectRecord | null>(null)
const loading = ref(false)
const error = ref(false)

async function fetchDetail(id: string) {
  loading.value = true
  error.value = false
  record.value = null
  try {
    const { data: res } = await getRecordDetail(id)
    if (res.success && res.data) {
      record.value = res.data
    } else {
      error.value = true
      ElMessage.error(res.error || '记录不存在')
    }
  } catch {
    error.value = true
    ElMessage.error('获取检测详情失败')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.back()
}

watch(
  () => route.params.id as string,
  (id) => { if (id) fetchDetail(id) },
  { immediate: true },
)
</script>

<template>
  <div class="record-detail-page">
    <!-- 顶部导航 -->
    <div class="page-header">
      <ElButton text @click="goBack">
        <ElIcon><ArrowLeft /></ElIcon>
        返回列表
      </ElButton>
      <h2 class="page-title">检测详情</h2>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="loading-wrapper" v-loading="true" />

    <!-- 错误态 -->
    <ElEmpty v-else-if="error" description="加载失败或记录不存在">
      <ElButton type="primary" @click="fetchDetail(route.params.id as string)">重试</ElButton>
    </ElEmpty>

    <!-- 正常展示 -->
    <template v-else-if="record">
      <!-- 元信息卡片 -->
      <div class="meta-card">
        <div class="meta-item">
          <span class="meta-label">检测时间</span>
          <span class="meta-value">{{ formatDateTime(record.created_at) }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">使用模型</span>
          <span class="meta-value">{{ record.models.name }} v{{ record.models.version }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">检测人</span>
          <span class="meta-value">{{ record.profiles.display_name }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">记录 ID</span>
          <span class="meta-value id-value">{{ record.id }}</span>
        </div>
      </div>

      <!-- 复用 SingleResult 展示核心检测结果 -->
      <SingleResult :result="record" />
    </template>
  </div>
</template>

<style scoped lang="scss">
.record-detail-page {
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.page-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);

  .page-title {
    font-size: 20px;
    font-weight: 700;
    color: var(--text-primary);
    margin: 0;
  }
}

.loading-wrapper {
  min-height: 300px;
}

.meta-card {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--spacing-md);
  padding: var(--spacing-md) var(--spacing-lg);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  background-color: #fafbfc;

  .meta-item {
    display: flex;
    flex-direction: column;
    gap: 2px;

    .meta-label {
      font-size: 12px;
      color: var(--text-secondary);
    }

    .meta-value {
      font-size: 14px;
      font-weight: 500;
      color: var(--text-primary);

      &.id-value {
        font-family: monospace;
        font-size: 12px;
        color: var(--text-regular);
      }
    }
  }
}
</style>
