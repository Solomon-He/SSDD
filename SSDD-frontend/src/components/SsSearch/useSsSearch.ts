import { reactive, computed, toValue, type MaybeRef } from 'vue'
import type { SsSearchItemConfig, SsSearchProps } from './types'

/* eslint-disable @typescript-eslint/no-explicit-any */
type AnyRecord = Record<string, any>
/* eslint-enable @typescript-eslint/no-explicit-any */

/**
 * useSsSearch — 配置驱动搜索栏的核心 Composable
 *
 * 核心职责：
 * 1. 根据 config 数组自动构建响应式的 searchModel
 * 2. 生成可直接 v-bind 到 SsSearch 组件的 searchProps 对象
 * 3. 暴露 getSearchParams（获取当前非空搜索参数）和 resetFields（重置搜索表单）
 *
 * @param config  搜索字段配置数组，支持普通数组或 Ref/ComputedRef
 * @param options 额外选项
 * @param options.labelPosition label 位置，默认 'top'
 * @param options.onSearch  点击"查询"或回车时的回调
 * @param options.onReset   点击"重置"时的回调
 *
 * @example
 * ```ts
 * const { searchProps, searchModel, getSearchParams } = useSsSearch(config, {
 *   onSearch: (params) => fetchList(params),
 *   onReset: () => fetchList(),
 * })
 * ```
 */
export function useSsSearch<T extends AnyRecord = AnyRecord>(
  config: MaybeRef<SsSearchItemConfig[]>,
  options: {
    labelPosition?: 'top' | 'left'
    onSearch?: (params: Partial<T>) => void
    onReset?: () => void
  } = {},
) {
  const { labelPosition = 'top', onSearch, onReset } = options

  // ---- 1. 构建初始表单数据 ----
  const initialValues: AnyRecord = {}
  for (const item of toValue(config)) {
    initialValues[item.prop] = item.defaultValue ?? null
  }

  const searchModel = reactive<T>({ ...initialValues } as T)

  // ---- 2. 组装 SsSearch 组件的 Props ----
  const searchProps = computed<SsSearchProps>(() => ({
    config: toValue(config),
    labelPosition,
  }))

  // ---- 3. 获取当前搜索参数（过滤掉空值） ----
  function getSearchParams(): Partial<T> {
    const params: AnyRecord = {}
    for (const item of toValue(config)) {
      const val = (searchModel as AnyRecord)[item.prop]
      if (val !== null && val !== undefined && val !== '') {
        params[item.prop] = val
      }
    }
    return params as Partial<T>
  }

  // ---- 4. 重置搜索表单 ----
  function resetFields() {
    for (const item of toValue(config)) {
      ;(searchModel as AnyRecord)[item.prop] = item.defaultValue ?? null
    }
  }

  // ---- 5. 搜索 & 重置回调 ----
  function handleSearch() {
    onSearch?.(getSearchParams())
  }

  function handleReset() {
    resetFields()
    onReset?.()
  }

  return {
    /** 响应式搜索数据，用于 v-model 双向绑定 */
    searchModel,
    /** 计算属性，直接 v-bind 到 SsSearch 组件 */
    searchProps,
    /** 获取当前非空的搜索参数 */
    getSearchParams,
    /** 重置搜索表单到初始值 */
    resetFields,
    /** 触发查询回调 */
    handleSearch,
    /** 重置表单并触发重置回调 */
    handleReset,
  }
}
