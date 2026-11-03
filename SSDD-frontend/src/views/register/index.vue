<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()

const authStore = useAuthStore()

const formRef = ref<FormInstance>()

const form = reactive({
  email: '',
  password: '',
  confirmPassword: '',
  displayName: '',
})

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  displayName: [
    { max: 50, message: '显示名称不能超过 50 个字符', trigger: 'blur' },
  ],
}


async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const success = await authStore.register(
    form.email,
    form.password,
    form.displayName || undefined,
  )

  if (success) {
    router.push('/login')
  }
}
</script>

<template>
  <div class="register-container">
    <!-- 动态背景装饰 -->
    <div class="bg-decoration circle-1"></div>
    <div class="bg-decoration circle-2"></div>

    <!-- 注册主展示区 -->
    <div class="register-content">
      <!-- 视图左侧 -->
      <div class="register-header">
        <div class="logo-text">SSDD</div>
        <h1 class="system-name">加入我们<br>开启智能检测</h1>
        <p class="system-desc">注册账号以访问全部功能</p>
      </div>

      <!-- 视图右侧 -->
      <div class="register-card">
        <div class="card-title">新用户注册</div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          class="register-form"
          @keyup.enter="handleRegister"
        >
          <el-form-item prop="email">
            <el-input
              v-model="form.email"
              placeholder="请输入邮箱"
              prefix-icon="Message"
            />
          </el-form-item>

          <el-form-item prop="displayName">
            <el-input
              v-model="form.displayName"
              placeholder="显示名称 (选填)"
              prefix-icon="User"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="设置密码 (至少 6 位)"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="确认密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <div class="form-actions">
            <el-button
              type="primary"
              :loading="authStore.loading"
              class="submit-btn"
              @click="handleRegister"
            >
              立即注册
            </el-button>
          </div>
        </el-form>

        <div class="card-footer">
          <span class="text-gray">已有账号？</span>
          <router-link to="/login" class="link-login">返回登录</router-link>
        </div>
      </div>
    </div>

    <div class="copyright">
      &copy; 2026 Intelligent Surface Defect Detection System
    </div>
  </div>
</template>

<style scoped lang="scss">
/* 注册页主容 */
.register-container {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  overflow: hidden;
  color: #303133;

  /* 背景动态光斑 */
  .bg-decoration {
    position: absolute;
    border-radius: 50%;
    filter: blur(60px);
    z-index: 0;
    opacity: 0.5;
    animation: float 10s infinite ease-in-out;

    /* 第一颗光斑 - 定位在右上方 (与登录页的左上方互补) */
    &.circle-1 {
      width: 450px;
      height: 450px;
      background: #a0cfff;
      top: -80px;
      right: -80px; /* 位置微调，与登录页形成互补 */
      animation-delay: 0s;
    }

    /* 第二颗光斑 - 定位在左下方 (与登录页的右下方互补) */
    &.circle-2 {
      width: 350px;
      height: 350px;
      background: #d9ecff;
      bottom: -60px;
      left: -60px;
      animation-delay: -5s;
    }
  }

  /* 注册页主内容区 - 双栏布局 */
  .register-content {
    position: relative;
    z-index: 1;
    display: flex;
    align-items: center;
    gap: 200px;
    padding: 40px;
    width: 100%;
    max-width: 1200px;
    justify-content: center;

    /* 左侧文字展示区 */
    .register-header {
      width: 450px;

      .logo-text {
        font-size: 24px;
        font-weight: 800;
        letter-spacing: 4px;
        color: #409eff;
        margin-bottom: 20px;
      }

      .system-name {
        font-size: 48px;
        line-height: 1.2;
        font-weight: bold;
        margin-bottom: 24px;
        color: #303133;
        background: linear-gradient(120deg, #2c3e50, #409eff);
        -webkit-background-clip: text;
        background-clip: text; // 补充标准属性
        -webkit-text-fill-color: transparent;
      }

      .system-desc {
        font-size: 18px;
        color: #606266;
        letter-spacing: 1px;
        line-height: 1.6;
      }
    }

    /* 右侧注册表单卡片 */
    .register-card {
      width: 400px;
      padding: 40px;
      background: rgba(255, 255, 255, 0.75);
      backdrop-filter: blur(20px);
      border: 1px solid rgba(255, 255, 255, 0.8);
      border-radius: 24px;
      box-shadow: 0 20px 40px rgba(0, 0, 0, 0.08);

      .card-title {
        font-size: 24px;
        font-weight: 600;
        color: #303133;
        margin-bottom: 24px;
        text-align: center;
      }

      /* 覆盖 Element UI 输入框默认样式，适配浅色透明主题 */
      :deep(.el-input__wrapper) {
        background-color: #f5f7fa;
        box-shadow: 0 0 0 1px #dcdfe6 inset;
        padding: 8px 12px;
        transition: all 0.3s;

        &:hover {
          background-color: #fff;
          box-shadow: 0 0 0 1px #c0c4cc inset;
        }

        &.is-focus {
          background-color: #fff;
          box-shadow: 0 0 0 1px #409eff inset;
        }
      }

      :deep(.el-input__inner) {
        color: #606266;
        height: 40px;
      }

      /* 压缩表单项间距以适应高度 (注册页表单项多，防止超屏) */
      :deep(.el-form-item) {
        margin-bottom: 20px;
      }

      /* 注册提交按钮 */
      .submit-btn {
        width: 100%;
        height: 48px;
        font-size: 16px;
        letter-spacing: 2px;
        font-weight: 600;
        background: linear-gradient(135deg, #409eff 0%, #3a8ee6 100%);
        border: none;
        border-radius: 8px;
        margin-top: 8px;
        box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
        transition: all 0.3s;

        /* 悬停效果*/
        &:hover {
          transform: translateY(-2px);
          box-shadow: 0 6px 16px rgba(64, 158, 255, 0.4);
        }
      }

      /* 表单底部操作链接 (返回登录) */
      .card-footer {
        text-align: center;
        margin-top: 20px;
        font-size: 14px;
        color: #909399;

        .link-login {
          color: #409eff;
          text-decoration: none;
          font-weight: 500;
          margin-left: 8px;
          transition: color 0.2s;

          &:hover {
            text-decoration: underline;
            color: #337ecc;
          }
        }
      }
    }
  }

  /* 底部版权声明 */
  .copyright {
    position: absolute;
    bottom: 20px;
    font-size: 12px;
    color: #909399;
  }
}

/* 动态背景光斑动画关键帧 */
@keyframes float {
  0%,
  100% {
    transform: translate(0, 0);
  }
  50% {
    transform: translate(30px, 30px);
  }
}

/* 响应式适配：在屏幕宽度较小（平板或手机）时，将左右双栏平铺排列 */
@media (max-width: 900px) {
  .register-container {
    .register-content {
      flex-direction: column;
      gap: 30px;
      padding: 20px;

      .register-header {
        text-align: center;

        .system-name {
          font-size: 32px;
        }
      }

      .register-card {
        width: 100%;
        max-width: 420px;
      }
    }
  }
}
</style>
