<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  /** 图片 URL */
  src: string
  /** alt 文字 */
  alt?: string
  /** 预览时的标题 */
  title?: string
}>()

const visible = ref(false)
</script>

<template>
  <div class="image-preview-trigger" @click="visible = true">
    <img :src="src" :alt="alt || ''" class="trigger-image" />
    <div class="zoom-overlay">
      <ElIcon :size="20"><ZoomIn /></ElIcon>
    </div>
  </div>

  <ElDialog v-model="visible" :title="title || '图片预览'" width="80%" align-center destroy-on-close>
    <div class="preview-body">
      <img :src="src" :alt="alt || ''" class="preview-image" />
    </div>
  </ElDialog>
</template>

<style scoped lang="scss">
.image-preview-trigger {
  position: relative;
  cursor: pointer;
  overflow: hidden;
  border-radius: var(--border-radius);
  border: 1px solid var(--border-color);
  line-height: 0;

  &:hover .zoom-overlay {
    opacity: 1;
  }

  .trigger-image {
    width: 100%;
    height: 100%;
    object-fit: contain;
    display: block;
  }

  .zoom-overlay {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: rgba(0, 0, 0, 0.3);
    opacity: 0;
    transition: opacity 0.2s ease;
    color: #fff;
  }
}

.preview-body {
  display: flex;
  justify-content: center;
  max-height: 75vh;
  overflow: auto;

  .preview-image {
    max-width: 100%;
    max-height: 75vh;
    object-fit: contain;
  }
}
</style>
