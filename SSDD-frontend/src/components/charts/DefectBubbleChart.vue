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
 * 气泡图配置项（computed 保证数据变化时自动更新图表）
 *
 * 图表结构：
 * - X 轴：每张图片的文件名
 * - Y 轴：缺陷面积占比 (%)
 * - 气泡大小：与占比成正比，越大表示该缺陷扮演的面积越大
 * - 颜色：各缺陷类别对应的主题色
 */
const option = computed(() => {
  // X 轴标签：所有图片的文件名
  const xLabels = props.results.map((r) => r.file_name)
  // 是否显示 dataZoom 组件（当图片数量较多时启用）
  const showDataZoom = props.results.length > 10

  // 收集所有占比值，用于计算气泡大小的映射范围
  const allRatios: number[] = []

  /**
   * 为每个缺陷类别创建一个 series（数据系列）。
   * ECharts 中一个 series 代表一组相同类型的数据，
   * 多个 series 可以叠加在同一个坐标系上，并在图例中分别显示。
   */
  const series = DEFECT_CLASSES.map((cls) => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const data: any[] = []
    props.results.forEach((r, xIndex) => {
      const totalPixels = r.defect_info.total_pixels
      // 获取该类别的缺陷像素数
      const clsPixels = r.defect_info.class_distribution[String(cls.index)] || 0
      if (clsPixels > 0) {
        // 计算该类缺陷占整张图片的面积百分比
        const ratio = (clsPixels / totalPixels) * 100
        allRatios.push(ratio)
        // 数据点格式：[X索引, Y值(占比), 像素数]
        data.push({
          value: [xIndex, ratio, clsPixels],
          name: r.file_name,
        })
      }
    })
    return {
      name: cls.label, // series 名称，显示在图例和 tooltip 中
      type: 'scatter' as const, // 图表类型：散点图
      data,
      itemStyle: {
        color: cls.color, // 缺陷类别对应的颜色
        opacity: 0.8, // 半透明，避免气泡重叠时完全遮挡
        borderColor: '#fff',
        borderWidth: 1,
      },
      // emphasis：鼠标悬浮时的高亮效果
      emphasis: {
        itemStyle: { opacity: 1, shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.2)' },
      },
    }
  })

  /**
   * 动态设置气泡大小的映射函数。
   * symbolSize 可以是固定值或函数，这里用函数根据数据点的占比动态计算。
   * 使用平方根曲线（sqrt）保证气泡面积与数值成正比，
   * 而非半径与数值成正比（后者会导致视觉失真）。
   */
  const maxRatio = Math.max(...allRatios, 0.1) // 最大占比值，保底 0.1 防止除以零
  series.forEach((s) => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ;(s as any).symbolSize = (val: any) => {
      const ratio = val[1] // Y 值即占比
      const minSize = 10 // 最小气泡尺寸，保证可见性
      const maxSize = 46 // 最大气泡尺寸
      return Math.max(minSize, Math.sqrt(ratio / maxRatio) * maxSize)
    }
  })

  return {
    // tooltip：鼠标悬浮时显示的提示框
    tooltip: {
      trigger: 'item', // 触发方式：单个数据点触发
      // 自定义 tooltip 内容格式
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      formatter: (p: any) => {
        const fileName = xLabels[p.value[0]]
        const ratio = p.value[1].toFixed(2)
        const pixels = p.value[2]
        return `${fileName}<br/><b>${p.seriesName}</b><br/>面积占比：${ratio}%<br/>像素数：${pixels} px`
      },
    },
    // legend：图例，显示各缺陷类别的颜色标识
    legend: {
      bottom: 15,
      itemWidth: 12,
      itemHeight: 12,
      textStyle: { fontSize: 12, color: '#6b7280' },
    },
    // grid：图表在容器中的布局位置（上右下左边距）
    grid: {
      left: 16,
      right: 24,
      top: 16,
      bottom: 48, // 给图例和 dataZoom 滑块留出空间
      containLabel: true, // 边距包含坐标轴标签，防止标签被截断
    },
    // X 轴配置：类目型（category），每张图片对应一个分类
    xAxis: {
      type: 'category',
      data: xLabels,
      axisLabel: {
        fontSize: 11,
        color: '#6b7280',
        rotate: xLabels.length > 6 ? 35 : 0, // 图片较多时旋转标签避免重叠
        overflow: 'truncate', // 超出截断
        width: 80,
      },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      splitLine: { show: true, lineStyle: { color: '#f9fafb', type: 'dashed' } },
    },
    // Y 轴配置：数值型（value），显示缺陷面积占比百分比
    yAxis: {
      type: 'value',
      name: '缺陷面积占比 (%)', // 轴标题
      nameTextStyle: { fontSize: 12, color: '#6b7280' },
      axisLabel: { fontSize: 12, color: '#6b7280', formatter: '{value}%' }, // 标签带 % 后缀
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      splitLine: { show: true, lineStyle: { color: '#f3f4f6' } }, // 水平网格线
    },
    /**
     * dataZoom：数据区域缩放控件，解决图片过多时 X 轴拥挤的问题。
     * - inside：支持鼠标滚轮 / 触屏手势缩放
     * - slider：底部可拖拽的滑动条
     */
    dataZoom: showDataZoom
      ? [
          {
            type: 'inside', // 内置缩放，不显示 UI，通过鼠标操作
            xAxisIndex: 0,
            start: 0,
            // 超过 10 张图片时默认只显示前 10 张，其余可拖动查看
            end: Math.floor((10 / props.results.length) * 100),
          },
          {
            type: 'slider', // 底部可见的滑动条
            xAxisIndex: 0,
            bottom: 0,
            height: 12,
            showDetail: false, // 不显示拖拽时的详细数值
          },
        ]
      : undefined,
    series, // 上方构建的多个散点图 series
  }
})
</script>

<template>
  <VChart :option="option" autoresize class="defect-bubble-chart" />
</template>

<style scoped>
.defect-bubble-chart {
  width: 100%;
  height: 360px;
}
</style>
