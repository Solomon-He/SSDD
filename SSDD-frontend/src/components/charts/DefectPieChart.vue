<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import '@/utils/echarts' // ECharts 按需模块统一注册（见 src/utils/echarts.ts）

/** 饼图单项数据 */
export interface PieDataItem {
  name: string // 缺陷类别名称
  value: number // 像素数
  color: string // 对应颜色
}

const props = defineProps<{
  data: PieDataItem[]
  title?: string // 可选标题
}>()

/**
 * 饼图（环形图）配置项。
 * 用于单张检测结果中展示各缺陷类别的像素分布占比。
 * 采用环形图（radius 设置了内外半径），比实心饼图更现代美观。
 */
const option = computed(() => ({
  // title：可选的图表标题，居中显示
  title: props.title
    ? {
        text: props.title,
        left: 'center',
        textStyle: { fontSize: 14, fontWeight: 600, color: '#111827' },
      }
    : undefined,
  // tooltip：悬浮显示缺陷名称、像素数、百分比
  tooltip: {
    trigger: 'item', // 单个数据项触发
    formatter: '{b}: {c} px ({d}%)', // {b}=名称, {c}=像素数, {d}=百分比
  },
  // legend：底部图例，显示各缺陷类别的颜色标识
  legend: {
    orient: 'horizontal' as const, // 水平排列
    bottom: 0,
    itemWidth: 10,
    itemHeight: 10,
    textStyle: { fontSize: 12, color: '#6b7280' },
  },
  series: [
    {
      type: 'pie',
      radius: ['45%', '70%'], // [内半径, 外半径]，形成环形图
      center: ['50%', '45%'], // 图表中心位置，略向上偏移给图例留空间
      avoidLabelOverlap: true, // 自动避免标签重叠
      // 扇区样式：圆角 + 白色间隔线，增加层次感
      itemStyle: {
        borderRadius: 4,
        borderColor: '#fff',
        borderWidth: 2,
      },
      // label：常驻标签，显示缺陷名 + 百分比
      label: {
        show: true,
        formatter: '{b}\n{d}%', // 换行显示名称和百分比
        color: '#6b7280',
        fontSize: 12,
        lineHeight: 16,
      },
      // labelLine：标签与扇区之间的牵引线
      labelLine: {
        lineStyle: { color: '#CBCCCF' },
        smooth: 0.2, // 轻微弯曲，更柔和
        length: 10, // 第一段长度（从扇区边缘开始）
        length2: 15, // 第二段长度（到文字）
      },
      // emphasis：鼠标悬浮时的高亮效果
      emphasis: {
        label: {
          show: true,
          fontSize: 13,
          fontWeight: 'bold',
          color: '#111827',
        },
      },
      // 每个扇区单独设置颜色
      data: props.data.map((d) => ({
        name: d.name,
        value: d.value,
        itemStyle: { color: d.color },
      })),
    },
  ],
}))
</script>

<template>
  <VChart :option="option" autoresize class="defect-pie-chart" />
</template>

<style scoped>
.defect-pie-chart {
  width: 100%;
  height: 280px;
}
</style>
