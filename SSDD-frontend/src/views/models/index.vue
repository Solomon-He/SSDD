<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useModelsStore } from '@/stores/models'
import { formatDateTime } from '@/utils/format'
import type { Model } from '@/types/model'
import UploadModelDialog from './components/UploadModelDialog.vue'
import EditModelDialog from './components/EditModelDialog.vue'

const modelsStore = useModelsStore()
const uploadDialogRef = ref<InstanceType<typeof UploadModelDialog> | null>(null)
const editDialogRef = ref<InstanceType<typeof EditModelDialog> | null>(null)

onMounted(() => modelsStore.fetchModels(true))

/** 激活模型 — 确认弹窗 */
async function handleActivate(model: Model) {
  try {
    await ElMessageBox.confirm(
      `确认激活模型「${model.name}」(${model.version})？激活后将替代当前推理模型。`,
      '激活模型',
      { confirmButtonText: '确认激活', cancelButtonText: '取消', type: 'warning' },
    )
    await modelsStore.handleActivateModel(model.id)
  } catch {
    // 用户取消
  }
}

/** 删除模型 — 确认弹窗 */
async function handleDelete(model: Model) {
  try {
    await ElMessageBox.confirm(
      `确认删除模型「${model.name}」(${model.version})？此操作不可撤销，模型文件将被永久移除。`,
      '删除模型',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'error' },
    )
    await modelsStore.handleDeleteModel(model.id)
  } catch {
    // 用户取消
  }
}

/** 格式化指标值 */
function fmtMetric(val: number | null, unit = '', decimals = 3) {
  if (val == null) return '—'
  return val.toFixed(decimals) + (unit ? ` ${unit}` : '')
}

/** 百分比格式化（保留 2 位小数） */
function fmtPercent(val: number | null) {
  if (val == null) return '—'
  return (val * 100).toFixed(2) + '%'
}
</script>

<template>
  <div class="models-page">
    <!-- Header -->
    <div class="page-header">
      <div>
        <h2 class="page-title">模型管理</h2>
        <p class="page-desc">管理系统中的 ONNX 推理模型，同一时刻仅可激活一个模型</p>
      </div>
      <ElButton type="primary" @click="uploadDialogRef?.open()">
        <ElIcon><Plus /></ElIcon>
        上传模型
      </ElButton>
    </div>

    <!-- Model List -->
    <div v-loading="modelsStore.loading" class="model-list">
      <div v-if="!modelsStore.loading && modelsStore.models.length === 0" class="empty-state">
        <ElEmpty description="暂无模型，请上传第一个模型" />
      </div>

      <div v-for="model in modelsStore.models" :key="model.id" class="model-card">
        <div class="model-card__header">
          <div class="model-card__title-row">
            <span class="model-card__name">{{ model.name }}</span>
            <ElTag
              :type="model.status === 'active' ? 'success' : 'info'"
              size="small"
              effect="light"
            >
              {{ model.status === 'active' ? '运行中' : '未激活' }}
            </ElTag>
          </div>
          <span class="model-card__version">v{{ model.version }}</span>
        </div>

        <p v-if="model.description" class="model-card__desc">{{ model.description }}</p>

        <!-- 性能指标  -->
        <div class="model-card__metrics">
          <div class="metric-item">
            <span class="metric-label">mIoU</span>
            <span class="metric-value">{{ fmtMetric(model.miou) }}</span>
          </div>
          <div class="metric-item">
            <span class="metric-label">Precision</span>
            <span class="metric-value">{{ fmtPercent(model.precision) }}</span>
          </div>
          <div class="metric-item">
            <span class="metric-label">Recall</span>
            <span class="metric-value">{{ fmtPercent(model.recall) }}</span>
          </div>
          <div class="metric-item">
            <span class="metric-label">Dice</span>
            <span class="metric-value">{{ fmtMetric(model.dice) }}</span>
          </div>
          <div class="metric-item">
            <span class="metric-label">FPS</span>
            <span class="metric-value">{{ fmtMetric(model.fps, '', 2) }}</span>
          </div>
          <div class="metric-item">
            <span class="metric-label">文件大小</span>
            <span class="metric-value">{{ fmtMetric(model.file_size, 'MB', 2) }}</span>
          </div>
        </div>

        <!-- 底部：元信息 + 操作 -->
        <div class="model-card__footer">
          <div class="model-card__meta">
            <span>文件：{{ model.file_name }}</span>
            <span>上传于 {{ formatDateTime(model.created_at) }}</span>
          </div>
          <div class="model-card__actions">
            <ElButton
              text
              size="small"
              @click="editDialogRef?.open(model)"
            >
              编辑
            </ElButton>
            <ElButton
              v-if="model.status !== 'active'"
              type="primary"
              text
              size="small"
              @click="handleActivate(model)"
            >
              激活
            </ElButton>
            <ElButton
              v-if="model.status !== 'active'"
              type="danger"
              text
              size="small"
              @click="handleDelete(model)"
            >
              删除
            </ElButton>
            <ElTooltip v-if="model.status === 'active'" content="当前激活模型不可操作" placement="top">
              <span class="active-hint">当前使用中</span>
            </ElTooltip>
          </div>
        </div>
      </div>
    </div>

    <!-- Upload Dialog -->
    <UploadModelDialog ref="uploadDialogRef" />
    <!-- Edit Dialog -->
    <EditModelDialog ref="editDialogRef" />
  </div>
</template>

<style scoped lang="scss">
.models-page {
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
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

.model-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  min-height: 200px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

.model-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: var(--shadow-sm);
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__title-row {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
  }

  &__name {
    font-size: 16px;
    font-weight: 600;
    color: var(--text-primary);
  }

  &__version {
    font-size: 13px;
    color: var(--text-secondary);
    font-family: 'SF Mono', 'Fira Code', monospace;
  }

  &__desc {
    font-size: 13px;
    color: var(--text-regular);
    margin: 0;
    line-height: 1.5;
  }

  &__metrics {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: var(--spacing-sm);
    padding: var(--spacing-sm) 0;
    border-top: 1px solid var(--border-color);
    border-bottom: 1px solid var(--border-color);

    .metric-item {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .metric-label {
      font-size: 11px;
      color: var(--text-placeholder);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .metric-value {
      font-size: 14px;
      font-weight: 600;
      color: var(--text-primary);
      font-family: 'SF Mono', 'Fira Code', monospace;
    }
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-top: var(--spacing-xs);
  }

  &__meta {
    display: flex;
    flex-direction: column;
    gap: 2px;
    font-size: 12px;
    color: var(--text-placeholder);
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--spacing-xs);

    .active-hint {
      font-size: 12px;
      color: var(--color-success);
      font-weight: 500;
    }
  }
}
</style>
