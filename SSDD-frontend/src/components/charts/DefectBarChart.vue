<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import '@/utils/echarts' // ECharts 按需模块统一注册（见 src/utils/echarts.ts）

/** 柱状图单条数据项 */
export interface BarDataItem {
  name: string // 类别名称（显示在 X 轴）
  value: number // 数值（柱子高度）
  color: string // 柱子颜色
}

const props = defineProps<{
  data: BarDataItem[]
}>()

/**
 * 柱状图配置项。
 * 用于单张检测结果中展示各缺陷类别的数量统计。
 * 每根柱子代表一个缺陷类别，高度为检测到的缺陷数量。
 */
const option = computed(() => ({
  // tooltip：鼠标悬浮在柱子上时显示的提示框
  tooltip: {
    trigger: 'axis', // 触发方式：按坐标轴触发（同一 X 值下所有柱子一起显示）
    axisPointer: { type: 'shadow' }, // 指示器类型：阴影（柱状图常用）
    formatter: '{b}: {c} 个', // {b}=类别名, {c}=数值
  },
  // grid：图表在容器中的位置和边距
  grid: {
    left: 12,
    right: 12,
    top: 18,
    bottom: 4,
    containLabel: true, // 边距包含坐标轴文字，防止标签被截断
  },
  // X 轴：类目型，每个缺陷类别对应一根柱子
  xAxis: {
    type: 'category',
    data: props.data.map((d) => d.name),
    axisLabel: { fontSize: 12, color: '#6b7280' },
    axisLine: { lineStyle: { color: '#e5e7eb' } },
    axisTick: { show: false }, // 隐藏刻度线，更简洁
  },
  // Y 轴：数值型，minInterval:1 保证只显示整数刻度
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { fontSize: 12, color: '#6b7280' },
    splitLine: { lineStyle: { color: '#f3f4f6', type: 'dashed' } }, // 水平虚线网格
  },
  series: [
    {
      type: 'bar',
      barWidth: '45%', // 柱子宽度占分类宽度的 45%
      // label：柱子顶部显示具体数值
      label: {
        show: true,
        position: 'top',
        color: '#6b7280',
        fontSize: 12,
      },
      // 每根柱子单独设置颜色和圆角
      data: props.data.map((d) => ({
        value: d.value,
        itemStyle: {
          color: d.color,
          borderRadius: [4, 4, 0, 0], // 上方圆角，下方直角
        },
      })),
    },
  ],
}))
</script>

<template>
  <VChart :option="option" autoresize class="defect-bar-chart" />
</template>

<style scoped>
.defect-bar-chart {
  width: 100%;
  height: 280px;
}
</style>
