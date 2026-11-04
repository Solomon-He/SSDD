<script setup lang="ts">
import { computed } from 'vue'
import type { DetectResultBase } from '@/types/detect'
import { DEFECT_CLASSES } from '@/constants/defects'
import { useImageUrl } from '@/composables/useImageUrl'
import ImagePreview from '@/components/common/ImagePreview.vue'
import DefectBarChart from '@/components/charts/DefectBarChart.vue'
import DefectPieChart from '@/components/charts/DefectPieChart.vue'

const props = defineProps<{
  result: DetectResultBase
}>()

const { toFullUrl } = useImageUrl()

const images = computed(() => [
  { label: '原图', url: toFullUrl(props.result.original_image_url) },
  { label: '掩码图', url: toFullUrl(props.result.mask_image_url) },
  { label: '叠加图', url: toFullUrl(props.result.overlay_image_url) },
])

const defectRatioPercent = computed(() => (props.result.defect_info.defect_ratio * 100).toFixed(2))

const barData = computed(() =>
  DEFECT_CLASSES.map((cls) => ({
    name: cls.label,
    value: props.result.defect_class_counts[String(cls.index)] || 0,
    color: cls.color,
  })),
)

const pieData = computed(() =>
  DEFECT_CLASSES.map((cls) => ({
    name: cls.label,
    value: props.result.defect_info.class_distribution[String(cls.index)] || 0,
    color: cls.color,
  })).filter((d) => d.value > 0),
)
</script>

<template>
  <div class="single-result">
    <ElAlert type="info" :closable="false" title="预览图会压缩多类别重叠信息；统计来自四通道掩码。图片链接默认有效期为 15 分钟，过期后请重新打开检测详情。" />
    <div v-if="result.inference_config_snapshot" class="section-title">
      实际设备：{{ result.inference_config_snapshot.actual_device.toUpperCase() }}
      <span v-if="result.inference_config_snapshot.fallback_reason">（CUDA 初始化失败，已回退 CPU）</span>
    </div>
    <!-- 三宫格图片对比 -->
    <div class="image-compare">
      <div v-for="img in images" :key="img.label" class="image-cell">
        <div class="image-label">{{ img.label }}</div>
        <ImagePreview :src="img.url" :alt="img.label" :title="img.label" />
      </div>
    </div>

    <div class="result-details">
      <!-- 缺陷统计摘要卡片 -->
      <div class="summary-cards">
        <div class="summary-card">
          <span class="card-value">{{ result.defect_count }}</span>
          <span class="card-label">缺陷区域数</span>
        </div>
        <div class="summary-card">
          <span class="card-value">{{ defectRatioPercent }}%</span>
          <span class="card-label">面积占比</span>
        </div>
        <div class="summary-card">
          <span class="card-value">{{ result.inference_time_ms.toFixed(1) }}</span>
          <span class="card-label">推理耗时 (ms)</span>
        </div>
      </div>

      <!-- 图表区：柱状图 + 饼图并排 -->
      <div class="charts-row">
        <div class="chart-cell">
          <div class="section-title">缺陷分类统计</div>
          <DefectBarChart :data="barData" />
        </div>
        <div v-if="pieData.length" class="chart-cell">
          <div class="section-title">缺陷像素分布</div>
          <DefectPieChart :data="pieData" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.single-result {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.image-compare {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-md);

  .image-cell {
    display: flex;
    flex-direction: column;
    gap: var(--spacing-xs);

    .image-label {
      font-size: 12px;
      font-weight: 500;
      color: var(--text-secondary);
      text-align: center;
    }
  }
}

.result-details {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.summary-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-md);

  .summary-card {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    padding: var(--spacing-md);
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius);
    background-color: #fafbfc;

    .card-value {
      font-size: 24px;
      font-weight: 700;
      color: var(--text-primary);
    }

    .card-label {
      font-size: 12px;
      color: var(--text-secondary);
    }
  }
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--spacing-sm);
}

.charts-row {
  display: flex;
  flex-wrap: wrap;
  gap: calc(var(--spacing-xl) * 2);

  .chart-cell {
    flex: 1 1 300px;
    min-width: 0;
  }
}
</style>
