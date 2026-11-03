<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import '@/utils/echarts'
import type { DetectRecord } from '@/types/detect'
import { createDefectPieOption, createTrendOption } from './chartOptions'

const props = defineProps<{
  records: DetectRecord[]
}>()

const defectPieOption = computed(() => createDefectPieOption(props.records))
const trendOption = computed(() => createTrendOption(props.records))
</script>

<template>
  <section class="home-charts">
    <h2 class="section-title">数据可视化</h2>
    <div class="charts-row">
      <div class="chart-card">
        <h3 class="chart-card__title">缺陷类型分布</h3>
        <p class="chart-card__subtitle">基于最近 {{ records.length }} 条检测记录</p>
        <VChart :option="defectPieOption" autoresize class="chart" />
      </div>
      <div class="chart-card">
        <h3 class="chart-card__title">检测活动趋势</h3>
        <p class="chart-card__subtitle">按日期统计检测次数</p>
        <VChart :option="trendOption" autoresize class="chart" />
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@/assets/styles/mixins' as *;

.section-title {
  @include section-title;
}

.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.chart-card {
  background: #fff;
  border-radius: 10px;
  padding: 24px;
  border: 1px solid var(--border-color);

  &__title {
    font-size: 15px;
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: 2px;
  }

  &__subtitle {
    font-size: 12px;
    color: var(--text-placeholder);
    margin-bottom: 16px;
  }
}

.chart {
  width: 100%;
  height: 300px;
}
</style>
