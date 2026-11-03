<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { CameraFilled } from '@element-plus/icons-vue'
import type { Model } from '@/types/model'
import dayjs from 'dayjs'

defineProps<{
  totalRecords: number
  activeModel: Model | null
}>()

const router = useRouter()
const auth = useAuthStore()

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayStr = computed(() => dayjs().format('YYYY年M月D日'))
</script>

<template>
  <section class="home-hero">
    <div class="home-hero__main">
      <div class="home-hero__text">
        <h1 class="home-hero__greeting">{{ greeting }}，{{ auth.displayName || '用户' }}</h1>
        <p class="home-hero__subtitle">
          {{ todayStr }} · 已累计完成
          <strong>{{ totalRecords.toLocaleString() }}</strong> 次检测
        </p>
      </div>
      <div class="home-hero__actions">
        <el-button type="primary" size="large" @click="router.push('/detect')">
          <el-icon><CameraFilled /></el-icon>
          开始检测
        </el-button>
        <el-button size="large" @click="router.push('/records')">查看全部记录</el-button>
      </div>
    </div>
    <div class="home-hero__status">
      <span class="status-dot" :class="activeModel ? 'status-dot--active' : 'status-dot--warn'" />
      <span>
        {{
          activeModel
            ? `模型就绪：${activeModel.name} v${activeModel.version}`
            : '暂无活跃模型，请先激活模型'
        }}
      </span>
    </div>
  </section>
</template>

<style scoped lang="scss">
@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.45);
  }
  70% {
    box-shadow: 0 0 0 6px rgba(16, 185, 129, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0);
  }
}

.home-hero {
  background:
    repeating-linear-gradient(
      -45deg,
      transparent,
      transparent 8px,
      rgba(0, 0, 0, 0.014) 8px,
      rgba(0, 0, 0, 0.014) 9px
    ),
    linear-gradient(135deg, #f8fafc 0%, #eef2f7 100%);
  border-radius: 12px;
  padding: 28px 32px;
  border: 1px solid var(--border-color);
}

.home-hero__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}

.home-hero__greeting {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.3px;
  line-height: 1.3;
}

.home-hero__subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin-top: 6px;

  strong {
    color: var(--color-primary);
    font-weight: 600;
  }
}

.home-hero__actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.home-hero__status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
  font-size: 13px;
  color: var(--text-secondary);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &--active {
    background: #10b981;
    animation: pulse 2s infinite;
  }

  &--warn {
    background: #f59e0b;
  }
}
</style>
