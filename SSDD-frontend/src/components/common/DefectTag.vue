<script setup lang="ts">
import { computed } from 'vue'
import { getDefectLabel, getDefectColor } from '@/constants/defects'

const props = defineProps<{
  /** 缺陷类别索引 (1-4) */
  classIndex: number
}>()

const label = computed(() => getDefectLabel(props.classIndex))
const color = computed(() => getDefectColor(props.classIndex))
</script>

<template>
  <span class="defect-tag" :style="{ '--tag-color': color }">
    {{ label }}
  </span>
</template>

<style scoped lang="scss">
.defect-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.5;
  border-radius: 999px;
  color: var(--tag-color);
  background-color: color-mix(in srgb, var(--tag-color) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--tag-color) 25%, transparent);
  white-space: nowrap;
}
</style>
