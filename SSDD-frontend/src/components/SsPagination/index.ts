/**
 * SsPagination 模块 — 桶导出文件（Barrel Export）
 *
 * 统一导出 SsPagination 相关的组件、Hook 和类型，
 * 使用方只需 import { SsPagination, useSsPagination } from '@/components/SsPagination'
 */
export { default as SsPagination } from './index.vue'
export { useSsPagination } from './useSsPagination'
export type { SsPaginationConfig, SsPaginationProps } from './types'
