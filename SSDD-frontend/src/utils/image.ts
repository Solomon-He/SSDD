const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:3000'

/**
 * 将后端返回的相对图片路径转换为完整 URL
 * 开发环境下通过 Vite 代理访问，生产环境拼接基础地址
 * @param relativePath 相对路径，如 /uploads/results/xxx.png
 */
export function getImageUrl(relativePath: string): string {
  if (!relativePath) return ''
  // 如果已经是完整 URL 则直接返回
  if (relativePath.startsWith('http://') || relativePath.startsWith('https://')) {
    return relativePath
  }
  // 开发环境走 Vite 代理，直接用相对路径即可
  if (import.meta.env.DEV) {
    return relativePath
  }
  // 生产环境拼接基础地址
  return `${BASE_URL}${relativePath}`
}
