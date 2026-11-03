<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  HomeFilled,
  CameraFilled,
  Document,
  UserFilled,
  Setting,
  Notebook,
  User,
} from '@element-plus/icons-vue'

defineProps<{
  collapsed: boolean
}>()

const route = useRoute()
const authStore = useAuthStore()

/** 菜单项定义 */
interface MenuItem {
  index: string
  title: string
  icon: typeof HomeFilled
  adminOnly?: boolean
}

const menuItems = computed<MenuItem[]>(() => {
  const items: MenuItem[] = [
    { index: '/', title: '首页', icon: HomeFilled },
    { index: '/detect', title: '缺陷检测', icon: CameraFilled },
    { index: '/records', title: '检测历史', icon: Document },
  ]


  if (authStore.isAdmin) {
    items.push(
      { index: '/admin/models', title: '模型管理', icon: Setting, adminOnly: true },
      { index: '/admin/users', title: '用户管理', icon: UserFilled, adminOnly: true },
    )
  }

  items.push({ index: '/logs', title: '操作日志', icon: Notebook })
  items.push({ index: '/profile', title: '个人中心', icon: User })

  return items
})

/** 当前激活的菜单项 */
const activeMenu = computed(() => {
  const path = route.path
  // 检测详情页高亮 "检测历史"
  if (path.startsWith('/records/')) return '/records'
  return path
})
</script>

<template>
  <aside class="app-sidebar" :class="{ 'app-sidebar--collapsed': collapsed }">
    <el-menu
      :default-active="activeMenu"
      :collapse="collapsed"
      :collapse-transition="true"
      router
      class="app-sidebar__menu"
    >
      <el-menu-item v-for="item in menuItems" :key="item.index" :index="item.index">
        <el-icon><component :is="item.icon" /></el-icon>
        <template #title>
          <div class="menu-item-title-wrapper">
            <span>{{ item.title }}</span>
            <el-tag v-if="item.adminOnly" size="small" type="danger" class="admin-tag">管理</el-tag>
          </div>
        </template>
      </el-menu-item>
    </el-menu>
  </aside>
</template>

<style scoped lang="scss">
.app-sidebar {
  width: var(--sidebar-width);
  background-color: #ffffff; /* 纯白底色 */
  border-right: 1px solid #e5e7eb; /* 细腻边框 */
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow-x: hidden;
  overflow-y: auto;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.app-sidebar--collapsed {
  width: var(--sidebar-collapsed-width);
}

.app-sidebar__menu {
  height: 100%;
  border-right: none;
  background-color: transparent;
  padding: 12px 0; /* 增加上下内边距 */
}

.app-sidebar__menu:not(.el-menu--collapse) {
  width: var(--sidebar-width);
}

/* 覆盖 Element Plus 菜单样式，打造现代 Github/Supabase 控制台风格 */
:deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  margin: 4px 12px;
  border-radius: 6px;
  color: #4b5563; /* 默认灰黑色 */
  font-weight: 500;
  font-size: 14px;
  transition: all 0.2s ease;

  .el-icon {
    font-size: 18px;
    color: #6b7280;
    transition: color 0.2s ease;
  }

  &:hover {
    background-color: #f3f4f6; /* hover 浅灰底 */
    color: #111827;

    .el-icon {
      color: #111827;
    }
  }

  &.is-active {
    background-color: #f0f7ff; /* 激活态极浅蓝底 */
    color: #0969da; /* Github风格蓝 */
    font-weight: 600;

    .el-icon {
      color: #0969da;
    }
  }
}

/* 收起时的特殊处理，重置外边距 */
.app-sidebar--collapsed :deep(.el-menu-item) {
  margin: 4px 8px;
  padding: 0 !important;
  justify-content: center;

  .el-tooltip__trigger {
    justify-content: center;
  }
}

.menu-item-title-wrapper {
  display: flex;
  align-items: center;
  width: 100%;
}

.admin-tag {
  margin-left: 8px; /* 推到右侧边缘 */
  border-radius: 4px;
  border: 1px solid #fca5a5;
  background-color: #fef2f2;
  color: #ef4444;
  font-weight: 600;
  padding: 0 6px;
  height: 20px;
  line-height: 18px;
  font-size: 12px;
}
</style>
