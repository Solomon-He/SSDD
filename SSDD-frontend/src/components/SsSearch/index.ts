/**
 * SsSearch 模块 — 桶导出文件（Barrel Export）
 *
 * 统一导出 SsSearch 相关的组件、Hook 和类型，
 * 使用方只需 import { SsSearch, useSsSearch, SsSearchItemConfig } from '@/components/SsSearch'
 */
export { default as SsSearch } from './index.vue'
export { useSsSearch } from './useSsSearch'
export type { SsSearchItemConfig, SsSearchProps } from './types'
