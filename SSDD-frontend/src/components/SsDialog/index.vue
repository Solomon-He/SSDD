<!--
  SsDialog — 基础对话框二次封装组件

  基于 ElDialog 进行封装，统一全局对话框的默认行为和视觉风格：
  - 默认禁止点击遮罩关闭（close-on-click-modal = false）
  - 默认关闭后销毁内部 DOM（destroy-on-close）
  - 内建「取消 + 确认」双按钮 footer，支持 loading 状态
  - 通过 showFooter 控制是否展示底部，适配纯展示型弹窗

  设计要点：
  1. inheritAttrs: false + v-bind="$attrs"：
     将父组件传入的所有非 props 属性（如 v-model、title、width、align-center 等）
     透传给 ElDialog，保证 ElDialog 原生 API 100% 可用，不丢失任何功能。
  2. 双层 footer 插槽降级：
     - 若使用方不提供 #footer 插槽 → 渲染默认的「取消 + 确认」按钮
     - 若使用方提供 #footer 插槽 → 完全自定义底部内容
  3. 非 scoped 样式：
     因为 ElDialog 默认通过 Teleport 挂载到 body 上，scoped 样式无法穿透，
     所以此处使用全局样式并以 .ss-dialog 类名隔离作用域。
-->
<script setup lang="ts">
defineOptions({
  name: 'SsDialog',
  /**
   * 关闭 Vue 默认的 attrs 自动继承行为。
   * 这样 $attrs（如 v-model、title、width 等 ElDialog 原生属性）
   * 不会被自动绑定到组件根元素，而是由我们手动通过 v-bind="$attrs" 精确透传给 ElDialog。
   */
  inheritAttrs: false,
})

withDefaults(
  defineProps<{
    /** 确认按钮文字 */
    confirmText?: string
    /** 取消按钮文字 */
    cancelText?: string
    /** 确认按钮 loading 状态 */
    loading?: boolean
    /** 是否显示 footer 区域（设为 false 可完全隐藏底部，适用于纯展示型弹窗如图表可视化） */
    showFooter?: boolean
  }>(),
  {
    confirmText: '确认',
    cancelText: '取消',
    loading: false,
    showFooter: true,
  },
)

const emit = defineEmits<{
  /** 用户点击确认按钮时触发 */
  confirm: []
  /** 用户点击取消按钮时触发 */
  cancel: []
}>()
</script>

<template>
  <!--
    v-bind="$attrs" 将父组件传入的所有属性（v-model、title、width 等）透传给 ElDialog。
    同时注入两个统一默认值：禁止点击遮罩关闭 + 关闭后销毁内部 DOM。
  -->
  <ElDialog
    v-bind="$attrs"
    :close-on-click-modal="false"
    destroy-on-close
    class="ss-dialog"
  >
    <!-- 默认插槽：弹窗内容区域 -->
    <slot />

    <!--
      Footer 区域：通过 v-if 控制整体显隐。
      内部使用具名插槽 #footer 实现双层降级：
      - 不传 #footer → 渲染默认的「取消 + 确认(loading)」按钮
      - 传入 #footer → 由使用方完全自定义
    -->
    <template v-if="showFooter" #footer>
      <slot name="footer">
        <div class="ss-dialog__footer">
          <ElButton @click="emit('cancel')">{{ cancelText }}</ElButton>
          <ElButton type="primary" :loading="loading" @click="emit('confirm')">
            {{ confirmText }}
          </ElButton>
        </div>
      </slot>
    </template>
  </ElDialog>
</template>

<!--
  全局（非 scoped）样式。
  原因：ElDialog 默认通过 Vue Teleport 将弹窗 DOM 挂载到 <body> 下，
  而 scoped 样式生成的 data-v-xxx 属性只作用于当前组件模板内的 DOM 节点，
  无法穿透到 Teleport 目标容器中的 ElDialog 真实 DOM。
  因此必须使用全局样式，并通过 .ss-dialog 类名来限定作用域，避免污染其他组件。
-->
<style lang="scss">
.ss-dialog {
  /* 对话框整体圆角，比 Element Plus 默认值更圆润 */
  border-radius: 12px;

  /* ---- 头部区域 ---- */
  .el-dialog__header {
    padding: 20px 24px 16px;
    margin-right: 0; /* 覆盖 ElDialog 默认的右侧 margin（为关闭按钮留出的空间） */
    border-bottom: 1px solid var(--border-color); /* 头部与内容区之间用分割线分隔 */

    .el-dialog__title {
      font-size: 16px;
      font-weight: 600;
      color: var(--text-primary);
    }

    /* 调整关闭按钮位置，与新的 padding 对齐 */
    .el-dialog__headerbtn {
      top: 20px;
      right: 20px;
    }
  }

  /* ---- 内容区域 ---- */
  .el-dialog__body {
    padding: 20px 24px;
  }

  /* ---- 底部区域 ---- */
  .el-dialog__footer {
    padding: 12px 24px 20px;
    border-top: 1px solid var(--border-color); /* 底部与内容区之间用分割线分隔 */
  }
}

/* 默认 footer 布局：按钮右对齐，间距 8px */
.ss-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;

  .el-button {
    border-radius: var(--border-radius);
    font-weight: 500;
    margin-left: 0; /* 覆盖 Element Plus 默认的 .el-button + .el-button { margin-left: 12px } */
  }
}
</style>
