<script setup lang="ts">
import { computed, ref } from 'vue'
import { useAsyncDetection } from '@/composables/useAsyncDetection'
import UploadArea from './UploadArea.vue'
import BatchResult from './BatchResult.vue'

const state = useAsyncDetection()
const { task, taskId, submitting, polling, uploadProgress, error } = state
const upload = ref<InstanceType<typeof UploadArea> | null>(null)
const labels = { queued: '排队中', running: '处理中', retry_wait: '等待重试', succeeded: '已完成', failed: '失败' }
const reasons: Record<string, string> = {
  INPUT_MISSING: '输入文件缺失，请重新提交图片。', INPUT_CHANGED: '输入校验未通过，请重新提交图片。',
  TASK_EXPIRED: '任务已超出有效期，请重新提交。', USER_DISABLED: '账号已停用，请联系管理员。',
  RETRY_EXHAUSTED: '已达到重试上限，请稍后新建任务。',
  INPUT_OR_MODEL_INVALID: '图片或原模型不可用，请检查后重新提交。',
  NATIVE_TIMEOUT: '推理超时，可减少图片数量后重新提交。', ATTEMPT_TIMEOUT: '处理超时，可减少图片数量后重新提交。',
}
const reason = computed(() => reasons[task.value?.error_code ?? ''] ?? '处理未完成，请稍后新建任务或联系管理员。')
const active = computed(() => task.value && !['succeeded', 'failed'].includes(task.value.status))
function reset() { state.reset();upload.value?.clearFiles() }
</script>

<template>
  <div class="async-panel">
    <ElAlert type="info" :closable="false" title="提交后可离开页面，请保留任务号以恢复查询。暂停查询不会取消后台处理；结果图片链接过期时也可恢复查询刷新。" />
    <div class="task-recovery">
      <ElInput v-model="taskId" placeholder="任务号（最近一次任务号会在本账号保留）" :disabled="submitting" />
      <ElButton :disabled="submitting || !taskId" @click="state.load()">恢复查询</ElButton>
      <ElButton v-if="polling" @click="state.stop()">暂停查询</ElButton>
      <ElButton :disabled="submitting" @click="reset">新建任务</ElButton>
    </div>
    <ElAlert v-if="error" type="error" :closable="false" :title="error" />
    <UploadArea v-if="!task || !active" ref="upload" batch :loading="submitting" :upload-progress="uploadProgress" button-label="提交异步任务" loading-label="提交中..." @detect="state.submit" />
    <div v-if="task" class="task-state">
      <ElTag :type="task.status === 'failed' ? 'danger' : task.status === 'succeeded' ? 'success' : 'primary'">{{ labels[task.status] }}</ElTag>
      <span>{{ task.model.name }} {{ task.model.version }} · {{ task.actual_device.toUpperCase() }}</span>
      <span>尝试 {{ task.attempts }} / {{ task.max_attempts }}</span>
      <p>本次已处理 {{ task.completed_images }} / {{ task.image_count }} 张</p>
      <ElProgress :percentage="Math.round(task.progress_percent)" :status="task.status === 'failed' ? 'exception' : task.status === 'succeeded' ? 'success' : undefined" />
      <p v-if="task.status === 'retry_wait'">本次未发布结果，系统将按计划重试。</p>
      <p v-if="task.status === 'failed'">任务失败，未发布本批检测结果。{{ reason }}</p>
      <p v-if="task.fallback_reason">提交时已回退至 CPU，本任务继续使用该设备。</p>
    </div>
    <BatchResult v-if="task?.status === 'succeeded' && task.result" :results="task.result.results" />
  </div>
</template>

<style scoped>
.async-panel { display: flex; flex-direction: column; gap: 16px; }
.task-recovery { display: flex; gap: 8px; flex-wrap: wrap; }
.task-recovery .el-input { flex: 1; min-width: 260px; }
.task-state { padding: 16px; border: 1px solid var(--border-color); border-radius: 8px; }
.task-state span { margin-right: 12px; }
</style>
