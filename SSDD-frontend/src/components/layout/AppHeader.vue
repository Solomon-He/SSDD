<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { Fold, Expand, User, SwitchButton, Setting } from '@element-plus/icons-vue'

defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  toggleSidebar: []
}>()

const authStore = useAuthStore()
const router = useRouter()

function goProfile() {
  router.push('/profile')
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__left">
      <el-icon class="app-header__toggle" @click="emit('toggleSidebar')">
        <Fold v-if="!collapsed" />
        <Expand v-else />
      </el-icon>
      <h1 class="app-header__title">钢铁表面缺陷检测系统</h1>
    </div>
    <div class="app-header__right">
      <el-dropdown trigger="click">
        <span class="app-header__user">
          <el-avatar :size="32" :src="authStore.avatarUrl || undefined">
            <el-icon><User /></el-icon>
          </el-avatar>
          <span class="app-header__username">{{ authStore.displayName || '用户' }}</span>
          <el-icon class="el-icon--right"><arrow-down /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="goProfile">
              <el-icon><Setting /></el-icon>
              个人中心
            </el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script lang="ts">
import { ArrowDown } from '@element-plus/icons-vue'
export default {
  components: { ArrowDown },
}
</script>

<style scoped lang="scss">
.app-header {
  height: 60px; /* 更加精巧的顶部高度 */
  background-color: #F6F8FA;
  border-bottom: 1px solid #e5e7eb; /* 细致的底边框替代大阴影 */
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  z-index: 100;
  flex-shrink: 0;
}

.app-header__left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.app-header__toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  cursor: pointer;
  color: #6b7280;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.app-header__toggle:hover {
  color: #111827;
  background-color: #f3f4f6;
}

.app-header__title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  white-space: nowrap;
  letter-spacing: 0.5px;
}

.app-header__right {
  display: flex;
  align-items: center;
}

.app-header__user {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  color: #4b5563;
  padding: 4px 12px 4px 4px;
  border-radius: 32px; /* 胶囊态背景 */
  transition: all 0.2s ease;
  border: 1px solid transparent;
}

.app-header__user:hover {
  background-color: #f9fafb;
  border-color: #e5e7eb;
  color: #111827;
}

.app-header__username {
  font-size: 14px;
  font-weight: 500;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
