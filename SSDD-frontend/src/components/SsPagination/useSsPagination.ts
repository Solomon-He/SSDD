import { ref, computed } from 'vue'
import type { SsPaginationConfig } from './types'

/**
 * useSsPagination — 配置驱动分页的核心 Composable（Hook）
 *
 * 核心职责：
 * 1. 维护 current / pageSize / total 三个响应式状态
 * 2. 翻页或切换条数时自动调用 onChange 回调
 * 3. 生成可直接 v-bind 到 SsPagination 组件的 paginationProps
 * 4. 暴露 reset 方法重置为第一页
 *
 * @param config 分页配置选项
 *
 * @returns current         — 当前页码
 * @returns pageSize        — 每页条数
 * @returns total           — 总条数（由外部赋值）
 * @returns paginationProps — 计算属性，直接 v-bind 到 SsPagination 组件
 * @returns reset           — 重置到第一页
 * @returns loading         — 加载状态（由外部控制）
 *
 * @example
 * ```ts
 * const { current, pageSize, total, loading, paginationProps, reset } = useSsPagination({
 *   defaultPageSize: 20,
 *   pageSizes: [10, 20, 50],
 *   onChange: ({ current, pageSize }) => fetchUsers(),
 * })
 * ```
 */
export function useSsPagination(config: SsPaginationConfig = {}) {
  const { defaultPageSize = 10, pageSizes = [10, 20, 50], onChange } = config

  const current = ref(1)
  const pageSize = ref(defaultPageSize)
  const total = ref(0)
  const loading = ref(false)

  /** 组装 SsPagination 组件的 Props + 事件回调，使用方只需 v-bind="paginationProps" 一行搞定 */
  const paginationProps = computed(() => ({
    current: current.value,
    pageSize: pageSize.value,
    total: total.value,
    pageSizes,
    'onUpdate:current': handleCurrentChange,
    'onUpdate:pageSize': handleSizeChange,
  }))

  /** 翻页时由组件触发 */
  function handleCurrentChange(newPage: number) {
    current.value = newPage
    onChange?.({ current: newPage, pageSize: pageSize.value })
  }

  /** 切换每页条数时由组件触发 */
  function handleSizeChange(newSize: number) {
    pageSize.value = newSize
    current.value = 1
    onChange?.({ current: 1, pageSize: newSize })
  }

  /** 重置到第一页 */
  function reset() {
    current.value = 1
  }

  return {
    /** 当前页码 */
    current,
    /** 每页条数 */
    pageSize,
    /** 总条数，由外部赋值 */
    total,
    /** 加载状态，由外部控制 */
    loading,
    /** 计算属性，直接 v-bind 到 SsPagination 组件 */
    paginationProps,
    /** 翻页回调（由 SsPagination 组件内部调用） */
    handleCurrentChange,
    /** 切换条数回调（由 SsPagination 组件内部调用） */
    handleSizeChange,
    /** 重置到第一页 */
    reset,
  }
}
