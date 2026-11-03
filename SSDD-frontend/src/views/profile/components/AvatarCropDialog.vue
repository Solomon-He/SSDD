<script setup lang="ts">
/**
 * 头像裁剪弹窗
 * - 选择图片后弹出，提供圆形选区，可自由拖动和缩放
 * - 确认后输出裁剪后的圆形头像（PNG，带透明背景）
 */
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'

interface Props {
  /** 弹窗显隐（v-model:visible） */
  visible: boolean
  /** 待裁剪的原始图片文件 */
  file: File | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:visible': [val: boolean]
  /** 用户确认后输出裁剪后的图片文件 */
  confirm: [file: File]
}>()

/** 画布容器尺寸（视图区域） */
const STAGE_SIZE = 420
/** 输出头像尺寸（像素，最终生成的图） */
const OUTPUT_SIZE = 320
/** 选区最小直径（视图像素） */
const MIN_CROP = 60

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

/** 当前图片的 ObjectURL */
const imageUrl = ref('')
/** 加载完成的原图（自然尺寸） */
const naturalWidth = ref(0)
const naturalHeight = ref(0)
/** 图片在 stage 中的渲染尺寸与位置（contain 适配） */
const renderWidth = ref(0)
const renderHeight = ref(0)
const renderOffsetX = ref(0)
const renderOffsetY = ref(0)

/** 圆形选区状态（相对 stage 容器的坐标，圆心 + 直径） */
const cropX = ref(0)
const cropY = ref(0)
const cropSize = ref(0)

/** 处理状态 */
const processing = ref(false)

/** 当弹窗打开且 file 存在时，加载图片并初始化选区 */
watch(
  () => [props.visible, props.file] as const,
  ([visible, file]) => {
    if (visible && file) {
      loadImage(file)
    } else if (!visible) {
      releaseUrl()
    }
  },
  { immediate: true },
)

onBeforeUnmount(releaseUrl)

function releaseUrl() {
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = ''
  }
}

/** 加载图片，计算 contain 渲染尺寸，初始化裁剪框 */
function loadImage(file: File) {
  releaseUrl()
  imageUrl.value = URL.createObjectURL(file)
  const img = new Image()
  img.onload = () => {
    naturalWidth.value = img.naturalWidth
    naturalHeight.value = img.naturalHeight
    const ratio = Math.min(STAGE_SIZE / img.naturalWidth, STAGE_SIZE / img.naturalHeight)
    renderWidth.value = img.naturalWidth * ratio
    renderHeight.value = img.naturalHeight * ratio
    renderOffsetX.value = (STAGE_SIZE - renderWidth.value) / 2
    renderOffsetY.value = (STAGE_SIZE - renderHeight.value) / 2

    // 默认选区：图片可见区域内最大的居中圆
    const initSize = Math.min(renderWidth.value, renderHeight.value) * 0.8
    cropSize.value = initSize
    cropX.value = STAGE_SIZE / 2 - initSize / 2
    cropY.value = STAGE_SIZE / 2 - initSize / 2
  }
  img.onerror = () => {
    ElMessage.error('图片加载失败')
    dialogVisible.value = false
  }
  img.src = imageUrl.value
}

/** 选区可移动的边界（必须在图片渲染区域内） */
function clampPosition(x: number, y: number, size: number) {
  const minX = renderOffsetX.value
  const minY = renderOffsetY.value
  const maxX = renderOffsetX.value + renderWidth.value - size
  const maxY = renderOffsetY.value + renderHeight.value - size
  return {
    x: Math.min(Math.max(x, minX), maxX),
    y: Math.min(Math.max(y, minY), maxY),
  }
}

