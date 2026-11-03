import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/components/layout/AppLayout.vue'

/** 路由 meta 类型扩展 */
declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiredRole?: 'admin' | 'inspector'
    title?: string
  }
}

const routes: RouteRecordRaw[] = [
  // ---- 公开页面（独立布局） ----
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/register/index.vue'),
    meta: { title: '注册' },
  },

  // ---- 需要认证的页面（使用 AppLayout 布局） ----
  {
    path: '/',
    component: AppLayout,
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/home/index.vue'),
        meta: { requiresAuth: true, title: '首页' },
      },
      {
        path: 'detect',
        name: 'detect',
        component: () => import('@/views/detect-task/index.vue'),
        meta: { requiresAuth: true, title: '缺陷检测' },
      },
      {
        path: 'records',
        name: 'records',
        component: () => import('@/views/detect-records/index.vue'),
        meta: { requiresAuth: true, title: '检测历史' },
      },
      {
        path: 'records/:id',
        name: 'record-detail',
        component: () => import('@/views/detect-records/detail.vue'),
        meta: { requiresAuth: true, title: '检测详情' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { requiresAuth: true, title: '个人信息' },
      },
      {
        path: 'logs',
        name: 'logs',
        component: () => import('@/views/logs/index.vue'),
        meta: { requiresAuth: true, title: '操作日志' },
      },
      // ---- 管理员页面 ----
      {
        path: 'admin/users',
        name: 'admin-users',
        component: () => import('@/views/users/index.vue'),
        meta: { requiresAuth: true, requiredRole: 'admin', title: '用户管理' },
      },
      {
        path: 'admin/models',
        name: 'admin-models',
        component: () => import('@/views/models/index.vue'),
        meta: { requiresAuth: true, requiredRole: 'admin', title: '模型管理' },
      },
    ],
  },

  // ---- 404 ----
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/not-found/index.vue'),
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

/** 全局前置守卫 */
router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const isPublicPage = to.name === 'login' || to.name === 'register'

  // 如果有 Token 但没有用户信息（页面刷新场景），先恢复用户数据
  if (authStore.isLoggedIn && !authStore.user) {
    await authStore.fetchCurrentUser()
  }

  // 1. 公开页面：已登录则重定向到首页
  if (isPublicPage) {
    if (authStore.isLoggedIn) {
      return { name: 'home' }
    }
    return true
  }

  // 2. 需要认证的页面
  if (to.meta.requiresAuth) {
    // 未登录 → 跳转登录页
    if (!authStore.isLoggedIn) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }

    // 需要特定角色但当前用户无权限 → 重定向首页
    if (to.meta.requiredRole === 'admin' && !authStore.isAdmin) {
      return { name: 'home' }
    }
  }

  return true
})

/** 设置页面标题 */
router.afterEach((to) => {
  const baseTitle = '钢铁表面缺陷检测系统'
  document.title = to.meta.title ? `${to.meta.title} - ${baseTitle}` : baseTitle
})

export default router
