/**
 * ECharts 按需模块统一注册入口。
 *
 * 所有图表组件只需 `import '@/utils/echarts'` 即可获得全部已注册模块，
 * 避免在每个图表组件中重复调用 use() 注册相同的模块。
 * Tree-shaking 仍然有效——只有此处 import 的模块才会被打包。
 */
import { use } from 'echarts/core'

// ---- 图表类型 ----
import { BarChart, PieChart, ScatterChart, LineChart } from 'echarts/charts'

// ---- 组件 ----
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent,
} from 'echarts/components'

// ---- 渲染器 ----
import { CanvasRenderer } from 'echarts/renderers'

// ---- 特性兼容 ----
import { LegacyGridContainLabel } from 'echarts/features'

use([
  // 图表
  BarChart,
  PieChart,
  ScatterChart,
  LineChart,
  // 组件
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent,
  // 渲染器 & 兼容
  CanvasRenderer,
  LegacyGridContainLabel,
])
