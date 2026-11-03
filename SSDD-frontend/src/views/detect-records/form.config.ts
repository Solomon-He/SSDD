import type { SsSearchItemConfig } from '@/components/SsSearch'

interface SearchConfigOptions {
  isAdmin: boolean
}

export function getSearchConfig({ isAdmin }: SearchConfigOptions): SsSearchItemConfig[] {
  return [
    {
      prop: 'search',
      label: '用户搜索',
      compProps: { placeholder: '按用户名搜索…', clearable: true, style: 'width: 280px' },
      isHide: !isAdmin,
    },
  ]
}
