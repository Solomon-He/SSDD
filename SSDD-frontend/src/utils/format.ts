import dayjs from 'dayjs'

/**
 * 格式化日期时间
 * @param dateStr ISO 日期字符串
 * @param format 格式模板，默认 'YYYY-MM-DD HH:mm:ss'
 */
export function formatDateTime(dateStr: string, format = 'YYYY-MM-DD HH:mm:ss'): string {
  return dayjs(dateStr).format(format)
}

/**
 * 格式化日期（不含时间）
 */
export function formatDate(dateStr: string): string {
  return dayjs(dateStr).format('YYYY-MM-DD')
}

/**
 * 格式化数值，保留指定小数位
 */
export function formatNumber(value: number, decimals = 2): string {
  return value.toFixed(decimals)
}

/**
 * 格式化百分比
 * @param ratio 0~1 之间的小数
 */
export function formatPercent(ratio: number, decimals = 2): string {
  return `${(ratio * 100).toFixed(decimals)}%`
}

/**
 * 格式化推理耗时
 */
export function formatInferenceTime(ms: number): string {
  return `${ms.toFixed(1)} ms`
}
