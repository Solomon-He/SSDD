<!--
  SsSearch — 配置驱动的搜索栏组件

  核心思路：通过一个配置数组（SsSearchItemConfig[]）自动渲染搜索字段，
  配合 useSsSearch Hook 实现「配置 → 数据 → UI」的单向驱动。

  功能特性：
  1. 配置驱动：遍历 config 数组自动生成搜索字段
  2. Grid 布局：一行多列横向排列，末尾自动附带"查询"和"重置"按钮
  3. 动态组件：使用 Vue 的 <component :is> + resolveComponent() 支持字符串和组件对象
  4. 回车触发查询：监听 keyup.enter 事件
  5. 动态显隐：isHide 支持布尔值和函数

  数据流：
  父组件 → v-model（searchModel）→ SsSearch → v-model 绑定到各子组件
-->
<script setup lang="ts">
import { computed, type Component } from 'vue'
import type { SsSearchProps } from './types'
import { resolveComp, filterVisible } from '@/utils/component'

defineOptions({ name: 'SsSearch' })

const props = withDefaults(defineProps<SsSearchProps>(), {
  labelPosition: 'top',
})

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const model = defineModel<Record<string, any>>({ required: true })

const emit = defineEmits<{
  search: []
  reset: []
}>()

/** 过滤掉 isHide 为 true 的配置项 */
const visibleConfig = computed(() => filterVisible(props.config))

/** 解析组件引用 */
function resolve(comp: string | Component): Component {
  return resolveComp(comp)
}

function onSearch() {
  emit('search')
}

function onReset() {
  emit('reset')
}
</script>

<template>
  <div class="ss-search" @keyup.enter="onSearch">
    <div class="ss-search__fields">
      <div
        v-for="item in visibleConfig"
        :key="item.prop"
        class="ss-search__item"
      >
        <label v-if="labelPosition === 'top'" class="ss-search__label">
          {{ item.label }}
        </label>
        <component
          :is="resolve(item.comp ?? 'ElInput')"
          v-model="model[item.prop]"
          v-bind="item.compProps"
        />
      </div>

      <!-- 操作按钮 -->
      <div class="ss-search__actions">
        <ElButton type="primary" @click="onSearch">
          <template #icon><ElIcon><Search /></ElIcon></template>
          查询
        </ElButton>
        <ElButton @click="onReset">
          <template #icon><ElIcon><Refresh /></ElIcon></template>
          重置
        </ElButton>
      </div>
    </div>
  </div>
</template>

<style lang="scss">
.ss-search {
  &__fields {
    display: flex;
    align-items: flex-end;
    gap: 12px;
    flex-wrap: wrap;
  }

  &__item {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__label {
    font-size: 13px;
    font-weight: 500;
    color: var(--text-secondary);
    line-height: 1.4;
    white-space: nowrap;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    /* 当 labelPosition='top' 时，按钮需要与输入框底部对齐 */
    padding-bottom: 1px;
  }
}
</style>
