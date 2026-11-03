import { getImageUrl } from '@/utils/image'

/**
 * 图片 URL 处理组合式函数
 */
export function useImageUrl() {
  /**
   * 将后端相对路径转换为完整可访问 URL
   */
  function toFullUrl(relativePath: string): string {
    return getImageUrl(relativePath)
  }

  return {
    toFullUrl,
  }
}
