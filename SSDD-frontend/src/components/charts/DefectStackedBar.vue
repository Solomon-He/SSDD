<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import '@/utils/echarts' // ECharts 按需模块统一注册（见 src/utils/echarts.ts）
import type { BatchDetectItem } from '@/types/detect'
import { DEFECT_CLASSES } from '@/constants/defects'

const props = defineProps<{
  results: BatchDetectItem[] // 批量检测结果数组
}>()

/**
 * 堆叠柱状图配置项。
 * 用于批量检测场景下，对比各张图片的缺陷类别构成。
 * 每根柱子代表一张图片，堆叠的不同颜色区域分别表示各缺陷类别的数量。
 */
const option = computed(() => {
  // X 轴标签：所有图片的文件名
  const xLabels = props.results.map((r) => r.file_name)
  // 是否显示 dataZoom 组件（当图片数量较多时启用）
  const showDataZoom = props.results.length > 12

  /**
   * 为每张图片找到最顶部的非零堆叠段（即 DEFECT_CLASSES 中最后一个有值的类别），
   * 只对该段施加顶部圆角，其余段保持直角。
   */
  const topClassIdx = props.results.map((r) => {
    for (let k = DEFECT_CLASSES.length - 1; k >= 0; k--) {
      if ((r.defect_class_counts[String(DEFECT_CLASSES[k]!.index)] || 0) > 0) {
        return k
      }
    }
    return -1 // 该图片无缺陷
  })

  /**
   * 为每个缺陷类别创建一个 series。
   * 所有 series 共享同一个 stack 名称 'defects'，
   * ECharts 会将相同 stack 的 series 堆叠在同一根柱子上。
   */
  const series = DEFECT_CLASSES.map((cls, clsIdx) => ({
    name: cls.label, // 显示在图例和 tooltip 中
    type: 'bar' as const,
    stack: 'defects', // 相同 stack 名的 series 会堆叠显示
    barWidth: '50%',
    emphasis: { focus: 'series' as const }, // 悬浮时高亮整个 series，淡化其他
    itemStyle: {
      color: cls.color, // 缺陷类别对应的颜色
    },
    // 每张图片对应该类别的缺陷数量；若该段是该柱子的最顶层则加圆角
    data: props.results.map((r, i) => {
      const value = r.defect_class_counts[String(cls.index)] || 0
      if (topClassIdx[i] === clsIdx && value > 0) {
        return { value, itemStyle: { borderRadius: [4, 4, 0, 0] } }
      }
      return value
    }),
  }))

  return {
    // tooltip：悬浮显示某张图片的所有缺陷类别数量
    tooltip: {
      trigger: 'axis', // 按坐标轴触发，同时显示所有堆叠层
      axisPointer: { type: 'shadow' }, // 阴影指示器
    },
    // legend：图例，显示各缺陷类别颜色标识
    legend: {
      bottom: 15,
      itemWidth: 12,
      itemHeight: 12,
      textStyle: { fontSize: 12, color: '#6b7280' },
    },
    // grid：图表在容器中的布局位置
    grid: {
      left: 16,
      right: 16,
      top: 16,
      bottom: 40, // 给图例和 dataZoom 滑块留出空间
      containLabel: true, // 边距包含坐标轴标签
    },
    // X 轴：类目型，每张图片对应一根堆叠柱子
    xAxis: {
      type: 'category',
      data: xLabels,
      axisLabel: {
        fontSize: 11,
        color: '#6b7280',
        rotate: xLabels.length > 6 ? 35 : 0, // 图片多时旋转标签避免重叠
        overflow: 'truncate',
        width: 80,
      },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisTick: { show: false },
    },
    // Y 轴：数值型，minInterval:1 只显示整数
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { fontSize: 12, color: '#6b7280' },
      splitLine: { lineStyle: { color: '#f3f4f6' } },
    },
    /**
     * dataZoom：数据区域缩放，解决图片过多时 X 轴拥挤的问题。
     * - inside：支持鼠标滚轮 / 触屏手势缩放
     * - slider：底部可拖拽的滑动条
     */
    dataZoom: showDataZoom
      ? [
          {
            type: 'inside', // 内置缩放，通过鼠标操作
            xAxisIndex: 0,
            start: 0,
            // 超过 12 张图片时默认只显示前 12 张
            end: props.results.length > 12 ? Math.floor((12 / props.results.length) * 100) : 100,
          },
          {
            type: 'slider', // 底部可见的滑动条
            xAxisIndex: 0,
            bottom: 0,
            height: 12,
            showDetail: false,
          },
        ]
      : undefined,
    series, // 上方构建的多个堆叠柱状图 series
  }
})
</script>

<template>
  <VChart :option="option" autoresize class="defect-stacked-bar" />
</template>

<style scoped>
.defect-stacked-bar {
  width: 100%;
  height: 360px;
}
</style>
