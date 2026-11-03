<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useDetectStore } from '@/stores/detect'
import { useModelsStore } from '@/stores/models'
import type { DetectModelInfo } from '@/types/detect'
import UploadArea from './components/UploadArea.vue'
import ModelInfoCard from './components/ModelInfoCard.vue'
import SingleResult from './components/SingleResult.vue'
import BatchResult from './components/BatchResult.vue'
import BatchVisuDialog from './components/BatchVisuDialog.vue'

const detectStore = useDetectStore()
const modelsStore = useModelsStore()

const uploadRef = ref<InstanceType<typeof UploadArea> | null>(null)
const batchVisuRef = ref<InstanceType<typeof BatchVisuDialog> | null>(null)

/** 检测模式：single / batch */
const mode = ref<'single' | 'batch'>('single')

/** 活跃模型信息（用于上传前展示，优先使用检测结果中的模型） */
const activeModelInfo = computed<DetectModelInfo | null>(() => {
  if (detectStore.modelInfo) return detectStore.modelInfo
  const am = modelsStore.activeModel
  if (am) return { id: am.id, name: am.name, version: am.version }
  return null
})

/** 切换模式时清除结果和文件 */
function switchMode(newMode: 'single' | 'batch') {
  mode.value = newMode
  detectStore.clearResults()
  uploadRef.value?.clearFiles()
}

/** 处理检测 */
async function handleDetect(files: File[]) {
  if (mode.value === 'single') {
    if (files[0]) await detectStore.runDetect(files[0])
  } else {
    await detectStore.runBatchDetect(files)
  }
}

/** 清除结果 */
function handleClear() {
  detectStore.clearResults()
  uploadRef.value?.clearFiles()
}

/** 加载模型列表（逻辑已封装至 modelsStore.fetchModels） */
onMounted(() => modelsStore.fetchModels())
</script>

<template>
  <div class="detect-task-page">
    <div class="page-header">
      <h2 class="page-title">缺陷检测</h2>
      <p class="page-desc">上传钢铁表面图片，系统将自动进行缺陷语义分割检测</p>
    </div>

    <div class="page-body">
      <!-- 左侧：上传 + 模型信息 -->
      <div class="left-panel">
        <!-- 模式切换 -->
        <div class="mode-switch">
          <ElRadioGroup
            :model-value="mode"
            @update:model-value="switchMode($event as 'single' | 'batch')"
          >
            <ElRadioButton value="single">单张检测</ElRadioButton>
            <ElRadioButton value="batch">批量检测</ElRadioButton>
          </ElRadioGroup>
        </div>

        <!-- 上传区域 -->
        <UploadArea
          ref="uploadRef"
          :batch="mode === 'batch'"
          :loading="detectStore.loading"
          :upload-progress="detectStore.uploadProgress"
          @detect="handleDetect"
        />

        <!-- 模型信息 -->
        <ModelInfoCard :model="activeModelInfo" />
      </div>

      <!-- 右侧：检测结果 -->
      <div class="right-panel">
        <template v-if="detectStore.currentResult">
          <div class="result-header">
            <h3>检测结果</h3>
            <ElButton text size="small" type="danger" @click="handleClear">清除结果</ElButton>
          </div>
          <SingleResult :result="detectStore.currentResult" />
        </template>

        <template v-else-if="detectStore.batchData?.results?.length">
          <div class="result-header">
            <h3>批量检测结果</h3>
            <div class="result-actions">
              <ElButton text size="small" type="primary" @click="batchVisuRef?.open()">
                <ElIcon><DataAnalysis /></ElIcon>
                可视化分析
              </ElButton>
              <ElButton text size="small" type="danger" @click="handleClear">清除结果</ElButton>
            </div>
          </div>
          <BatchVisuDialog ref="batchVisuRef" :results="detectStore.batchData.results" />
          <BatchResult :results="detectStore.batchData.results" />
        </template>

        <div v-else class="empty-result">
          <ElIcon :size="48" color="var(--text-placeholder)"><PictureFilled /></ElIcon>
          <p>上传图片并点击"开始检测"查看结果</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.detect-task-page {
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.page-header {
  .page-title {
    font-size: 20px;
    font-weight: 700;
    color: var(--text-primary);
    margin: 0;
  }

  .page-desc {
    font-size: 13px;
    color: var(--text-secondary);
    margin: 4px 0 0;
  }
}

.page-body {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: var(--spacing-lg);
  align-items: start;
}

.left-panel {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  position: sticky;
  top: calc(var(--header-height) + var(--spacing-lg));

  .mode-switch {
    display: flex;
  }
}

.right-panel {
  min-height: 400px;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  h3 {
    font-size: 16px;
    font-weight: 600;
    color: var(--text-primary);
    margin: 0;
  }

  .result-actions {
    display: flex;
    align-items: center;
    gap: var(--spacing-xs);
  }
}

.empty-result {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-md);
  color: var(--text-placeholder);
  font-size: 14px;
  border: 1px dashed var(--border-color);
  border-radius: var(--border-radius);
  min-height: 400px;
}
</style>
