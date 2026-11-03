<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { formatDateTime, formatNumber } from '@/utils/format'
import type { DetectRecord } from '@/types/detect'
import type { Model } from '@/types/model'
import DefectTag from '@/components/common/DefectTag.vue'

const props = defineProps<{
  totalRecords: number
  records: DetectRecord[]
  activeModel: Model | null
  avgDefectCount: number
  avgInferenceTime: number
}>()

const router = useRouter()

/** 最近 8 条记录 */
const recentRecords = computed(() => props.records.slice(0, 8))

function goToRecord(id: string) {
  router.push(`/records/${id}`)
}

function getDefectTypes(record: DetectRecord): number[] {
  return Object.entries(record.defect_class_counts)
    .filter(([, v]) => v > 0)
    .map(([k]) => Number(k))
}
</script>

<template>
  <section class="overview">
    <div class="overview__grid">
      <!-- ====== 第一列：个人概况 ====== -->
      <div class="overview__col overview__col--stats">
        <h2 class="section-title">我的检测概况</h2>
        <div class="stat-stack">
          <div class="stat-item" style="--accent: #3b82f6">
            <span class="stat-item__value">{{ totalRecords.toLocaleString() }}</span>
            <span class="stat-item__label">检测总数</span>
          </div>
          <div class="stat-item" style="--accent: #10b981">
            <span class="stat-item__value">{{ formatNumber(avgDefectCount, 1) }}</span>
            <span class="stat-item__label">平均缺陷数量</span>
          </div>
          <div class="stat-item" style="--accent: #f59e0b">
            <span class="stat-item__value">{{ formatNumber(avgInferenceTime, 1) }} ms</span>
            <span class="stat-item__label">平均推理耗时</span>
          </div>
        </div>

        <!-- 活跃模型（紧凑版） -->
        <h2 class="section-title" style="margin-top: 20px">当前活跃模型</h2>
        <div v-if="activeModel" class="model-compact">
          <div class="model-compact__head">
            <span class="model-compact__name">{{ activeModel.name }}</span>
            <el-tag type="success" size="small" effect="light">
              v{{ activeModel.version }}
            </el-tag>
          </div>
          <div class="model-compact__metrics">
            <span v-if="activeModel.miou != null">
              mIoU <strong>{{ (activeModel.miou * 100).toFixed(1) }}%</strong>
            </span>
            <span v-if="activeModel.dice != null">
              Dice <strong>{{ (activeModel.dice * 100).toFixed(1) }}%</strong>
            </span>
            <span v-if="activeModel.fps != null">
              FPS <strong>{{ activeModel.fps.toFixed(1) }}</strong>
            </span>
          </div>
        </div>
        <div v-else class="model-compact model-compact--empty">
          <span class="text-muted">暂无活跃模型</span>
        </div>
      </div>

      <!-- ====== 第二、三列：最近检测记录 ====== -->
      <div class="overview__col overview__col--records">
        <div class="section-header">
          <h2 class="section-title">最近检测记录</h2>
          <el-button text type="primary" @click="router.push('/records')">查看全部 →</el-button>
        </div>

        <div v-if="recentRecords.length" class="records-list">
          <div
            v-for="record in recentRecords"
            :key="record.id"
            class="record-row"
            @click="goToRecord(record.id)"
          >
            <div class="record-row__left">
              <span
                class="defect-dot"
                :class="record.defect_count > 0 ? 'defect-dot--has' : 'defect-dot--none'"
              />
              <span class="record-row__time">
                {{ formatDateTime(record.created_at, 'MM-DD HH:mm') }}
              </span>
              <span class="record-row__defects">
                {{ record.defect_count > 0 ? `${record.defect_count} 处缺陷` : '无缺陷' }}
              </span>
            </div>
            <div class="record-row__right">
              <div class="record-row__tags">
                <DefectTag
                  v-for="idx in getDefectTypes(record)"
                  :key="idx"
                  :class-index="idx"
                />
              </div>
              <el-button link type="primary" size="small" @click.stop="goToRecord(record.id)">
                详情
              </el-button>
            </div>
          </div>
        </div>

        <el-empty v-else description="暂无检测记录" :image-size="64">
          <el-button type="primary" size="small" @click="router.push('/detect')">
            开始检测
          </el-button>
        </el-empty>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@/assets/styles/mixins' as *;

.section-title {
  @include section-title;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* ============ 三列网格（1 : 2） ============ */
.overview__grid {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 24px;
  align-items: start;
}

/* ============ 第一列：统计 + 模型 ============ */
.stat-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stat-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: 10px;
  padding: 16px 20px;
  border: 1px solid var(--border-color);
  border-left: 3px solid var(--accent, var(--color-primary));
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;

  &:hover {
    transform: translateX(2px);
    box-shadow: var(--shadow-sm);
  }

  &__value {
    font-size: 22px;
    font-weight: 700;
    color: var(--text-primary);
    letter-spacing: -0.3px;
  }

  &__label {
    font-size: 13px;
    color: var(--text-secondary);
  }
}

/* ---- 紧凑模型卡片 ---- */
.model-compact {
  background: #fff;
  border-radius: 10px;
  padding: 16px 20px;
  border: 1px solid var(--border-color);

  &--empty {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 60px;
  }
}

.model-compact__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-compact__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.model-compact__metrics {
  display: flex;
  gap: 16px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-secondary);

  strong {
    color: var(--text-primary);
    font-weight: 600;
    margin-left: 3px;
  }
}

/* ============ 第二列：记录列表 ============ */
.records-list {
  display: flex;
  flex-direction: column;
}

.record-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-radius: 8px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    transform 0.15s ease;

  &:hover {
    background: #f8fafc;
    transform: translateX(2px);
  }

  & + & {
    border-top: 1px solid #f3f4f6;
  }
}

.record-row__left {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 0;
}

.defect-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;

  &--has {
    background: #ef4444;
  }

  &--none {
    background: #10b981;
  }
}

.record-row__time {
  font-size: 13px;
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.record-row__defects {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
}

.record-row__right {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-shrink: 0;
}

.record-row__tags {
  display: flex;
  gap: 4px;
}

.record-row__arrow {
  color: var(--text-placeholder);
  font-size: 14px;
}

.text-muted {
  color: var(--text-placeholder);
  font-size: 13px;
}
</style>
