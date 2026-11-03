<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()

const authStore = useAuthStore()

const formRef = ref<FormInstance>()

const form = reactive({
  email: '',
  password: '',
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
}


async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const success = await authStore.login(form.email, form.password)

  if (success) {
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  }
}
</script>

<template>
  <div class="login-container">
    <!-- 动态背景装饰 -->
    <div class="bg-decoration circle-1"></div>
    <div class="bg-decoration circle-2"></div>

    <!-- 登录主内容区 -->
    <div class="login-content">
      <!-- 视窗左侧 -->
      <div class="login-header">
        <div class="logo-text">SSDD</div>
        <h1 class="system-name">钢铁表面<br>缺陷检测系统</h1>
        <p class="system-desc">基于深度学习的工业级智能检测平台</p>
      </div>

      <!-- 视窗右侧 -->
      <div class="login-card">
        <div class="card-title">用户登录</div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          class="login-form"
          @keyup.enter="handleLogin"
        >
          <!-- 账号输入域：绑定 email，带有自定义前置图标 -->
          <el-form-item prop="email">
            <el-input
              v-model="form.email"
              placeholder="请输入邮箱账号"
              prefix-icon="Message"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入登录密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              :loading="authStore.loading"
              class="submit-btn"
              @click="handleLogin"
            >
              立即登录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="card-footer">
          <span class="text-gray">还没有账号？</span>
          <router-link to="/register" class="link-register">注册新用户</router-link>
        </div>
      </div>
    </div>

    <div class="copyright">
      &copy; 2026 Intelligent Surface Defect Detection System
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-container {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  /* 浅色渐变背景 */
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

    /* 第一颗光斑 */
    &.circle-1 {
      width: 400px;
      height: 400px;
      background: #a0cfff;
      top: -50px;
      left: -50px;
      animation-delay: 0s;
    }

    /* 第二颗光斑 */
    &.circle-2 {
      width: 300px;
      height: 300px;
      background: #d9ecff;
      bottom: -50px;
      right: -50px;
      animation-delay: -5s;
    }
  }

  /* 登录主内容区 */
  .login-content {
    position: relative;
    z-index: 1;
    display: flex;
    align-items: center; /* 上下垂直居中对齐 */
    gap: 200px; /* 两个组件之间的固定间距为 80px */
    padding: 40px;
    width: 100%;
    max-width: 1200px;
    justify-content: center; /* 在水平方向上居中 */

    /* 左侧文字展示区 */
    .login-header {
      width: 450px;

      .logo-text {
        font-size: 24px;
        font-weight: 800;
        letter-spacing: 4px;
        color: #409eff;
        margin-bottom: 20px;
      }

      /* 系统主标题 */
      .system-name {
        font-size: 48px;
        line-height: 1.2;
        font-weight: bold;
        margin-bottom: 24px;
        color: #303133;
        background: linear-gradient(120deg, #2c3e50, #409eff);
        background-clip: text;
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
      }

      .system-desc {
        font-size: 18px;
        color: #606266;
        letter-spacing: 1px;
        line-height: 1.6;
      }
    }

    /* 右侧登录表单卡片 */
    .login-card {
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
        margin-bottom: 30px;
        text-align: center;
      }

      /* Element UI 输入框组件样式深度覆盖，适配整体浅色主题交互 */
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

      :deep(.el-form-item__label) {
        color: #606266;
        font-weight: 500;
      }

      .submit-btn {
        width: 100%;
        height: 48px;
        font-size: 16px;
        letter-spacing: 2px;
        font-weight: 600;
        background: linear-gradient(135deg, #409eff 0%, #3a8ee6 100%);
        border: none;
        border-radius: 8px;
        margin-top: 10px;
        box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
        transition: all 0.3s;

        &:hover {
          transform: translateY(-2px);
          box-shadow: 0 6px 16px rgba(64, 158, 255, 0.4);
        }
      }

      .card-footer {
        text-align: center;
        margin-top: 24px;
        font-size: 14px;
        color: #909399;

        .link-register {
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

  .copyright {
    position: absolute;
    bottom: 24px;
    font-size: 12px;
    color: #909399;
  }
}

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
  .login-container {
    .login-content {
      flex-direction: column;
      gap: 40px;
      padding: 20px;

      .login-header {
        text-align: center;
        margin-bottom: 0;

        .system-name {
          font-size: 32px;
        }
      }

      .login-card {
        width: 100%;
        max-width: 400px;
      }
    }
  }
}
</style>
