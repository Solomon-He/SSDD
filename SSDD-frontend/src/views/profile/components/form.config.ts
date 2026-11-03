import type { SsFormItemConfig } from '@/components/SsForm'
import SsInput from '@/components/SsBase/SsInput/index.vue'

export const passwordFormConfig: SsFormItemConfig[] = [
  {
    prop: 'oldPassword',
    label: '当前密码',
    comp: SsInput,
    compProps: { type: 'password', placeholder: '请输入当前密码', showPassword: true },
    rules: true,
  },
  {
    prop: 'newPassword',
    label: '新密码',
    comp: SsInput,
    compProps: { type: 'password', placeholder: '设置新密码（至少 6 位）', showPassword: true },
    // 规则含联动校验，在组件中动态补充
  },
  {
    prop: 'confirmPassword',
    label: '确认密码',
    comp: SsInput,
    compProps: { type: 'password', placeholder: '请再次输入新密码', showPassword: true },
  },
]
