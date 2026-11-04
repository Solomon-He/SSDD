import { ElTag } from 'element-plus'
import { formatDateTime } from '@/utils/format'
import { getLogActionLabel, getLogActionTagType } from '@/constants/logActions'
import type { SsTableColumnConfig } from '@/components/SsTable'
import type { LogEntry } from '@/types/logs'

interface TableConfigOptions {
  isAdmin: boolean
}

/** 安全地从 details 中获取字符串值 */
function str(val: unknown): string | undefined {
  return typeof val === 'string' ? val : undefined
}

/** 安全地从 details 中获取数值 */
function num(val: unknown): number | undefined {
  return typeof val === 'number' ? val : undefined
}

/** 根据 action 类型渲染详情摘要 */
function renderDetails(d: Record<string, unknown>): string {
  if (!d || Object.keys(d).length === 0) return ''

  const parts: string[] = []
  const modelName = str(d.model_name)
  const defectCount = num(d.defect_count)
  const inferenceTime = num(d.inference_time_ms)
  const imageCount = num(d.image_count)
  const fileName = str(d.file_name)
  const email = str(d.email)
  const reason = str(d.reason)
  const targetName = str(d.target_display_name)
  const oldRole = str(d.old_role)
  const newRole = str(d.new_role)

  if (modelName) parts.push(`模型: ${modelName}`)
  if (defectCount !== undefined) parts.push(`缺陷: ${defectCount}`)
  if (inferenceTime !== undefined) parts.push(`耗时: ${inferenceTime}ms`)
  if (imageCount !== undefined) parts.push(`图片: ${imageCount}张`)
  if (fileName) parts.push(`文件: ${fileName}`)
  if (email) parts.push(`邮箱: ${email}`)
  if (reason) parts.push(`原因: ${reason}`)
  if (targetName && oldRole && newRole) parts.push(`${targetName}: ${oldRole} → ${newRole}`)

  return parts.length > 0 ? parts.join('；') : JSON.stringify(d)
}

export function getTableConfig(options: TableConfigOptions): SsTableColumnConfig[] {
  const { isAdmin } = options

  return [
    {
      prop: 'user',
      label: '操作用户',
      minWidth: 160,
      formatter: (row: LogEntry) => (
        <div class="flex flex-col gap-1">
          <span class="font-medium text-sm text-(--text-primary)">
            {row.user?.display_name ?? '—'}
          </span>
          {isAdmin && row.user?.email && (
            <span class="text-xs text-(--text-secondary)">{row.user.email}</span>
          )}
        </div>
      ),
    },
    {
      prop: 'action',
      label: '操作类型',
      width: 150,
      align: 'center',
      formatter: (row: LogEntry) => (
        <ElTag type={getLogActionTagType(row.action)} size="small" effect="light">
          {getLogActionLabel(row.action)}
        </ElTag>
      ),
    },
    {
      prop: 'details',
      label: '详情',
      minWidth: 240,
      formatter: (row: LogEntry) => {
        const text = renderDetails(row.details)
        if (!text) return <span class="text-(--text-secondary)">—</span>
        return <span class="text-xs text-(--text-secondary)">{text}</span>
      },
    },
    {
      prop: 'status',
      label: '状态',
      width: 100,
      align: 'center',
      formatter: (row: LogEntry) => (
        <ElTag type={row.status === 'success' ? 'success' : 'danger'} size="small" effect="light">
          {row.status === 'success' ? '成功' : '失败'}
        </ElTag>
      ),
    },
    {
      prop: 'ip_address',
      label: 'IP 地址',
      width: 140,
      align: 'center',
    },
    {
      prop: 'created_at',
      label: '操作时间',
      width: 190,
      align: 'center',
      formatter: (row: LogEntry) => <span>{formatDateTime(row.created_at)}</span>,
    },
  ]
}
