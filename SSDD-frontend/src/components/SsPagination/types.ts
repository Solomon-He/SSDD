/**
 * SsPagination 类型定义文件
 *
 * 定义了配置驱动分页组件的核心类型：
 * - SsPaginationConfig：useSsPagination 的初始化选项
 * - SsPaginationProps：SsPagination 组件接受的 Props 类型
 */

/**
 * useSsPagination Hook 的初始化选项
 *
 * @example
 * ```ts
 * const paginationConfig: SsPaginationConfig = {
 *   defaultPageSize: 20,
 *   pageSizes: [10, 20, 50],
 *   onChange: ({ current, pageSize }) => fetchData(),
 * }
 * ```
 */
export interface SsPaginationConfig {
  /** 默认每页条数，默认 10 */
  defaultPageSize?: number
  /** 可选的每页条数列表，默认 [10, 20, 50] */
  pageSizes?: number[]
  /** 翻页或切换条数时的回调，接收最新的 current 和 pageSize */
  onChange?: (params: { current: number; pageSize: number }) => void
}

/**
 * SsPagination 组件 Props 类型
 *
 * 该类型由 useSsPagination Hook 的 paginationProps 计算属性生成，
 * 使用方只需 v-bind="paginationProps" 即可将全部配置传入 SsPagination 组件。
 */
export interface SsPaginationProps {
  /** 当前页码 */
  current: number
  /** 每页条数 */
  pageSize: number
  /** 总条数 */
  total: number
  /** 可选的每页条数列表 */
  pageSizes: number[]
}
