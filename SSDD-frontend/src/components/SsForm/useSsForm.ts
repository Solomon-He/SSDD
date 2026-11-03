import { reactive, ref, computed } from 'vue'
import type { FormInstance, FormRules, FormItemRule } from 'element-plus'
import type { SsFormItemConfig, SsFormProps } from './types'
import { isSelectLikeComp } from '@/utils/component'

/**
 * 通用的「字符串键 → 任意值」字典类型。
 * 因为表单数据的 value 类型在运行时由配置决定，编译期无法精确推断，
 * 此处使用 any 是必要的妥协，通过 eslint-disable 显式声明意图。
 */
/* eslint-disable @typescript-eslint/no-explicit-any */
type AnyRecord = Record<string, any>
/* eslint-enable @typescript-eslint/no-explicit-any */

/**
 * useSsForm — 配置驱动表单的核心 Composable（Hook）
 *
 * 核心职责：
 * 1. 根据 config 数组 + defaultValue 自动构建响应式的 formModel
 * 2. 从 config 的 rules 字段提取校验规则，与额外 rules 合并
 * 3. 生成可直接 v-bind 到 SsForm 组件的 formProps 对象
 * 4. 暴露 validate / resetFields / clearValidate / setValues 等快捷方法
 *
 * 泛型参数 T 用于约束 formModel 的类型，使用方可传入具体接口获得完整类型推导。
 *
 * @param config  表单项配置数组，描述每个字段的渲染方式和校验规则
 * @param options 额外选项
 * @param options.defaultValue  表单初始值，未提供的字段默认为 null
 * @param options.rowCount      每行列数，默认 1
 * @param options.rules          额外校验规则，会覆盖 config 中同名字段的规则
 * @param options.labelPosition  标签位置，默认 'top'
 * @param options.labelWidth     标签宽度
 *
 * @returns formModel    — 响应式表单数据对象
 * @returns formProps    — 计算属性，直接 v-bind 到 SsForm 组件
 * @returns formRef      — ElForm 实例的 ref，用于底层 API 调用
 * @returns validate      — 表单全量校验，返回 Promise<boolean>
 * @returns resetFields   — 重置表单到初始值并清除校验
 * @returns clearValidate — 清除指定字段或全部字段的校验状态
 * @returns setValues     — 批量更新表单字段值
 *
 * @example
 * ```ts
 * const { formModel, formProps, formRef, validate, resetFields, setValues } = useSsForm(config, {
 *   defaultValue: { name: '', version: '1.0.0' },
 *   rowCount: 2,
 * })
 * ```
 */
export function useSsForm<T extends AnyRecord = AnyRecord>(
  config: SsFormItemConfig[],
  options: {
    defaultValue?: Partial<T>
    rowCount?: number
    rules?: FormRules
    labelPosition?: 'top' | 'left' | 'right'
    labelWidth?: string | number
  } = {},
) {
  const {
    defaultValue = {} as Partial<T>,
    rowCount = 1,
    rules: extraRules = {},
    labelPosition = 'top',
    labelWidth,
  } = options

  // ---- 1. 构建初始表单数据 ----
  // 遍历 config 中的每个字段名，从 defaultValue 中取值，未提供的字段默认为 null
  const initialValues: AnyRecord = {}
  for (const item of config) {
    initialValues[item.prop] = (defaultValue as AnyRecord)[item.prop] ?? null
  }

  /**
   * 响应式表单数据对象。
   * 使用 reactive<T> 包裹，保证模板中双向绑定（v-model）的响应性。
   * 类型断言 `as T` 是因为 initialValues 是运行时动态构建的，编译期无法自动推断为 T。
   */
  const formModel = reactive<T>({ ...initialValues } as T)

  /** ElForm 组件实例引用，用于调用 validate / resetFields 等原生方法 */
  const formRef = ref<FormInstance>()

  // ---- 2. 从 config 中提取校验规则并合并 ----
  /**
   * 合并后的校验规则（计算属性）。
   *
   * 规则来源优先级：
   * 1. config 中每个 item 的 rules 字段（true → 自动生成必填规则；数组 → 自定义规则）
   * 2. options.rules（extraRules），会覆盖 config 中同名字段的规则
   *
   * 设计为计算属性是为了支持 config 中 rules 的动态变化（虽然当前场景下 config 通常是静态的）。
   */
  const mergedRules = computed<FormRules>(() => {
    const configRules: FormRules = {}
    for (const item of config) {
      if (item.rules === true) {
        // 自动生成必填规则：根据组件类型决定 message 前缀和 trigger
        const isSelect = isSelectLikeComp(item.comp)
        configRules[item.prop] = [
          {
            required: true,
            message: `请${isSelect ? '选择' : '输入'}${item.label}`,
            trigger: isSelect ? 'change' : 'blur',
          } as FormItemRule,
        ]
      } else if (Array.isArray(item.rules)) {
        // 使用方自定义的规则数组，直接透传
        configRules[item.prop] = item.rules
      }
    }
    // extraRules 放在后面，同名字段会覆盖 configRules 中的规则
    return { ...configRules, ...extraRules }
  })

  // ---- 3. 组装 SsForm 组件的 Props ----
  /**
   * formProps 计算属性，使用方只需 `v-bind="formProps"` 即可将全部配置传入 SsForm 组件，
   * 无需逐个绑定 config / rowCount / rules / labelPosition 等属性。
   */
  const formProps = computed<SsFormProps>(() => ({
    config,
    rowCount,
    rules: mergedRules.value,
    labelPosition,
    labelWidth,
  }))

  // ---- 4. 快捷方法封装 ----

  /**
   * 表单全量校验。
   * 调用 ElForm 的 validate()，校验失败时 ElForm 会 reject Promise，
   * 此处通过 .catch(() => false) 将异常转为 false，方便使用方用 if (!valid) return 的模式。
   * 若 formRef 尚未挂载（undefined），返回 false 兜底。
   */
  async function validate() {
    return formRef.value?.validate().catch(() => false) ?? false
  }

  /** 重置表单到初始值，并清除所有校验状态 */
  function resetFields() {
    formRef.value?.resetFields()
  }

  /** 清除指定字段（或全部字段）的校验状态，不重置表单值 */
  function clearValidate(props?: string | string[]) {
    formRef.value?.clearValidate(props)
  }

  /**
   * 批量更新表单字段值。
   * 只更新 formModel 中已存在的 key，忽略传入的多余字段，防止意外注入未知属性。
   */
  function setValues(values: Partial<T>) {
    for (const key of Object.keys(values)) {
      if (key in formModel) {
        ;(formModel as AnyRecord)[key] = (values as AnyRecord)[key]
      }
    }
  }

  return {
    /** 响应式表单数据，用于 v-model 双向绑定 */
    formModel,
    /** 计算属性，直接 v-bind 到 SsForm 组件 */
    formProps,
    /** ElForm 实例引用，传入 SsForm 的 v-model:form-ref */
    formRef,
    /** 全量校验，返回 Promise<boolean> */
    validate,
    /** 重置表单值 + 清除校验 */
    resetFields,
    /** 仅清除校验状态 */
    clearValidate,
    /** 批量更新字段值 */
    setValues,
  }
}
