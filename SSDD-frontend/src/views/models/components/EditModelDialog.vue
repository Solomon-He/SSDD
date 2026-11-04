<script setup lang="ts">
import { ref } from 'vue'
import type { Model, UpdateModelParams } from '@/types/model'
import { useModelsStore } from '@/stores/models'
import SsDialog from '@/components/SsDialog/index.vue'
import SsForm from '@/components/SsForm/index.vue'
import { useSsForm } from '@/components/SsForm/useSsForm'
import { editModelBaseConfig, editModelMetricConfig } from './form.config'

const modelsStore = useModelsStore()

const submitting = ref(false)
const modelId = ref('')

const { formModel: baseModel, formProps: baseProps, formRef: baseRef, validate: validateBase } = useSsForm(
  editModelBaseConfig,
  { defaultValue: { name: '', version: '', description: '' } },
)

interface MetricForm {
  miou: number | null
  precision: number | null
  recall: number | null
  dice: number | null
  fps: number | null
  file_size: number | null
  metric_source: string | null
}

const { formModel: metricModel, formProps: metricProps, formRef: metricRef, validate: validateMetric } = useSsForm<MetricForm>(
  editModelMetricConfig,
  {
    rowCount: 2,
    defaultValue: { miou: null, precision: null, recall: null, dice: null, fps: null, file_size: null, metric_source: null },
  },
)

function open(model: Model) {
  modelId.value = model.id
  baseModel.name = model.name
  baseModel.version = model.version
  baseModel.description = model.description ?? ''
  metricModel.miou = model.miou
  metricModel.precision = model.precision
  metricModel.recall = model.recall
  metricModel.dice = model.dice
  metricModel.fps = model.fps
  metricModel.file_size = model.file_size
  metricModel.metric_source = model.metric_source
  baseRef.value?.clearValidate()
  metricRef.value?.clearValidate()
}

async function handleSubmit() {
  const [v1, v2] = await Promise.all([validateBase(), validateMetric()])
  if (!v1 || !v2) return

  const params: UpdateModelParams = {
    name: baseModel.name,
    version: baseModel.version || undefined,
    description: baseModel.description || null,
    miou: metricModel.miou,
    precision: metricModel.precision,
    recall: metricModel.recall,
    dice: metricModel.dice,
    fps: metricModel.fps,
    metric_source: metricModel.metric_source || null,
  }

  submitting.value = true
  const ok = await modelsStore.handleUpdateModel(modelId.value, params)
  submitting.value = false

  if (ok) {
    visible.value = false
  }
}

const visible = ref(false)

defineExpose({ open: (model: Model) => { visible.value = true; open(model) } })
</script>

<template>
  <SsDialog
    v-model="visible"
    title="编辑模型信息"
    width="560px"
    :loading="submitting"
    :confirm-text="submitting ? '保存中...' : '保存'"
    @confirm="handleSubmit"
    @cancel="visible = false"
  >
    <SsForm
      v-model="baseModel"
      v-bind="baseProps"
      v-model:form-ref="baseRef"
    />

    <ElDivider content-position="left">性能指标</ElDivider>

    <SsForm
      v-model="metricModel"
      v-bind="metricProps"
      v-model:form-ref="metricRef"
    />
  </SsDialog>
</template>

<style scoped lang="scss">
.metric-input {
  width: 100%;
}
</style>
