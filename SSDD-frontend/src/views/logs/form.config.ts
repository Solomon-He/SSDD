import { LOG_ACTIONS } from '@/constants/logActions'
import SsSelect from '@/components/SsBase/SsSelect/index.vue'
import SsDatePicker from '@/components/SsBase/SsDatePicker/index.vue'
import type { SsSearchItemConfig } from '@/components/SsSearch'

interface SearchConfigOptions {
  isAdmin: boolean
}

export function getSearchConfig({ isAdmin }: SearchConfigOptions): SsSearchItemConfig[] {
  return [
    {
      prop: 'search',
      label: '用户搜索',
      compProps: { placeholder: '搜索用户名称或邮箱', clearable: true, style: 'width: 220px' },
      isHide: !isAdmin,
    },
    {
      prop: 'action',
      label: '操作类型',
      comp: SsSelect,
      compProps: {
        placeholder: '全部操作',
        clearable: true,
        style: 'width: 160px',
        options: LOG_ACTIONS.map((a) => ({ label: a.label, value: a.value })),
      },
    },
    {
      prop: 'role',
      label: '角色',
      comp: SsSelect,
      compProps: {
        placeholder: '全部角色',
        clearable: true,
        style: 'width: 130px',
        options: [
          { label: '管理员', value: 'admin' },
          { label: '质检员', value: 'inspector' },
        ],
      },
      isHide: !isAdmin,
    },
    {
      prop: 'dateRange',
      label: '日期范围',
      comp: SsDatePicker,
      compProps: {
        type: 'daterange',
        rangeSeparator: '至',
        startPlaceholder: '开始日期',
        endPlaceholder: '结束日期',
        valueFormat: 'YYYY-MM-DDTHH:mm:ssZ',
        style: 'width: 280px',
      },
    },
  ]
}
