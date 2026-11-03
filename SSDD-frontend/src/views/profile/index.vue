<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { getImageUrl } from '@/utils/image'
import { formatDateTime } from '@/utils/format'
import { User, Check, Upload, Key } from '@element-plus/icons-vue'
import type { FormInstance, FormRules, UploadProps } from 'element-plus'
import { ElMessage } from 'element-plus'
import PasswordDialog from './components/PasswordDialog.vue'
import AvatarCropDialog from './components/AvatarCropDialog.vue'

const authStore = useAuthStore()

// ---- 状态与计算属性 ----
const userEmail = computed(() => authStore.user?.email ?? '')
const userRole = computed(() => authStore.userRole)
const roleName = computed(() => (userRole.value === 'admin' ? '系统管理员' : '质检操作员'))
const roleType = computed(() => (userRole.value === 'admin' ? 'danger' : 'primary'))
const createdAt = computed(() =>
  authStore.user?.profile?.created_at ? formatDateTime(authStore.user.profile.created_at) : '-',
)

// ---- 基本信息编辑 ----
const profileFormRef = ref<FormInstance>()
/** 是否处于编辑显示名称状态 */
const isEditingName = ref(false)
/** 编辑表单数据（仅在切换为编辑态时同步 store 值） */
const profileForm = reactive({
  displayName: '',
})
/** 显示名称校验规则 */
const profileRules: FormRules = {
  displayName: [
    { required: true, message: '请输入显示名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度 2~50 个字符', trigger: 'blur' },
  ],
}

/** 进入编辑态，从 store 同步当前名称 */
function startEdit() {
  profileForm.displayName = authStore.displayName
  isEditingName.value = true
}

/** 取消编辑，恢复 store 中的原始值 */
function cancelEdit() {
  isEditingName.value = false
  profileForm.displayName = authStore.displayName
}

/** 提交显示名称修改（成功提示由 store 统一处理） */
async function saveProfile() {
  if (!profileFormRef.value) return
  const valid = await profileFormRef.value.validate().catch(() => false)
  if (!valid) return

  const ok = await authStore.updateProfile(profileForm.displayName)
  if (ok) {
    isEditingName.value = false
  }
}

// ---- 头像上传 ----
/** 头像展示地址（自动拼接完整 URL） */
const avatarSrc = computed(() => {
  const url = authStore.avatarUrl
  return url ? getImageUrl(url) : ''
})

/** 头像上传中状态标志 */
const avatarUploading = ref(false)

/** 允许上传的图片 MIME 类型 */
const ALLOWED_AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
/** 头像最大文件大小（MB） */
const MAX_AVATAR_SIZE_MB = 2

/** 待裁剪文件与裁剪弹窗显隐 */
const pendingAvatarFile = ref<File | null>(null)
const cropDialogVisible = ref(false)

/** 上传前校验文件类型与大小，通过后改为打开裁剪弹窗（阻止原生上传） */
const validateAvatar: UploadProps['beforeUpload'] = (file) => {
  if (!ALLOWED_AVATAR_TYPES.includes(file.type)) {
    ElMessage.error('只支持 JPG / PNG / GIF / WebP 格式')
    return false
  }
  if (file.size / 1024 / 1024 >= MAX_AVATAR_SIZE_MB) {
    ElMessage.error('头像大小不能超过 2MB')
    return false
  }
  // 转入裁剪流程，阻止 el-upload 自动上传
  pendingAvatarFile.value = file as File
  cropDialogVisible.value = true
  return false
}

/** 裁剪完成后实际执行上传 */
async function handleCroppedAvatar(file: File) {
  avatarUploading.value = true
  try {
    await authStore.uploadAvatar(file)
  } finally {
    avatarUploading.value = false
    pendingAvatarFile.value = null
  }
}

// ---- 弹窗控制 ----
/** 控制修改密码弹窗的显隐 */
const passwordDialogVisible = ref(false)
</script>

