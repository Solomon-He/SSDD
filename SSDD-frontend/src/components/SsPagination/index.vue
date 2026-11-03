<!--
  SsPagination — 配置驱动的分页组件

  基于 ElPagination 的二次封装，统一全局分页的默认行为与布局：
  - 固定 layout: total, sizes, prev, pager, next, jumper
  - 默认右对齐
  - 通过 v-bind="paginationProps" 一行绑定全部配置

  数据流：
  useSsPagination → paginationProps → v-bind → SsPagination
  SsPagination → emit('update:current' / 'update:page-size') → useSsPagination 的 handler
-->
<script setup lang="ts">
import type { SsPaginationProps } from './types'

defineOptions({ name: 'SsPagination' })

withDefaults(defineProps<SsPaginationProps>(), {
  pageSizes: () => [10, 20, 50],
})

defineEmits<{
  'update:current': [value: number]
  'update:pageSize': [value: number]
}>()
</script>

<template>
  <div v-if="total > 0" class="ss-pagination">
    <ElPagination
      :current-page="current"
      :page-size="pageSize"
      :total="total"
      :page-sizes="pageSizes"
      layout="total, sizes, prev, pager, next, jumper"
      background
      @update:current-page="$emit('update:current', $event)"
      @update:page-size="$emit('update:pageSize', $event)"
    />
  </div>
</template>

<style lang="scss">
.ss-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;

  .el-pagination {
    /* 整体字体 */
    font-weight: 500;

    /* ---- 总条数文字 ---- */
    .el-pagination__total {
      font-size: 13px;
      color: var(--text-secondary);
      font-weight: 400;
    }

    /* ---- 页码按钮通用 ---- */
    .btn-prev,
    .btn-next,
    .el-pager li {
      border-radius: var(--border-radius);
      font-size: 13px;
      color: var(--text-regular);
      transition: all 0.15s ease;
      min-width: 32px;
      height: 32px;
      line-height: 32px;

      &:hover:not(.is-disabled) {
        color: var(--color-primary);
      }
    }

    /* 激活页码 */
    .el-pager li.is-active {
      font-weight: 600;
    }

    /* ---- "每页N条" 下拉框 ---- */
    .el-pagination__sizes {
      .el-select {
        .el-select__wrapper {
          border-radius: var(--border-radius);
          height: 32px;
          font-size: 13px;
          box-shadow: 0 0 0 1px var(--border-color) inset;
          transition: box-shadow 0.15s ease;

          &:hover {
            box-shadow: 0 0 0 1px var(--text-secondary) inset;
          }

          &.is-focused {
            box-shadow: 0 0 0 1px var(--color-primary) inset,
              0 0 0 3px rgba(64, 158, 255, 0.2);
          }
        }
      }
    }

    /* ---- "前往N页" 输入框 ---- */
    .el-pagination__jump {
      font-size: 13px;
      color: var(--text-secondary);
      font-weight: 400;

      .el-input {
        .el-input__wrapper {
          border-radius: var(--border-radius);
          height: 32px;
          box-shadow: 0 0 0 1px var(--border-color) inset;
          transition: box-shadow 0.15s ease;

          &:hover {
            box-shadow: 0 0 0 1px var(--text-secondary) inset;
          }

          &.is-focus {
            box-shadow: 0 0 0 1px var(--color-primary) inset,
              0 0 0 3px rgba(64, 158, 255, 0.2);
          }
        }
      }
    }
  }
}
</style>
