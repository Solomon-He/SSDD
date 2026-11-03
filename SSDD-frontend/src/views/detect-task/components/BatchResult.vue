<script setup lang="ts">
import { ref, computed } from 'vue'
import type { BatchDetectItem } from '@/types/detect'
import { DEFECT_CLASSES } from '@/constants/defects'
import { useImageUrl } from '@/composables/useImageUrl'
import ImagePreview from '@/components/common/ImagePreview.vue'
import DefectTag from '@/components/common/DefectTag.vue'
import SingleResult from './SingleResult.vue'

const props = defineProps<{
  results: BatchDetectItem[]
}>()

const { toFullUrl } = useImageUrl()

/** 当前展开详情的卡片索引 */
const expandedIndex = ref<number | null>(null)

function toggleExpand(index: number) {
  expandedIndex.value = expandedIndex.value === index ? null : index
}

/** 详情弹窗 */
const detailVisible = ref(false)
/**
 * 控制 SingleResult 的渲染时机。
 * ElDialog 打开动画期间内部 DOM 尺寸为 0，如果此时挂载包含 ECharts 图表的组件，
 * 会触发 "Can't get DOM width or height" 警告。
 * 因此通过 @opened 回调延迟至动画结束后才将其置为 true。
 */
const detailReady = ref(false)
const detailItem = ref<BatchDetectItem | null>(null)

function openDetail(item: BatchDetectItem) {
  detailItem.value = item
  detailVisible.value = true
}

function onDetailOpened() {
  detailReady.value = true
}

function onDetailClosed() {
  detailReady.value = false
}

/** 统计汇总（防御空数组除零） */
const summary = computed(() => {
  const total = props.results.length
  const totalDefects = props.results.reduce((sum, r) => sum + r.defect_count, 0)
  const avgTime = total > 0
    ? props.results.reduce((sum, r) => sum + r.inference_time_ms, 0) / total
    : 0
  return { total, totalDefects, avgTime: avgTime.toFixed(1) }
})
</script>

<template>
  <div class="batch-result">
    <!-- 汇总统计 -->
    <div class="batch-summary">
      <div class="stat-item">
        <span class="stat-value">{{ summary.total }}</span>
        <span class="stat-label">检测图片</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ summary.totalDefects }}</span>
        <span class="stat-label">缺陷总数</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ summary.avgTime }} ms</span>
        <span class="stat-label">平均耗时</span>
      </div>
    </div>

    <!-- 结果卡片列表 -->
    <div class="card-list">
      <div
        v-for="(item, idx) in results"
        :key="item.record_id"
        class="result-card"
        :class="{ expanded: expandedIndex === idx }"
      >
        <div class="card-main" @click="toggleExpand(idx)">
          <div class="card-thumb">
            <!-- loading="lazy"：浏览器原生懒加载，批量图片不会同时请求 -->
            <img :src="toFullUrl(item.overlay_image_url)" :alt="item.file_name" loading="lazy" />
          </div>
          <div class="card-info">
            <div class="card-title">{{ item.file_name }}</div>
            <div class="card-meta">
              <span>缺陷：<strong>{{ item.defect_count }}</strong></span>
              <span>耗时：{{ item.inference_time_ms.toFixed(1) }} ms</span>
            </div>
            <div class="card-tags">
              <template v-for="cls in DEFECT_CLASSES" :key="cls.index">
                <DefectTag
                  v-if="item.defect_class_counts[String(cls.index)]"
                  :class-index="cls.index"
                />
              </template>
              <span v-if="item.defect_count === 0" class="no-defect">无缺陷</span>
            </div>
          </div>
          <ElButton
            class="detail-btn"
            type="primary"
            size="small"
            link
            @click.stop="openDetail(item)"
          >
            查看详细
          </ElButton>
          <ElIcon class="expand-icon" :size="16">
            <ArrowDown v-if="expandedIndex !== idx" />
            <ArrowUp v-else />
          </ElIcon>
        </div>

        <!-- 展开详情：三宫格 -->
        <div v-if="expandedIndex === idx" class="card-detail">
          <div class="detail-images">
            <div class="detail-image-cell">
              <div class="detail-label">原图</div>
              <ImagePreview :src="toFullUrl(item.original_image_url)" alt="原图" title="原图" />
            </div>
            <div class="detail-image-cell">
              <div class="detail-label">掩码图</div>
              <ImagePreview :src="toFullUrl(item.mask_image_url)" alt="掩码图" title="掩码图" />
            </div>
            <div class="detail-image-cell">
              <div class="detail-label">叠加图</div>
              <ImagePreview :src="toFullUrl(item.overlay_image_url)" alt="叠加图" title="叠加图" />
            </div>
          </div>
          <div class="detail-stats">
            <span>面积占比：{{ (item.defect_info.defect_ratio * 100).toFixed(2) }}%</span>
            <span>缺陷像素：{{ item.defect_info.defect_pixels }} / {{ item.defect_info.total_pixels }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 单张详情弹窗 -->
    <ElDialog
      v-model="detailVisible"
      :title="detailItem?.file_name ? `检测详情 - ${detailItem.file_name}` : '检测详情'"
      width="900px"
      destroy-on-close
      align-center
      @opened="onDetailOpened"
      @closed="onDetailClosed"
    >
      <SingleResult v-if="detailReady && detailItem" :result="detailItem" />
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.batch-result {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.batch-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-md);

  .stat-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    padding: var(--spacing-md);
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius);
    background-color: #fafbfc;

    .stat-value {
      font-size: 22px;
      font-weight: 700;
      color: var(--text-primary);
    }

    .stat-label {
      font-size: 12px;
      color: var(--text-secondary);
    }
  }
}

.card-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.result-card {
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  overflow: hidden;
  transition: box-shadow 0.2s ease;

  &:hover {
    box-shadow: var(--shadow-sm);
  }

  &.expanded {
    box-shadow: var(--shadow-md);
  }
}

.card-main {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
  cursor: pointer;

  .card-thumb {
    width: 56px;
    height: 56px;
    border-radius: 4px;
    overflow: hidden;
    flex-shrink: 0;
    border: 1px solid var(--border-color);

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  .card-info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;

    .card-title {
      font-size: 14px;
      font-weight: 500;
      color: var(--text-primary);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .card-meta {
      display: flex;
      gap: var(--spacing-md);
      font-size: 12px;
      color: var(--text-secondary);

      strong {
        color: var(--text-primary);
      }
    }

    .card-tags {
      display: flex;
      gap: var(--spacing-xs);
      flex-wrap: wrap;

      .no-defect {
        font-size: 12px;
        color: var(--color-success);
      }
    }
  }

  .expand-icon {
    color: var(--text-placeholder);
    flex-shrink: 0;
  }

  .detail-btn {
    flex-shrink: 0;
    font-size: 12px;
  }
}

.card-detail {
  padding: var(--spacing-md);
  border-top: 1px solid var(--border-color);
  background-color: #fafbfc;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);

  .detail-images {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: var(--spacing-sm);
  }

  .detail-image-cell {
    display: flex;
    flex-direction: column;
    gap: 4px;

    .detail-label {
      font-size: 11px;
      color: var(--text-secondary);
      text-align: center;
    }
  }

  .detail-stats {
    display: flex;
    gap: var(--spacing-lg);
    font-size: 12px;
    color: var(--text-secondary);
  }
}
</style>
