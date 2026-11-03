<script setup lang="ts">
import { ref } from 'vue'
import type { UploadRawFile } from 'element-plus'
import { useModelsStore } from '@/stores/models'
import SsDialog from '@/components/SsDialog/index.vue'
import SsForm from '@/components/SsForm/index.vue'
import { useSsForm } from '@/components/SsForm/useSsForm'
import { editModelBaseConfig } from './form.config'
import type { SsFormItemConfig } from '@/components/SsForm'

const modelsStore = useModelsStore()

const uploading = ref(false)

/** 上传模型专用配置：文件上传 + 基础信息 */
const uploadConfig: SsFormItemConfig[] = [
  {
    prop: 'file',
    label: '模型文件 (.onnx)',
    // 使用具名插槽自定义渲染
    rules: [{ required: true, message: '请上传模型文件', trigger: 'change' }],
  },
  ...editModelBaseConfig,
]

interface UploadForm {
  file: File | null
  name: string
  version: string
  description: string
}

const { formModel, formProps, formRef, validate, clearValidate } = useSsForm<UploadForm>(
  uploadConfig,
  { defaultValue: { file: null, name: '', version: '1.0.0', description: '' } },
)

const visible = ref(false)

function open() {
  visible.value = true
  formModel.name = ''
  formModel.version = '1.0.0'
  formModel.description = ''
  formModel.file = null
  clearValidate()
}

function handleFileChange(rawFile: UploadRawFile) {
  formModel.file = rawFile
  formRef.value?.validateField('file')
  return false
}

function handleFileRemove() {
  formModel.file = null
}

async function handleSubmit() {
  const valid = await validate()
  if (!valid || !formModel.file) return

  uploading.value = true
  const ok = await modelsStore.handleUploadModel({
    model: formModel.file,
    name: formModel.name,
    version: formModel.version || undefined,
    description: formModel.description || undefined,
  })
  uploading.value = false

  if (ok) {
    visible.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <SsDialog
    v-model="visible"
    title="上传模型"
    width="520px"
    :loading="uploading"
    :confirm-text="uploading ? '上传中...' : '上传'"
    @confirm="handleSubmit"
    @cancel="visible = false"
  >
    <SsForm
      v-model="formModel"
      v-bind="formProps"
      v-model:form-ref="formRef"
    >
      <!-- 通过具名插槽 #file 自定义上传区域 -->
      <template #file>
        <ElUpload
          :auto-upload="false"
          :limit="1"
          accept=".onnx"
          :on-exceed="() => {}"
          :before-upload="handleFileChange"
          :on-change="(file: any) => handleFileChange(file.raw)"
          :on-remove="handleFileRemove"
          drag
          class="model-upload"
        >
          <ElIcon :size="40" color="var(--text-placeholder)"><UploadFilled /></ElIcon>
          <div class="el-upload__text">
            拖拽文件到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">仅支持 .onnx 格式，最大 500MB</div>
          </template>
        </ElUpload>
      </template>
    </SsForm>
  </SsDialog>
</template>

<style scoped lang="scss">
.model-upload {
  width: 100%;

  :deep(.el-upload-dragger) {
    width: 100%;
    padding: var(--spacing-lg) var(--spacing-md);
  }

  :deep(.el-upload) {
    width: 100%;
  }
}
</style>
