<!--
  SsSelect — 下拉选择框二次封装组件

  基于 ElSelect 进行封装，统一全局下拉选择框的视觉风格（边框、圆角、聚焦效果）。
  采用与 SsInput 一致的 GitHub 风格 box-shadow 边框方案。

  设计要点：
  1. inheritAttrs: false + v-bind="$attrs"：
     将所有 ElSelect 原生属性（v-model、placeholder、clearable、filterable 等）透传，
     保证 ElSelect 全部 props 和事件均可正常使用。
  2. 默认插槽透传：父组件可在 SsSelect 内部写 ElOption，通过默认插槽传递给 ElSelect。
  3. 使用非 scoped 样式 + .ss-select 类名隔离，确保样式能穿透 ElSelect 内部结构。
-->
<script setup lang="ts">
defineOptions({
  name: 'SsSelect',
  /** 关闭自动 attrs 继承，由 v-bind="$attrs" 手动透传给 ElSelect */
  inheritAttrs: false,
})
</script>

<template>
  <ElSelect v-bind="$attrs" class="ss-select">
    <!-- 透传默认插槽，允许父组件在内部写 ElOption -->
    <slot />
  </ElSelect>
</template>

<!--
  非 scoped 全局样式，通过 .ss-select 类名限定作用域。
  与 SsInput 保持完全一致的 box-shadow 边框 / hover / focus 视觉语言。
-->
<style lang="scss">
@use '@/assets/styles/mixins' as *;

.ss-select {
  .el-select__wrapper {
    @include ss-field;
    padding: 2px 12px;

    &.is-focused {
      @include ss-field-focus;
    }
  }
}
</style>
