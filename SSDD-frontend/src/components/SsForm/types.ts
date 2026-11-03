/**
 * SsForm 类型定义文件
 *
 * 定义了 JSON 配置驱动表单系统的核心类型：
 * - SsFormItemConfig：单个表单项的配置描述
 * - SsFormProps：SsForm 组件接受的 Props 类型
 *
 * 这些类型同时被 SsForm 组件和 useSsForm Hook 共同引用。
 */
import type { FormRules, FormItemRule } from 'element-plus'
import type { SsBaseFieldConfig } from '@/utils/component'

/**
 * 单个表单项配置
 *
 * 扩展自 SsBaseFieldConfig，增加了表单校验规则和 Grid 跨列配置。
 * 配置数组由 useSsForm Hook 消费，最终驱动 SsForm 组件自动渲染表单。
 *
 * @example
 * ```ts
 * const config: SsFormItemConfig[] = [
 *   { prop: 'name', label: '名称', comp: SsInput, compProps: { placeholder: '请输入' }, rules: true },
 *   { prop: 'desc', label: '描述', comp: SsInput, compProps: { type: 'textarea', rows: 3 } },
 * ]
 * ```
 */
export interface SsFormItemConfig extends SsBaseFieldConfig {
  /**
   * 校验规则，支持两种模式：
   * - true: 由 useSsForm 自动生成必填规则，message 自动拼接 label
   *         如 label='名称' → { required: true, message: '请输入名称', trigger: 'blur' }
   *         若 comp 为选择类组件则自动切换为 '请选择' + trigger: 'change'
   * - FormItemRule[]: 自定义规则数组，支持 Element Plus 全部校验器语法
   * - 不传时表示该字段无校验
   */
  rules?: true | FormItemRule[]
  /** 该项在 CSS Grid 中占据的列数，默认 1。设为 2 可让该项横跨两列（如 textarea） */
  span?: number
}

/**
 * SsForm 组件 Props 类型
 *
 * 该类型由 useSsForm Hook 的 formProps 计算属性生成，
 * 使用方只需 v-bind="formProps" 即可将全部配置传入 SsForm 组件。
 */
export interface SsFormProps {
  /** 表单配置数组，驱动表单项的自动渲染 */
  config: SsFormItemConfig[]
  /** 每行显示几列，默认 1。通过 CSS Grid 的 grid-template-columns 实现 */
  rowCount?: number
  /** 表单校验规则（由 useSsForm 从 config 中提取并合并 extraRules 后生成） */
  rules?: FormRules
  /** label 位置，默认 'top'（标签在输入框上方） */
  labelPosition?: 'top' | 'left' | 'right'
  /** label 宽度，仅在 labelPosition 为 'left' 或 'right' 时生效 */
  labelWidth?: string | number
}
