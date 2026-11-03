import { ElAvatar, ElTag, ElTooltip, ElButton } from 'element-plus'
import { formatDateTime } from '@/utils/format'
import type { SsTableColumnConfig } from '@/components/SsTable'
import type { AdminUser } from '@/types/admin'

/**
 * getTableConfig 的参数类型
 *
 * 将页面级的回调和工具方法注入列配置，保持 config 文件自身无副作用。
 */
interface TableConfigOptions {
  /** 将相对路径转为完整 URL（头像） */
  toFullUrl: (path: string) => string
  /** 判断是否为当前登录用户 */
  isSelf: (user: AdminUser) => boolean
  /** 判断该用户是否正在变更角色中（loading） */
  isChangingRole: (user: AdminUser) => boolean
  /** 修改角色回调 */
  onRoleChange: (user: AdminUser, newRole: 'admin' | 'inspector') => void
}

/**
 * 获取用户管理表格的列配置
 *
 * 使用 getTableConfig 工厂函数而非直接导出常量，
 * 便于接收外部回调来配置操作列等动态内容。
 */
export function getTableConfig(options: TableConfigOptions): SsTableColumnConfig[] {
  const { toFullUrl, isSelf, isChangingRole, onRoleChange } = options

  return [
    {
      prop: 'user',
      label: '用户',
      minWidth: 220,
      formatter: (row: AdminUser) => (
        <div class="flex items-center gap-3">
          <ElAvatar size={36} src={row.avatar_url ? toFullUrl(row.avatar_url) : undefined}>
            {row.display_name?.charAt(0)}
          </ElAvatar>
          <div class="flex flex-col gap-1.5 min-w-0">
            <span class="font-semibold text-sm text-(--text-primary) truncate">
              {row.display_name}
            </span>
            <span class="text-xs text-(--text-secondary) truncate">{row.email}</span>
          </div>
        </div>
      ),
    },
    {
      prop: 'role',
      label: '角色',
      width: 140,
      align: 'center',
      formatter: (row: AdminUser) => (
        <ElTag type={row.role === 'admin' ? 'danger' : 'info'} size="small" effect="light">
          {row.role === 'admin' ? '管理员' : '质检员'}
        </ElTag>
      ),
    },
    {
      prop: 'created_at',
      label: '注册时间',
      width: 200,
      align: 'center',
      formatter: (row: AdminUser) => <span>{formatDateTime(row.created_at)}</span>,
    },
    {
      prop: 'action',
      label: '操作',
      width: 200,
      align: 'center',
      fixed: 'right',
      formatter: (row: AdminUser) => {
        if (isSelf(row)) {
          return (
            <ElTooltip content="不可修改自己的角色" placement="top">
              <ElButton text size="small" disabled>
                切换角色
              </ElButton>
            </ElTooltip>
          )
        }
        const changing = isChangingRole(row)
        return row.role === 'inspector' ? (
          <ElButton
            type="primary"
            text
            size="small"
            loading={changing}
            disabled={changing}
            onClick={() => onRoleChange(row, 'admin')}
          >
            设为管理员
          </ElButton>
        ) : (
          <ElButton
            type="warning"
            text
            size="small"
            loading={changing}
            disabled={changing}
            onClick={() => onRoleChange(row, 'inspector')}
          >
            设为质检员
          </ElButton>
        )
      },
    },
  ]
}
