<script setup lang="ts">
import type { SystemStats } from '@/types/admin'
import { computed } from 'vue'

const props = defineProps<{
  stats: SystemStats
}>()

const cards = computed(() => {
  const s = props.stats
  return [
    { label: '总用户数', value: String(s.total_users), color: '#3b82f6' },
    { label: '总检测次数', value: s.total_detections.toLocaleString(), color: '#10b981' },
    { label: '模型数量', value: String(s.total_models), color: '#8b5cf6' },
    { label: '平均推理耗时', value: `${(s.avg_inference_time_ms ?? 0).toFixed(1)} ms`, color: '#f59e0b' },
    { label: '平均缺陷数量', value: (s.avg_defect_count ?? 0).toFixed(1), color: '#ef4444' },
  ]
})
</script>

<template>
  <section class="admin-stats">
    <h2 class="section-title">系统概览</h2>
    <div class="stats-grid">
      <div
        v-for="(card, i) in cards"
        :key="i"
        class="stat-card"
        :style="{ '--accent': card.color, animationDelay: `${i * 0.06}s` }"
      >
        <div class="stat-card__value">{{ card.value }}</div>
        <div class="stat-card__label">{{ card.label }}</div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@/assets/styles/mixins' as *;

@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.section-title {
  @include section-title;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 10px;
  padding: 20px 22px;
  border: 1px solid var(--border-color);
  border-top: 3px solid var(--accent, var(--color-primary));
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
  animation: fadeUp 0.4s ease both;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-md);
  }

  &__value {
    font-size: 28px;
    font-weight: 700;
    color: var(--text-primary);
    line-height: 1.2;
    letter-spacing: -0.5px;
  }

  &__label {
    font-size: 13px;
    color: var(--text-secondary);
    margin-top: 4px;
  }
}
</style>
