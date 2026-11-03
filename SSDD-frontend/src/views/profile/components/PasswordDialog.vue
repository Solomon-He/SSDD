<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import type { FormRules } from 'element-plus'
import SsDialog from '@/components/SsDialog/index.vue'
import SsForm from '@/components/SsForm/index.vue'
import { useSsForm } from '@/components/SsForm/useSsForm'
import { passwordFormConfig } from './form.config'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const authStore = useAuthStore()
const loading = ref(false)

const { formModel, formProps, formRef, validate, resetFields } = useSsForm(
  passwordFormConfig,
  {
    defaultValue: { oldPassword: '', newPassword: '', confirmPassword: '' },
    rules: buildRules(),
  },
)

/** 构建含联动校验的密码规则 */
function buildRules(): FormRules {
  return {
    oldPassword: [
      { required: true, message: '请输入当前密码', trigger: 'blur' },
    ],
    newPassword: [
      { required: true, message: '请输入新密码', trigger: 'blur' },
      { min: 6, message: '密码至少 6 个字符', trigger: 'blur' },
      {
        validator: (_rule, _value, callback) => {
          if (formModel.confirmPassword) {
            formRef.value?.validateField('confirmPassword')
          }
          callback()
        },
        trigger: 'blur',
      },
    ],
    confirmPassword: [
      { required: true, message: '请再次输入新密码', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          if (value !== formModel.newPassword) {
            callback(new Error('两次输入的密码不一致'))
          } else {
            callback()
          }
        },
        trigger: 'blur',
      },
    ],
  }
}

async function submit() {
  const valid = await validate()
  if (!valid) return

  loading.value = true
  try {
    const ok = await authStore.changePassword(
      formModel.oldPassword,
      formModel.newPassword,
    )
    if (ok) {
      emit('update:visible', false)
      resetFields()
    }
  } finally {
    loading.value = false
  }
}

function handleClose() {
  emit('update:visible', false)
  resetFields()
}
</script>

<template>
  <SsDialog
    :model-value="visible"
    title="修改登录密码"
    width="420px"
    confirm-text="确认修改"
    :loading="loading"
    @update:model-value="handleClose"
    @confirm="submit"
    @cancel="handleClose"
  >
    <SsForm
      v-model="formModel"
      v-bind="formProps"
      v-model:form-ref="formRef"
    />
  </SsDialog>
</template>
