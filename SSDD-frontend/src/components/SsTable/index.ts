/**
 * SsTable 模块 — 桶导出文件（Barrel Export）
 *
 * 统一导出 SsTable 相关的组件、Hook 和类型，
 * 使用方只需 import { SsTable, useSsTable, SsTableColumnConfig } from '@/components/SsTable'
 */
export { default as SsTable } from './index.vue'
export { useSsTable } from './useSsTable'
export type { SsTableColumnConfig, SsTableProps } from './types'
