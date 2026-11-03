import { ref, computed, toValue, type MaybeRef } from 'vue'
import type { TableInstance } from 'element-plus'
import type { SsTableColumnConfig, SsTableProps } from './types'

/* eslint-disable @typescript-eslint/no-explicit-any */
type AnyRecord = Record<string, any>
/* eslint-enable @typescript-eslint/no-explicit-any */

/**
 * useSsTable — 配置驱动表格的核心 Composable（Hook）
 *
 * 核心职责：
 * 1. 根据传入的列配置和选项，生成可直接 v-bind 到 SsTable 组件的 tableConfig
 * 2. 提供 tableInstance（ElTable 实例引用），便于调用原生方法
 * 3. 暴露 clearSelection / toggleRowSelection 等快捷方法
 *
 * @param columns  列配置数组，描述每列的渲染方式
 * @param options  额外选项
 * @param options.showSelection  是否显示多选列，默认 false
 * @param options.showSerialNum  是否显示序号列，默认 false
 * @param options.tableAttrs     透传给 ElTable 的原生属性（如 stripe、border、height）
 *
 * @returns tableConfig    — 计算属性，直接 v-bind 到 SsTable 组件
 * @returns tableInstance  — ElTable 实例引用
 * @returns clearSelection — 清空所有选中行
 * @returns toggleRowSelection — 切换某行的选中状态
 * @returns getSelectionRows — 获取当前选中行
 *
 * @example
 * ```ts
 * const { tableConfig, tableInstance, clearSelection } = useSsTable(columns, {
 *   showSelection: true,
 *   tableAttrs: { stripe: true, border: true },
 * })
 * ```
 */
export function useSsTable(
  columns: MaybeRef<SsTableColumnConfig[]>,
  options: {
    showSelection?: boolean
    showSerialNum?: boolean
    tableAttrs?: AnyRecord
  } = {},
) {
  const { showSelection = false, showSerialNum = false, tableAttrs = {} } = options

  /** ElTable 组件实例引用 */
  const tableInstance = ref<TableInstance>()

  /** 组装 SsTable 组件的 Props */
  const tableConfig = computed<SsTableProps>(() => ({
    columns: toValue(columns),
    showSelection,
    showSerialNum,
    tableAttrs,
  }))

  /** 清空所有选中行 */
  function clearSelection() {
    tableInstance.value?.clearSelection()
  }

  /** 切换某行的选中状态 */
  function toggleRowSelection(row: AnyRecord, selected?: boolean) {
    tableInstance.value?.toggleRowSelection(row, selected)
  }

  /** 获取当前选中行 */
  function getSelectionRows(): AnyRecord[] {
    return tableInstance.value?.getSelectionRows() ?? []
  }

  return {
    /** 计算属性，直接 v-bind 到 SsTable 组件 */
    tableConfig,
    /** ElTable 实例引用，传入 SsTable 的 v-model:table-instance */
    tableInstance,
    /** 清空所有选中行 */
    clearSelection,
    /** 切换行选中状态 */
    toggleRowSelection,
    /** 获取当前选中行 */
    getSelectionRows,
  }
}
