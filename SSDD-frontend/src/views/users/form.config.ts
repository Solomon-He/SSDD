import SsSelect from '@/components/SsBase/SsSelect/index.vue'
import type { SsSearchItemConfig } from '@/components/SsSearch'

export const searchConfig: SsSearchItemConfig[] = [
  {
    prop: 'search',
    label: '用户搜索',
    compProps: { placeholder: '搜索用户名称或邮箱', clearable: true, style: 'width: 260px' },
  },
  {
    prop: 'role',
    label: '角色',
    comp: SsSelect,
    compProps: {
      placeholder: '全部角色',
      clearable: true,
      style: 'width: 140px',
      options: [
        { label: '管理员', value: 'admin' },
        { label: '质检员', value: 'inspector' },
      ],
    },
  },
]
