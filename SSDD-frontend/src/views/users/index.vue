<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getUsers, updateUserRole } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'
import { useImageUrl } from '@/composables/useImageUrl'
import { SsSearch, useSsSearch } from '@/components/SsSearch'
import { SsTable, useSsTable } from '@/components/SsTable'
import { SsPagination, useSsPagination } from '@/components/SsPagination'
import { getTableConfig } from './table.config'
import { searchConfig } from './form.config'
import type { AdminUser } from '@/types/admin'

const authStore = useAuthStore()
const { toFullUrl } = useImageUrl()

const users = ref<AdminUser[]>([])
const roleChangingId = ref<string | null>(null)

// ---- SsSearch 配置 ----
const { searchProps, searchModel, getSearchParams, handleSearch, handleReset } = useSsSearch(
  searchConfig,
  {
    onSearch: () => {
      reset()
      fetchUsers()
    },
    onReset: () => {
      reset()
      fetchUsers()
    },
  },
)

// ---- SsPagination 配置 ----
const {
  current,
  pageSize,
  total,
  loading,
  paginationProps,
  reset,
} = useSsPagination({ onChange: () => fetchUsers() })

/** 拉取用户列表 */
async function fetchUsers() {
  loading.value = true
  try {
    const params = getSearchParams()
    const { data: res } = await getUsers({
      page: current.value,
      pageSize: pageSize.value,
      search: params.search || undefined,
      role: (params.role as 'admin' | 'inspector') || undefined,
    })
    if (res.success && res.data) {
      users.value = res.data.items
      total.value = res.data.total
    }
  } finally {
    loading.value = false
  }
}

/** 修改用户角色 */
async function handleRoleChange(user: AdminUser, newRole: 'admin' | 'inspector') {
  if (newRole === user.role) return
  const oldRole = user.role
  const roleName = newRole === 'admin' ? '管理员' : '质检员'

  try {
    await ElMessageBox.confirm(
      `确认将用户「${user.display_name}」的角色从「${oldRole === 'admin' ? '管理员' : '质检员'}」变更为「${roleName}」？`,
      '修改用户角色',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return // 用户取消
  }

  roleChangingId.value = user.id
  try {
    const { data: res } = await updateUserRole(user.id, { role: newRole })
    if (res.success) {
      ElMessage.success(res.message || '角色修改成功')
      fetchUsers()
    } else {
      ElMessage.error(res.error || '角色修改失败')
    }
  } finally {
    roleChangingId.value = null
  }
}

/** 是否为当前登录用户自身 */
function isSelf(user: AdminUser) {
  return user.user_id === authStore.user?.id
}

// ---- SsTable 配置 ----
const columns = getTableConfig({
  toFullUrl,
  isSelf,
  isChangingRole: (user: AdminUser) => roleChangingId.value === user.id,
  onRoleChange: handleRoleChange,
})
const { tableConfig, tableInstance } = useSsTable(columns, {
  tableAttrs: { stripe: true, height: '100%', flexible: true },
})

onMounted(fetchUsers)
</script>

<template>
  <div class="users-page">
    <!-- Header -->
    <div class="page-header">
      <div>
        <h2 class="page-title">用户管理</h2>
        <p class="page-desc">查看系统用户、搜索并管理角色权限</p>
      </div>
    </div>

    <!-- Filter Bar -->
    <SsSearch
      v-model="searchModel"
      v-bind="searchProps"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- User Table -->
    <SsTable
      v-model="users"
      v-model:table-instance="tableInstance"
      v-bind="tableConfig"
      v-loading="loading"
    />

    <!-- Pagination -->
    <SsPagination v-bind="paginationProps" />
  </div>
</template>

<style scoped lang="scss">
.users-page {
  height: 100%;
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  overflow: hidden;
  box-sizing: border-box;

  .page-header,
  :deep(.ss-search),
  :deep(.ss-pagination) {
    flex-shrink: 0;
  }

  :deep(.ss-table) {
    flex: 1;
    min-height: 0;
  }
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;

  .page-title {
    font-size: 20px;
    font-weight: 700;
    color: var(--text-primary);
    margin: 0;
  }

  .page-desc {
    font-size: 13px;
    color: var(--text-secondary);
    margin: 4px 0 0;
  }
}
</style>
