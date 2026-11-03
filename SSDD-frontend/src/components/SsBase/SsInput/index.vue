<!--
  SsInput — 输入框二次封装组件

  基于 ElInput 进行封装，统一全局输入框的视觉风格（边框、圆角、聚焦效果）。
  采用 GitHub 风格的 box-shadow 边框方案代替传统 border，实现更细腻的交互反馈。

  设计要点：
  1. inheritAttrs: false + v-bind="$attrs"：
     与 SsDialog 同理，将所有 ElInput 原生属性（v-model、type、placeholder 等）透传，
     保证 ElInput 全部 props 和事件均可正常使用。
  2. 同时兼容 input（单行）和 textarea（多行）两种模式的样式覆盖。
  3. 使用非 scoped 样式 + .ss-input 类名隔离，确保样式能穿透 ElInput 内部 Shadow DOM。
-->
<script setup lang="ts">
defineOptions({
  name: 'SsInput',
  /** 关闭自动 attrs 继承，由 v-bind="$attrs" 手动透传给 ElInput */
  inheritAttrs: false,
})
</script>

<template>
  <!-- 将父组件传入的全部属性（v-model、type、placeholder 等）透传给 ElInput -->
  <ElInput v-bind="$attrs" class="ss-input" />
</template>

<!--
  非 scoped 全局样式，通过 .ss-input 类名限定作用域。
  使用 box-shadow 模拟边框是 GitHub 的设计手法：
  - 相比 border，box-shadow 不占据布局空间，不会导致元素尺寸变化
  - 可以叠加多层阴影实现「外发光 + 内描边」的聚焦效果
-->
<style lang="scss">
@use '@/assets/styles/mixins' as *;

.ss-input {
  /* ---- 单行输入框 ---- */
  .el-input__wrapper {
    @include ss-field;
    padding: 2px 12px;

    &.is-focus {
      @include ss-field-focus;
    }
  }

  /* ---- 多行文本框（textarea）---- */
  .el-textarea__inner {
    @include ss-field;

    &:focus {
      @include ss-field-focus;
    }
  }
}
</style>
