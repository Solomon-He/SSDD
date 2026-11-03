<script setup lang="ts">
import type { DetectModelInfo } from '@/types/detect'

defineProps<{
  model: DetectModelInfo | null
}>()
</script>

<template>
  <div class="model-info-card">
    <div class="card-header">
      <ElIcon :size="16"><Cpu /></ElIcon>
      <span>当前推理模型</span>
    </div>
    <template v-if="model">
      <div class="info-row">
        <span class="label">模型名称</span>
        <span class="value">{{ model.name }}</span>
      </div>
      <div class="info-row">
        <span class="label">版本</span>
        <ElTag size="small" type="info">{{ model.version }}</ElTag>
      </div>
    </template>
    <div v-else class="no-model">
      <ElIcon :size="16" color="var(--text-placeholder)"><Warning /></ElIcon>
      <span>暂无激活模型</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.model-info-card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  background-color: var(--bg-card);

  .card-header {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
  }

  .info-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 13px;

    .label {
      color: var(--text-secondary);
    }

    .value {
      color: var(--text-primary);
      font-weight: 500;
    }
  }

  .no-model {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: var(--text-placeholder);
  }
}
</style>
