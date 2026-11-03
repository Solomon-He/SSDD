<!--
  SsDatePicker — 日期选择器二次封装组件

  基于 ElDatePicker 进行封装，统一全局日期选择器的视觉风格（边框、圆角、聚焦效果）。
  采用与 SsInput 一致的 GitHub 风格 box-shadow 边框方案。

  设计要点：
  1. inheritAttrs: false + v-bind="$attrs"：
     将所有 ElDatePicker 原生属性（v-model、type、format、value-format 等）透传，
     保证 ElDatePicker 全部 props 和事件均可正常使用。
  2. 兼容单日期（date）、日期范围（daterange）等多种 type 模式。
  3. 使用非 scoped 样式 + .ss-date-picker 类名隔离。
-->
<script setup lang="ts">
defineOptions({
  name: 'SsDatePicker',
  /** 关闭自动 attrs 继承，由 v-bind="$attrs" 手动透传给 ElDatePicker */
  inheritAttrs: false,
})
</script>

<template>
  <ElDatePicker v-bind="$attrs" class="ss-date-picker" />
</template>

<!--
  非 scoped 全局样式，通过 .ss-date-picker 类名限定作用域。
  与 SsInput 保持完全一致的 box-shadow 边框 / hover / focus 视觉语言。
-->
<style lang="scss">
@use '@/assets/styles/mixins' as *;

.ss-date-picker {
  /* 单日期模式 */
  .el-input__wrapper {
    @include ss-field;
    padding: 2px 12px;

    &.is-focus {
      @include ss-field-focus;
    }
  }

  /* daterange / datetimerange / monthrange 模式 */
  &.el-date-editor--daterange,
  &.el-date-editor--datetimerange,
  &.el-date-editor--monthrange {
    @include ss-field;

    &.is-active {
      @include ss-field-focus;
    }
  }
}
</style>
