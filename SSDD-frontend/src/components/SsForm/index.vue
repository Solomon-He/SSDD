<!--
  SsForm — JSON 配置驱动的表单组件

  核心思路：通过一个配置数组（SsFormItemConfig[]）自动渲染表单项，
  配合 useSsForm Hook 实现「配置 → 数据 → UI」的单向驱动。

  功能特性：
  1. 配置驱动：遍历 config 数组自动生成 ElFormItem + 对应组件
  2. CSS Grid 布局：通过 rowCount 控制每行列数，span 控制单项跨列
  3. 动态组件：使用 Vue 的 <component :is> + resolveComponent() 支持字符串和组件对象
  4. 插槽覆盖：当 config 无法满足特殊需求时，可通过 #prop名 具名插槽完全自定义
  5. 动态显隐：isHide 支持布尔值和函数，实现表单项的条件渲染

  数据流：
  父组件 → v-model（formModel）→ SsForm → v-model 绑定到各子组件
  父组件 → v-model:formRef → SsForm → 内部 ElForm ref 回传给父组件
-->
<script setup lang="ts">
import { computed, type Component } from 'vue'
import type { SsFormProps } from './types'
import type { FormInstance } from 'element-plus'
import { resolveComp, filterVisible } from '@/utils/component'

defineOptions({ name: 'SsForm' })

const props = withDefaults(defineProps<SsFormProps>(), {
  rowCount: 1,
  labelPosition: 'top',
})

/**
 * 表单数据的双向绑定。
 * 父组件通过 v-model 传入 formModel（由 useSsForm 创建的 reactive 对象），
 * SsForm 内部将每个字段通过 v-model="model[item.prop]" 绑定到对应的输入组件。
 * 使用 any 是因为表单字段的值类型在运行时由配置决定，编译期无法精确推断。
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const model = defineModel<Record<string, any>>({ required: true })

/**
 * ElForm 实例的双向传递。
 * SsForm 内部持有 ElForm 的 ref，通过 defineModel('formRef') 回传给父组件，
 * 使得父组件可以调用 formRef.value?.validate() 等 ElForm 原生方法。
 * 这是利用 Vue 3.4+ 的 defineModel 实现「子组件 ref 向上暴露」的技巧。
 */
const formRef = defineModel<FormInstance | undefined>('formRef')

/**
 * 过滤掉 isHide 为 true 的配置项，只保留需要渲染的表单项。
 */
const visibleConfig = computed(() => filterVisible(props.config))

/**
 * CSS Grid 布局的列数，通过 CSS 变量传递给模板。
 */
const gridVars = computed(() => ({
  '--ss-form-cols': props.rowCount,
}))

/**
 * 解析组件引用：统一使用共享工具函数 resolveComp。
 */
function resolve(comp: string | Component): Component {
  return resolveComp(comp)
}
</script>

<template>
  <!--
    ElForm 核心属性绑定：
    - ref="formRef": 通过 defineModel('formRef') 实现双向传递，父组件可直接访问 ElForm 实例
    - :model: 绑定响应式表单数据
    - :rules: 绑定 useSsForm 合并后的校验规则
  -->
  <ElForm
    ref="formRef"
    :model="model"
    :rules="rules"
    :label-position="labelPosition"
    :label-width="labelWidth"
    class="ss-form"
  >
    <!-- CSS Grid 容器：根据 rowCount 决定列数 -->
    <div class="ss-form__grid" :style="gridVars">
      <template v-for="item in visibleConfig" :key="item.prop">
        <!--
          每个 ElFormItem 对应 config 中的一项。
          :prop 用于告知 ElForm 该项对应 model 中的哪个字段（校验时需要）。
          :style 中的 gridColumn: span N 可让该项跨越多列（如 textarea、divider）。
        -->
        <ElFormItem
          :label="item.label"
          :prop="item.prop"
          :style="item.span ? { gridColumn: `span ${item.span}` } : undefined"
        >
          <!--
            具名插槽机制（核心扩展点）：
            插槽名 = item.prop（字段名），父组件可通过 #字段名 自定义该表单项的渲染。
            例如 UploadModelDialog 中通过 #file 插槽自定义了文件上传区域。
            插槽参数: model（表单数据）和 item（当前配置项），方便自定义渲染时访问。

            若父组件未提供对应插槽，则回退到默认渲染：
            使用 <component :is> 动态渲染 item.comp 指定的组件，
            通过 v-model 绑定对应字段值，v-bind 展开 compProps。
          -->
          <slot :name="item.prop" :model="model" :item="item">
            <component
              :is="resolve(item.comp ?? 'ElInput')"
              v-model="model[item.prop]"
              v-bind="item.compProps"
            />
          </slot>
        </ElFormItem>
      </template>
    </div>

    <!--
      默认插槽：放置 config 数组无法描述的额外内容。
      例如 EditModelDialog 中在两个 SsForm 之间插入了 ElDivider 分割线。
    -->
    <slot />
  </ElForm>
</template>

<!--
  非 scoped 全局样式，通过 .ss-form 类名限定作用域。
  统一所有 SsForm 实例中表单项的间距和标签样式。
-->
<style lang="scss">
.ss-form {
  /* Grid 容器 */
  &__grid {
    display: grid;
    grid-template-columns: repeat(var(--ss-form-cols, 1), 1fr);
    gap: 0 16px;
  }

  /* 表单项间距 */
  .el-form-item {
    margin-bottom: 20px;
  }

  /* 表单标签样式：加粗、主色调、紧凑行高 */
  .el-form-item__label {
    font-weight: 500;
    color: var(--text-primary);
    padding-bottom: 6px;
    line-height: 1.4;
  }
}
</style>
