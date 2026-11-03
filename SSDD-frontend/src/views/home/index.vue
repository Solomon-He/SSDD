<script setup lang="ts">
import { onMounted, defineAsyncComponent } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useHomeData } from '@/composables/useHomeData'
import HomeHero from './components/HomeHero.vue'
import AdminStats from './components/AdminStats.vue'
import HomeOverview from './components/HomeOverview.vue'

/** 懒加载 ECharts 图表组件，减少首屏体积 */
const HomeCharts = defineAsyncComponent(() => import('./components/HomeCharts.vue'))

const auth = useAuthStore()
const {
  loading,
  myRecords,
  myTotal,
  chartRecords,
  adminStats,
  activeModel,
  avgDefectCount,
  avgInferenceTime,
} = useHomeData()

onMounted(() => {
  // composable 内部有 TTL 防抖，重复进入不会重复请求
  useHomeData().refresh()
})
</script>

<template>
  <div class="home-page" v-loading="loading">
    <HomeHero :total-records="myTotal" :active-model="activeModel" />

    <AdminStats
      v-if="auth.isAdmin && adminStats"
      :stats="adminStats"
      class="home-section"
    />

    <HomeOverview
      :total-records="myTotal"
      :records="myRecords"
      :active-model="activeModel"
      :avg-defect-count="avgDefectCount()"
      :avg-inference-time="avgInferenceTime()"
      class="home-section"
    />

    <HomeCharts
      v-if="chartRecords.length"
      :records="chartRecords"
      class="home-section"
    />
  </div>
</template>

<style scoped lang="scss">
@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.home-page {
  min-height: 100%;
}

.home-section {
  margin-top: var(--spacing-lg);
  animation: fadeUp 0.45s ease both;

  @for $i from 1 through 4 {
    &:nth-of-type(#{$i}) {
      animation-delay: #{$i * 0.07}s;
    }
  }
}
</style>
