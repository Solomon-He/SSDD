/**
 * SsSearch 类型定义文件
 *
 * 定义了配置驱动搜索栏的核心类型：
 * - SsSearchItemConfig：单个搜索字段的配置描述（复用 SsForm 的配置格式）
 * - SsSearchProps：SsSearch 组件接受的 Props 类型
 */
import type { SsBaseFieldConfig } from '@/utils/component'

/**
 * 单个搜索字段配置
 *
 * 扩展自 SsBaseFieldConfig，增加了默认值字段。
 *
 * @example
 * ```ts
 * const config: SsSearchItemConfig[] = [
 *   { prop: 'keyword', label: '关键词', comp: SsInput, compProps: { placeholder: '请输入' } },
 *   { prop: 'status', label: '状态', comp: 'ElSelect', compProps: { placeholder: '全部' } },
 * ]
 * ```
 */
export interface SsSearchItemConfig extends SsBaseFieldConfig {
  /**
   * 默认值，未提供时默认为 null。
   * 重置操作会将字段恢复到此值。
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  defaultValue?: any
}

/**
 * SsSearch 组件 Props 类型
 *
 * 该类型由 useSsSearch Hook 的 searchProps 计算属性生成，
 * 使用方只需 v-bind="searchProps" 即可将全部配置传入 SsSearch 组件。
 */
export interface SsSearchProps {
  /** 搜索字段配置数组 */
  config: SsSearchItemConfig[]
  /** label 位置，默认 'top' */
  labelPosition?: 'top' | 'left'
}
