import { DEFECT_CLASSES } from '@/constants/defects'
import type { DetectRecord } from '@/types/detect'
import dayjs from 'dayjs'

/** 缺陷类型饼图配置 */
export function createDefectPieOption(records: DetectRecord[]) {
  const counts: Record<string, number> = {}
  DEFECT_CLASSES.forEach((c) => {
    counts[String(c.index)] = 0
  })
  records.forEach((r) => {
    Object.entries(r.defect_class_counts).forEach(([k, v]) => {
      counts[k] = (counts[k] || 0) + v
    })
  })
  const data = DEFECT_CLASSES.map((c) => ({
    name: c.label,
    value: counts[String(c.index)] || 0,
    itemStyle: { color: c.color },
  })).filter((d) => d.value > 0)

  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 处 ({d}%)' },
    legend: {
      orient: 'horizontal' as const,
      bottom: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { fontSize: 12, color: '#6b7280' },
    },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: {
          show: true,
          formatter: '{b}\n{d}%',
          color: '#6b7280',
          fontSize: 12,
          lineHeight: 16,
        },
        labelLine: {
          lineStyle: { color: '#d1d5db' },
          smooth: 0.2,
          length: 10,
          length2: 15,
        },
        emphasis: {
          label: { fontSize: 13, fontWeight: 'bold' as const, color: '#111827' },
        },
        data,
      },
    ],
  }
}

/** 检测趋势折线图配置 */
export function createTrendOption(records: DetectRecord[]) {
  const map = new Map<string, number>()
  // ISO 字符串可直接用 localeCompare 排序，避免反复 new Date()
  const sorted = [...records].sort((a, b) => a.created_at.localeCompare(b.created_at))
  sorted.forEach((r) => {
    const d = dayjs(r.created_at).format('MM-DD')
    map.set(d, (map.get(d) || 0) + 1)
  })
  const dates = Array.from(map.keys())
  const values = Array.from(map.values())

  return {
    tooltip: { trigger: 'axis', formatter: '{b}: {c} 次检测' },
    grid: { left: 12, right: 36, top: 24, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category' as const,
      data: dates,
      axisLabel: { fontSize: 12, color: '#6b7280' },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisTick: { show: false },
      boundaryGap: false,
    },
    yAxis: {
      type: 'value' as const,
      minInterval: 1,
      axisLabel: { fontSize: 12, color: '#6b7280' },
      splitLine: { lineStyle: { color: '#f3f4f6', type: 'dashed' as const } },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#409eff', width: 2.5 },
        itemStyle: { color: '#409eff', borderColor: '#fff', borderWidth: 2 },
        areaStyle: {
          color: {
            type: 'linear' as const,
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(64,158,255,0.2)' },
              { offset: 1, color: 'rgba(64,158,255,0.01)' },
            ],
          },
        },
        data: values,
      },
    ],
  }
}
