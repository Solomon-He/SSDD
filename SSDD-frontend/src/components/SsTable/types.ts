/**
 * SsTable 类型定义文件
 *
 * 定义了配置驱动表格系统的核心类型：
 * - SsTableColumnConfig：单列的配置描述
 * - SsTableProps：SsTable 组件接受的 Props 类型
 *
 * 这些类型同时被 SsTable 组件和 useSsTable Hook 共同引用。
 */
import type { VNode } from 'vue'

/**
 * 单列配置项
 *
 * 每个配置项描述一个表格列的完整信息：字段名、标题、宽度、对齐方式、排序及自定义渲染。
 * 配置数组由 useSsTable Hook 消费，最终驱动 SsTable 组件自动渲染列。
 *
 * @example
 * ```tsx
 * const columns: SsTableColumnConfig[] = [
 *   { prop: 'name', label: '名称', minWidth: 120 },
 *   { prop: 'status', label: '状态', width: 100, formatter: (row) => <ElTag>{row.status}</ElTag> },
 * ]
 * ```
 */
export interface SsTableColumnConfig {
  /** 字段名，对应行数据对象中的 key */
  prop: string
  /** 列标题 */
  label: string
  /** 列固定宽度（px） */
  width?: number | string
  /** 列最小宽度（px），列会根据内容自动扩展但不小于此值 */
  minWidth?: number | string
  /** 固定列方向，true 等同于 'left' */
  fixed?: boolean | 'left' | 'right'
  /** 内容对齐方式，默认 'left' */
  align?: 'left' | 'center' | 'right'
  /** 是否可排序，设为 'custom' 时触发远程排序事件 */
  sortable?: boolean | 'custom'
  /**
   * 自定义渲染函数（TSX）。
   * 接收当前行数据 row 和行索引 index，返回 VNode。
   * 当需要渲染标签、按钮、图标等复杂内容时使用。
   *
   * @example
   * ```tsx
   * formatter: (row) => <ElTag type="success">{row.status}</ElTag>
   * ```
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  formatter?: (row: any, index: number) => VNode | string | number
  /** 是否隐藏该列 */
  isHide?: boolean | (() => boolean)
}

/**
 * SsTable 组件 Props 类型
 *
 * 该类型由 useSsTable Hook 的 tableConfig 计算属性生成，
 * 使用方只需 v-bind="tableConfig" 即可将全部配置传入 SsTable 组件。
 */
export interface SsTableProps {
  /** 列配置数组，驱动表格列的自动渲染 */
  columns: SsTableColumnConfig[]
  /** 是否显示多选列（checkbox） */
  showSelection?: boolean
  /** 是否显示序号列 */
  showSerialNum?: boolean
  /** 透传给 ElTable 的原生属性（如 stripe、border、height 等） */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  tableAttrs?: Record<string, any>
}
