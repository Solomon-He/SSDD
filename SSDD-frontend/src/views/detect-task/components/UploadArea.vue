<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const props = withDefaults(
  defineProps<{
    /** 是否为批量模式 */
    batch?: boolean
    /** 是否正在检测 */
    loading?: boolean
    /** 批量模式下最大文件数，默认 20 */
    maxFiles?: number
    /** 上传进度（0-100），传入时展示进度条 */
    uploadProgress?: number | null
  }>(),
  {
    maxFiles: 20,
    uploadProgress: null,
  },
)

const emit = defineEmits<{
  (e: 'detect', files: File[]): void
}>()

const fileList = ref<File[]>([])
const dragOver = ref(false)
const fileInput = ref<HTMLInputElement>()

const accept = 'image/jpeg,image/png,image/bmp'
/** 单个文件大小上限：10 MB */
const maxFileSize = 10 * 1024 * 1024

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length) return
  addFiles(Array.from(input.files))
  input.value = ''
}

function handleDrop(e: DragEvent) {
  dragOver.value = false
  const files = Array.from(e.dataTransfer?.files || []).filter((f) => f.type.match(/^image\/(jpeg|png|bmp)$/))
  if (files.length) addFiles(files)
}

function addFiles(files: File[]) {
  // 过滤超出大小限制的文件并提示用户
  const valid = files.filter((f) => {
    if (f.size > maxFileSize) {
      ElMessage.warning(`"${f.name}" 超过 10 MB 限制，已跳过`)
      return false
    }
    return true
  })
  if (!valid.length) return

  if (props.batch) {
    const remaining = props.maxFiles - fileList.value.length
    fileList.value.push(...valid.slice(0, remaining))
  } else if (valid[0]) {
    fileList.value = [valid[0]]
  }
}

function removeFile(index: number) {
  fileList.value.splice(index, 1)
}

function clearFiles() {
  fileList.value = []
}

function startDetect() {
  if (!fileList.value.length) return
  emit('detect', [...fileList.value])
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

defineExpose({ clearFiles })
</script>

<template>
  <div class="upload-area">
    <!-- 拖拽上传区域 -->
    <div
      class="drop-zone"
      :class="{ 'is-dragover': dragOver }"
      @dragover.prevent="dragOver = true"
      @dragleave.prevent="dragOver = false"
      @drop.prevent="handleDrop"
      @click="fileInput?.click()"
    >
      <input
        ref="fileInput"
        type="file"
        :accept="accept"
        :multiple="batch"
        class="file-input"
        @change="handleFileChange"
      />
      <ElIcon :size="40" color="var(--text-placeholder)"><UploadFilled /></ElIcon>
      <p class="drop-text">将图片拖拽到此处，或 <em>点击选择</em></p>
      <p class="drop-hint">
        支持 JPG / PNG / BMP 格式
        <template v-if="batch">，最多 {{ props.maxFiles }} 张</template>
      </p>
    </div>

    <!-- 已选文件列表 -->
    <div v-if="fileList.length" class="file-list">
      <div class="file-list-header">
        <span>已选文件（{{ fileList.length }}{{ batch ? ` / ${props.maxFiles}` : '' }}）</span>
        <ElButton text size="small" type="danger" @click="clearFiles">清空</ElButton>
      </div>
      <div class="file-items">
        <div v-for="(file, idx) in fileList" :key="idx" class="file-item">
          <ElIcon :size="16" color="var(--color-primary)"><Picture /></ElIcon>
          <span class="file-name" :title="file.name">{{ file.name }}</span>
          <span class="file-size">{{ formatSize(file.size) }}</span>
          <ElButton text size="small" circle @click.stop="removeFile(idx)">
            <ElIcon :size="14"><Close /></ElIcon>
          </ElButton>
        </div>
      </div>

      <!-- 批量上传进度条：仅在上传过程中展示 -->
      <ElProgress
        v-if="uploadProgress != null && loading"
        :percentage="uploadProgress"
        :stroke-width="8"
        :show-text="true"
        status=""
        class="upload-progress"
      />

      <!-- 开始检测按钮 -->
      <ElButton
        type="primary"
        size="large"
        :loading="loading"
        :disabled="!fileList.length"
        class="detect-btn"
        @click="startDetect"
      >
        <ElIcon v-if="!loading"><Cpu /></ElIcon>
        {{ loading ? '检测中...' : '开始检测' }}
      </ElButton>
    </div>
  </div>
</template>

<style scoped lang="scss">
.upload-area {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.drop-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-xl) var(--spacing-lg);
  border: 2px dashed var(--border-color);
  border-radius: var(--border-radius);
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover,
  &.is-dragover {
    border-color: var(--color-primary);
    background-color: rgba(64, 158, 255, 0.04);
  }

  .file-input {
    display: none;
  }

  .drop-text {
    font-size: 14px;
    color: var(--text-secondary);

    em {
      color: var(--color-primary);
      font-style: normal;
      cursor: pointer;
    }
  }

  .drop-hint {
    font-size: 12px;
    color: var(--text-placeholder);
  }
}

.file-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);

  .file-list-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 13px;
    color: var(--text-secondary);
    font-weight: 500;
  }

  .file-items {
    display: flex;
    flex-direction: column;
    gap: 4px;
    max-height: 200px;
    overflow-y: auto;
  }

  .file-item {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
    padding: 6px 10px;
    border-radius: var(--border-radius);
    background-color: #f9fafb;
    font-size: 13px;

    .file-name {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: var(--text-primary);
    }

    .file-size {
      color: var(--text-placeholder);
      font-size: 12px;
      flex-shrink: 0;
    }
  }
}

.detect-btn {
  width: 100%;
  margin-top: var(--spacing-sm);
}
</style>
