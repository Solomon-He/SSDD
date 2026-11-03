import { resolveComponent, type Component } from 'vue'

/**
 * 解析组件引用：
 * - 字符串（如 'ElInput'）：通过 resolveComponent() 从全局注册中查找
 * - 组件对象（如导入的 SFC）：直接返回
 */
export function resolveComp(comp: string | Component): Component {
  if (typeof comp === 'string') {
    return resolveComponent(comp) as Component
  }
  return comp
}

/**
 * 过滤掉 isHide 为 true 的配置项，支持布尔值和函数两种形式。
 * 适用于 SsForm / SsSearch / SsTable 等配置驱动组件。
 */
export function filterVisible<T extends { isHide?: boolean | (() => boolean) }>(items: T[]): T[] {
  return items.filter((item) => {
    if (typeof item.isHide === 'function') return !item.isHide()
    return !item.isHide
  })
}

/**
 * 判断组件是否为「选择类」组件（需要 "请选择" 而非 "请输入" 的提示文案）。
 * 支持字符串组件名和导入的 SFC 组件对象。
 */
export function isSelectLikeComp(comp?: string | Component): boolean {
  if (!comp) return false
  if (typeof comp === 'string') {
    return ['ElSelect', 'el-select', 'ElDatePicker', 'el-date-picker'].includes(comp)
  }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const name = (comp as any).name || (comp as any).__name || ''
  return ['SsSelect', 'SsDatePicker'].includes(name)
}

/**
 * Ss 组件配置共享的基础字段接口。
 * SsFormItemConfig 和 SsSearchItemConfig 均扩展自此类型。
 */
export interface SsBaseFieldConfig {
  /** 字段名，同时作为 model 的 key */
  prop: string
  /** 标签文字 */
  label: string
  /**
   * 渲染的组件：
   * - 字符串：全局注册的组件名（如 'ElInput'）
   * - Component 对象：直接导入的 SFC 组件
   * - 不传时默认使用 'ElInput'
   */
  comp?: string | Component
  /** 传递给 comp 组件的 props，通过 v-bind 展开 */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  compProps?: Record<string, any>
  /** 是否隐藏：布尔值或返回布尔值的函数（支持动态联动） */
  isHide?: boolean | (() => boolean)
}