<template>
  <div class="profile-page">
    <div class="page-header">
      <h2 class="page-title">个人主页</h2>
      <p class="page-desc">管理您的基本信息与账户安全设置。</p>
    </div>

    <div class="settings-container">
      <!-- 区块：公开信息 -->
      <div class="settings-section">
        <h3 class="section-title">基本信息</h3>
        <el-card shadow="never" class="settings-card">
          <!-- 头像行 -->
          <div class="settings-row avatar-row">
            <div class="row-info">
              <span class="row-label">头像</span>
              <span class="row-desc">点击右侧更新您的个人头像，支持 JPG / PNG，2MB以内。</span>
            </div>
            <div class="row-action">
              <el-upload
                class="avatar-uploader"
                action=""
                :show-file-list="false"
                :before-upload="validateAvatar"
                accept="image/jpeg,image/png,image/gif,image/webp"
              >
                <div class="avatar-hover-wrapper" :class="{ 'is-uploading': avatarUploading }">
                  <el-avatar :size="64" :src="avatarSrc" class="user-avatar">
                    <el-icon :size="28"><User /></el-icon>
                  </el-avatar>
                  <div class="avatar-mask">
                    <el-icon><Upload /></el-icon>
                  </div>
                </div>
              </el-upload>
            </div>
          </div>

          <!-- 显示名称行 -->
          <div class="settings-row">
            <div class="row-info flex-1">
              <span class="row-label">显示名称</span>
              <div v-if="!isEditingName" class="row-value">{{ authStore.displayName || '未设置' }}</div>
              <div v-else class="edit-wrapper">
                <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" @submit.prevent>
                  <el-form-item prop="displayName" class="mb-0">
                    <el-input
                      v-model="profileForm.displayName"
                      placeholder="请输入显示名称"
                      maxlength="30"
                      class="name-input"
                    />
                    <el-button type="primary" :icon="Check" :loading="authStore.loading" @click="saveProfile">保存</el-button>
                    <el-button @click="cancelEdit">取消</el-button>
                  </el-form-item>
                </el-form>
              </div>
            </div>
            <div class="row-action" v-if="!isEditingName">
              <el-button plain @click="startEdit">编辑</el-button>
            </div>
          </div>

          <!-- 邮箱行 (只读) -->
          <div class="settings-row">
            <div class="row-info">
              <span class="row-label">绑定邮箱</span>
              <span class="row-value">{{ userEmail }}</span>
            </div>
          </div>

          <!-- 角色行 (只读) -->
          <div class="settings-row">
            <div class="row-info">
              <span class="row-label">系统角色</span>
              <span class="row-value mt-2">
                <el-tag :type="roleType" size="small" effect="light" class="role-tag">{{ roleName }}</el-tag>
              </span>
            </div>
          </div>

          <!-- 注册时间行 (只读) -->
          <div class="settings-row border-none">
            <div class="row-info">
              <span class="row-label">注册时间</span>
              <span class="row-value text-secondary">{{ createdAt }}</span>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 区块：账户安全 -->
      <div class="settings-section">
        <h3 class="section-title">账户安全</h3>
        <el-card shadow="never" class="settings-card">
          <div class="settings-row border-none">
            <div class="row-info">
              <span class="row-label">登录密码</span>
              <span class="row-desc">建议您定期更改密码，确保账号与数据安全。</span>
            </div>
            <div class="row-action">
              <el-button plain :icon="Key" @click="passwordDialogVisible = true">修改密码</el-button>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 独立封装的密码修改弹窗 -->
    <PasswordDialog v-model:visible="passwordDialogVisible" />

    <!-- 头像圆形裁剪弹窗 -->
    <AvatarCropDialog
      v-model:visible="cropDialogVisible"
      :file="pendingAvatarFile"
      @confirm="handleCroppedAvatar"
    />
  </div>
</template>

<style scoped lang="scss">
.profile-page {
  max-width: 860px;
  margin: 0 auto; /* 居中对齐，类似于 Github 设置页的体验 */
  padding-bottom: 40px;
}

.page-header {
  margin-bottom: 32px;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.page-desc {
  font-size: 14px;
  color: var(--text-secondary);
}

.settings-container {
  display: flex;
  flex-direction: column;
  gap: 40px;
}

.settings-section {
  display: flex;
  flex-direction: column;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.settings-card {
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background-color: transparent;

  :deep(.el-card__body) {
    padding: 0;
  }
}

.settings-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  border-bottom: 1px solid var(--border-color);

  &.border-none {
    border-bottom: none;
  }
}

.row-info {
  display: flex;
  flex-direction: column;
  gap: 6px;

  &.flex-1 {
    flex: 1;
  }
}

.row-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.row-value {
  font-size: 14px;
  color: var(--text-regular);
}

.mt-2 {
  margin-top: 4px;
}

.row-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  max-width: 480px;
}

.text-secondary {
  color: var(--text-secondary) !important;
}

.role-tag {
  border-radius: 4px;
  font-weight: 500;
}

/* 带有悬浮蒙层的头像上传交互 */
.avatar-hover-wrapper {
  position: relative;
  cursor: pointer;
  border-radius: 50%;
  overflow: hidden;
  border: 1px solid var(--border-color);
  background-color: #fff;
  transition: all 0.2s ease;

  &.is-uploading {
    opacity: 0.5;
    pointer-events: none;
  }

  .avatar-mask {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0, 0, 0, 0.4);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transition: opacity 0.2s ease;
  }

  &:hover {
    border-color: var(--color-primary);

    .avatar-mask {
      opacity: 1;
    }
  }
}

/* 名称编辑表单特定样式 */
.edit-wrapper {
  margin-top: 12px;

  .mb-0 {
    margin-bottom: 0;
  }

  .name-input {
    max-width: 240px;
    margin-right: 12px;
  }
}
</style>
