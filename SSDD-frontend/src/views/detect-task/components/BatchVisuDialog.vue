<script setup lang="ts">
import { ref, nextTick } from 'vue'
import type { BatchDetectItem } from '@/types/detect'
import DefectBubbleChart from '@/components/charts/DefectBubbleChart.vue'
import DefectStackedBar from '@/components/charts/DefectStackedBar.vue'
import SsDialog from '@/components/SsDialog/index.vue'

defineProps<{
  results: BatchDetectItem[]
}>()

const visible = ref(false)
const activeTab = ref('bubble')
/** 控制图表渲染时机，避免 ECharts 获取不到 DOM 尺寸 */
const chartReady = ref(false)

function open() {
  visible.value = true
}

/**
 * 弹窗打开动画结束后才允许渲染图表。
 * ElDialog 打开时存在过渡动画，动画期间内部 DOM 的 clientWidth/clientHeight 为 0，
 * ECharts 此时初始化会触发警告："Can't get DOM width or height"。
 */
function onOpened() {
  chartReady.value = true
}

function onClosed() {
  chartReady.value = false
  activeTab.value = 'bubble'
}

/**
 * 切换 Tab 时先销毁当前图表，等待 nextTick DOM 更新后再重新挂载。
 * ElTabPane 切换同样存在短暂的 DOM 尺寸为 0 的问题，
 * 通过先置 false 再置 true 强制重建图表实例，确保容器已有正确尺寸。
 */
async function onTabChange() {
  chartReady.value = false
  await nextTick()
  chartReady.value = true
}

defineExpose({ open })
</script>

<template>
  <SsDialog
    v-model="visible"
    title="批量检测可视化分析"
    width="800px"
    align-center
    :show-footer="false"
    @opened="onOpened"
    @closed="onClosed"
  >
    <ElTabs v-model="activeTab" @tab-change="onTabChange">
      <ElTabPane label="缺陷面积占比气泡图" name="bubble">
        <p class="chart-desc">
          X 轴代表对应图片，Y 轴代表面积占比。气泡大小表示该缺陷在一张图片中的面积占比，鼠标悬浮可查看详细分布。
        </p>
        <DefectBubbleChart v-if="chartReady && activeTab === 'bubble'" :results="results" />
      </ElTabPane>
      <ElTabPane label="缺陷类别汇总堆叠图" name="stacked">
        <p class="chart-desc">
          每根柱子代表一张图片，不同颜色表示各缺陷类别的数量，可直观对比各图片的缺陷构成。
        </p>
        <DefectStackedBar v-if="chartReady && activeTab === 'stacked'" :results="results" />
      </ElTabPane>
    </ElTabs>
  </SsDialog>
</template>

<style scoped lang="scss">
.chart-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0 0 var(--spacing-sm);
  line-height: 1.6;
}
</style>
