/** 缺陷类别定义 */
export interface DefectClass {
  index: number
  key: string
  label: string
  color: string
}

/** 缺陷类别列表（不含背景）— 颜色与 variables.scss 中 --defect-* 变量保持一致 */
export const DEFECT_CLASSES: DefectClass[] = [
  { index: 1, key: 'Pit', label: '凹坑', color: '#ef4444' },
  { index: 2, key: 'EdgeCrack', label: '边缘裂纹', color: '#10b981' },
  { index: 3, key: 'Scratch', label: '划痕刮伤', color: '#3b82f6' },
  { index: 4, key: 'IronOxideScale', label: '氧化铁皮', color: '#f59e0b' },
]

/** 根据索引获取缺陷类别 */
export function getDefectClass(index: number): DefectClass | undefined {
  return DEFECT_CLASSES.find((d) => d.index === index)
}

/** 根据索引获取缺陷名称 */
export function getDefectLabel(index: number): string {
  return getDefectClass(index)?.label ?? '未知'
}

/** 根据索引获取缺陷颜色 */
export function getDefectColor(index: number): string {
  return getDefectClass(index)?.color ?? '#999999'
}
