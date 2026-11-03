<!--
  SsTable — 配置驱动的表格组件

  核心思路：通过列配置数组（SsTableColumnConfig[]）自动渲染表格列，
  配合 useSsTable Hook 实现「配置 → 数据 → UI」的单向驱动。

  功能特性：
  1. 配置驱动：遍历 columns 数组自动生成 ElTableColumn
  2. 特殊列：showSelection 显示复选列，showSerialNum 显示序号列
  3. 自定义渲染：通过 formatter（TSX 函数）自定义列内容
  4. 插槽覆盖：当 formatter 无法满足需求时，可通过 #prop名 具名插槽完全自定义
  5. 动态显隐：isHide 支持布尔值和函数，实现列的条件渲染
  6. 原生透传：tableAttrs 透传 ElTable 全部原生属性

  数据流：
  父组件 → v-model（data）→ SsTable → ElTable :data
  父组件 → v-model:table-instance → SsTable → 内部 ElTable ref 回传给父组件
-->
<script setup lang="ts">
import { computed, defineComponent } from 'vue'
import type { SsTableProps } from './types'
import type { TableInstance } from 'element-plus'
import { filterVisible } from '@/utils/component'

defineOptions({ name: 'SsTable' })

const props = withDefaults(defineProps<SsTableProps>(), {
  showSelection: false,
  showSerialNum: false,
})

/**
 * 表格数据的双向绑定。
 * 父组件通过 v-model 传入数据数组，SsTable 将其绑定到 ElTable 的 :data。
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const data = defineModel<Record<string, any>[]>({ default: () => [] })

/**
 * ElTable 实例的双向传递。
 * 通过 defineModel('tableInstance') 回传给父组件，
 * 使得父组件可以调用 ElTable 原生方法（如 clearSelection、sort 等）。
 */
const tableInstance = defineModel<TableInstance | undefined>('tableInstance')

/**
 * 过滤掉 isHide 为 true 的列，只保留需要渲染的列。
 */
const visibleColumns = computed(() => filterVisible(props.columns))

/**
 * VNode 渲染器：复用同一个组件定义渲染 formatter 返回的 VNode，
 * 避免每次渲染都创建匿名函数式组件导致 DOM 销毁重建。
 */
const VNodeRenderer = defineComponent({
  name: 'VNodeRenderer',
  props: { content: null },
  setup(props) {
    return () => props.content
  },
})

defineEmits<{
  /** 多选变化时触发 */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  'selection-change': [selection: Record<string, any>[]]
  /** 排序变化时触发 */
  'sort-change': [params: { column: unknown; prop: string; order: string | null }]
}>()
</script>

<template>
  <ElTable
    ref="tableInstance"
    :data="data"
    v-bind="tableAttrs"
    class="ss-table"
    @selection-change="$emit('selection-change', $event)"
    @sort-change="$emit('sort-change', $event)"
  >
    <!-- 多选列 -->
    <ElTableColumn
      v-if="showSelection"
      type="selection"
      width="50"
      align="center"
      fixed="left"
    />

    <!-- 序号列 -->
    <ElTableColumn
      v-if="showSerialNum"
      type="index"
      label="#"
      width="60"
      align="center"
      fixed="left"
    />

    <!-- 数据列：根据 columns 配置自动渲染 -->
    <template v-for="col in visibleColumns" :key="col.prop">
      <ElTableColumn
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :fixed="col.fixed"
        :align="col.align"
        :sortable="col.sortable"
      >
        <template #default="{ row, $index }">
          <!--
            具名插槽机制（核心扩展点）：
            插槽名 = col.prop（字段名），父组件可通过 #字段名 自定义该列的渲染。
            插槽参数: row（当前行数据）和 index（行索引）。

            若父组件未提供对应插槽:
            - 有 formatter → 调用 formatter(row, $index) 渲染
            - 无 formatter → 直接显示 row[col.prop] 原始值
          -->
          <slot :name="col.prop" :row="row" :index="$index">
            <VNodeRenderer
              v-if="col.formatter"
              :content="col.formatter(row, $index)"
            />
            <span v-else>{{ row[col.prop] }}</span>
          </slot>
        </template>
      </ElTableColumn>
    </template>

    <!-- 追加列插槽：用于添加操作列等 columns 配置之外的额外列 -->
    <slot name="append" />
  </ElTable>
</template>

<style lang="scss">
.ss-table {
  width: 100%;

  /* ---- 整体圆角与边框 ---- */
  --el-table-border-color: var(--border-color);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  overflow: hidden;

  /* ---- 表头 ---- */
  .el-table__header-wrapper {
    th.el-table__cell {
      background-color: var(--el-fill-color-light);
      font-weight: 600;
      font-size: 13px;
      color: var(--text-regular);
      padding: 10px 0;

      .cell {
        line-height: 1.4;
      }
    }
  }

  /* ---- 行 ---- */
  .el-table__body-wrapper {
    td.el-table__cell {
      padding: 12px 0;
      font-size: 14px;
      color: var(--text-primary);
      transition: background-color 0.15s ease;

      .cell {
        line-height: 1.5;
        /* 数字等宽对齐，表格内数据更整齐 */
        font-variant-numeric: tabular-nums;
      }
    }

    /* 条纹行 */
    .el-table__row--striped td.el-table__cell {
      background-color: var(--el-fill-color-lighter);
    }

    /* hover 高亮 */
    tr:hover > td.el-table__cell {
      background-color: var(--el-fill-color);
    }

    /* 去除最后一行的底部边框，避免与外层 border 叠加出现双线 */
    tr:last-child > td.el-table__cell {
      border-bottom: none;
    }
  }

  /* ---- 固定列阴影优化 ---- */
  .el-table-fixed-column--left.is-last-column::before,
  .el-table-fixed-column--right.is-first-column::before {
    box-shadow: none;
  }

  .el-table-fixed-column--left.is-last-column.el-table__cell {
    border-right: 1px solid var(--border-color);
  }

  .el-table-fixed-column--right.is-first-column.el-table__cell {
    border-left: 1px solid var(--border-color);
  }

  /* ---- 空状态 ---- */
  .el-table__empty-block {
    min-height: 200px;

    .el-table__empty-text {
      font-size: 13px;
      color: var(--text-secondary);
    }
  }

  /* ---- 选择列复选框居中 ---- */
  .el-table-column--selection .cell {
    padding-left: 14px;
    padding-right: 14px;
  }

  /* ---- 序号列 ---- */
  .el-table__body td.el-table-column--index .cell {
    color: var(--text-secondary);
    font-variant-numeric: tabular-nums;
  }
}
</style>