/** 拖动选区 */
function onDragStart(e: PointerEvent) {
  if ((e.target as HTMLElement).dataset.role === 'resize') return
  e.preventDefault()
  ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
  const startX = e.clientX
  const startY = e.clientY
  const originX = cropX.value
  const originY = cropY.value

  const move = (ev: PointerEvent) => {
    const dx = ev.clientX - startX
    const dy = ev.clientY - startY
    const next = clampPosition(originX + dx, originY + dy, cropSize.value)
    cropX.value = next.x
    cropY.value = next.y
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

/** 缩放选区（右下角手柄） */
function onResizeStart(e: PointerEvent) {
  e.stopPropagation()
  e.preventDefault()
  const startX = e.clientX
  const startY = e.clientY
  const originSize = cropSize.value
  const originCx = cropX.value
  const originCy = cropY.value

  const move = (ev: PointerEvent) => {
    const delta = Math.max(ev.clientX - startX, ev.clientY - startY)
    let nextSize = originSize + delta
    // 不能超过图片可见区域
    const maxByImage = Math.min(
      renderOffsetX.value + renderWidth.value - originCx,
      renderOffsetY.value + renderHeight.value - originCy,
    )
    nextSize = Math.min(Math.max(nextSize, MIN_CROP), maxByImage)
    cropSize.value = nextSize
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

/** 滚轮缩放 */
function onWheel(e: WheelEvent) {
  e.preventDefault()
  const factor = e.deltaY > 0 ? 0.95 : 1.05
  const cx = cropX.value + cropSize.value / 2
  const cy = cropY.value + cropSize.value / 2
  let nextSize = cropSize.value * factor
  const maxByImage = Math.min(
    Math.min(cx - renderOffsetX.value, renderOffsetX.value + renderWidth.value - cx) * 2,
    Math.min(cy - renderOffsetY.value, renderOffsetY.value + renderHeight.value - cy) * 2,
  )
  nextSize = Math.min(Math.max(nextSize, MIN_CROP), maxByImage)
  cropSize.value = nextSize
  const next = clampPosition(cx - nextSize / 2, cy - nextSize / 2, nextSize)
  cropX.value = next.x
  cropY.value = next.y
}

/** 取消 */
function onCancel() {
  dialogVisible.value = false
}

/** 确认裁剪：将选区映射到原图坐标，绘制圆形裁剪结果 */
async function onConfirm() {
  if (!props.file || !imageUrl.value) return
  processing.value = true
  try {
    const scale = naturalWidth.value / renderWidth.value
    const sourceX = (cropX.value - renderOffsetX.value) * scale
    const sourceY = (cropY.value - renderOffsetY.value) * scale
    const sourceSize = cropSize.value * scale

    const canvas = document.createElement('canvas')
    canvas.width = OUTPUT_SIZE
    canvas.height = OUTPUT_SIZE
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('Canvas 不可用')

    // 圆形裁剪
    ctx.save()
    ctx.beginPath()
    ctx.arc(OUTPUT_SIZE / 2, OUTPUT_SIZE / 2, OUTPUT_SIZE / 2, 0, Math.PI * 2)
    ctx.closePath()
    ctx.clip()

    const img = await loadImageElement(imageUrl.value)
    ctx.drawImage(img, sourceX, sourceY, sourceSize, sourceSize, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE)
    ctx.restore()

    const blob: Blob | null = await new Promise((resolve) => canvas.toBlob(resolve, 'image/png'))
    if (!blob) throw new Error('裁剪失败')

    const baseName = props.file.name.replace(/\.[^.]+$/, '')
    const cropped = new File([blob], `${baseName}.png`, { type: 'image/png' })
    emit('confirm', cropped)
    dialogVisible.value = false
  } catch (err) {
    ElMessage.error((err as Error).message || '裁剪失败')
  } finally {
    processing.value = false
  }
}

function loadImageElement(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = reject
    img.src = src
  })
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="裁剪头像"
    width="500"
    :close-on-click-modal="false"
    align-center
    append-to-body
  >
    <div class="crop-wrapper">
      <div
        class="crop-stage"
        :style="{ width: `${STAGE_SIZE}px`, height: `${STAGE_SIZE}px` }"
        @wheel="onWheel"
      >
        <img
          v-if="imageUrl"
          :src="imageUrl"
          class="crop-image"
          :style="{
            width: `${renderWidth}px`,
            height: `${renderHeight}px`,
            left: `${renderOffsetX}px`,
            top: `${renderOffsetY}px`,
          }"
          draggable="false"
          alt="待裁剪图片"
        />
        <div
          v-if="cropSize > 0"
          class="crop-circle"
          :style="{
            width: `${cropSize}px`,
            height: `${cropSize}px`,
            left: `${cropX}px`,
            top: `${cropY}px`,
          }"
          @pointerdown="onDragStart"
        >
          <span class="resize-handle" data-role="resize" @pointerdown="onResizeStart" />
        </div>
      </div>
      <p class="crop-tip">拖动圆形框选取头像区域，右下角手柄或滚轮缩放</p>
    </div>

    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button type="primary" :loading="processing" @click="onConfirm">确认</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.crop-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.crop-stage {
  position: relative;
  background-color: #1f1f1f;
  background-image:
    linear-gradient(45deg, #2a2a2a 25%, transparent 25%),
    linear-gradient(-45deg, #2a2a2a 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #2a2a2a 75%),
    linear-gradient(-45deg, transparent 75%, #2a2a2a 75%);
  background-size: 16px 16px;
  background-position: 0 0, 0 8px, 8px -8px, -8px 0;
  overflow: hidden;
  border-radius: 6px;
  user-select: none;
  touch-action: none;
}

.crop-image {
  position: absolute;
  pointer-events: none;
  user-select: none;
}

.crop-circle {
  position: absolute;
  border-radius: 50%;
  border: 2px dashed #fff;
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.55);
  cursor: move;
  box-sizing: border-box;
  touch-action: none;
}

.resize-handle {
  position: absolute;
  right: -8px;
  bottom: -8px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background-color: var(--el-color-primary, #409eff);
  border: 2px solid #fff;
  cursor: nwse-resize;
}

.crop-tip {
  font-size: 12px;
  color: var(--text-secondary, #999);
  margin: 0;
}
</style>
