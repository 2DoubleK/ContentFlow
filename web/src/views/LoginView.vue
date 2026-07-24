<template>
  <main class="login-page">
    <section class="login-hero">
      <div class="rail-brand">C</div>
      <h1>Content<span>Flow</span></h1>
      <p>蓝白青色调的 AI 内容工作台</p>
    </section>

    <section class="panel login-box">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password />
        </el-form-item>
        <template v-if="registerMode">
          <el-form-item label="确认密码"><el-input v-model="confirmPassword" type="password" show-password /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="email" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="phone" /></el-form-item>
        </template>
        <div class="login-actions">
          <el-button :icon="LogIn" type="primary" :loading="loading" @click="submitLogin">登录</el-button>
          <el-button :icon="UserPlus" :loading="loading" @click="registerMode ? submitRegister() : registerMode = true">注册</el-button>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
      </el-form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { LogIn, UserPlus } from 'lucide-vue-next'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import axios from 'axios'

const auth = useAuthStore()
const router = useRouter()
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const email = ref('')
const phone = ref('')
const registerMode = ref(false)
const loading = ref(false)
const error = ref('')

async function submitLogin() {
  await submit(() => auth.login(username.value, password.value))
}

async function submitRegister() {
  const validationError = validateRegistration()
  if (validationError) {
    error.value = validationError
    return
  }
  await submit(() => auth.register({ username: username.value, password: password.value, confirmPassword: confirmPassword.value, email: email.value, phone: phone.value }))
}

function validateRegistration() {
  if (!username.value.trim()) return '请输入账号'
  if (password.value.length < 6) return '密码至少需要 6 位'
  if (password.value !== confirmPassword.value) return '两次输入的密码不一致'
  if (!email.value.trim() && !phone.value.trim()) return '邮箱或手机号至少填写一项'
  return ''
}

async function submit(action: () => Promise<void>) {
  loading.value = true
  error.value = ''
  try {
    await action()
    await router.push('/')
  } catch (cause) {
    error.value = axios.isAxiosError(cause)
      ? cause.response?.data?.message || '请求失败，请稍后重试'
      : '请求失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 24px;
  padding: 24px;
}

.login-hero {
  display: grid;
  justify-items: center;
  gap: 12px;
  text-align: center;
}

.login-hero h1 {
  margin: 0;
  font-size: clamp(40px, 8vw, 78px);
  line-height: 1;
}

.login-hero span {
  color: #0891b2;
}

.login-hero p {
  margin: 0;
  color: #64748b;
}

.login-box {
  width: min(420px, 100%);
}

.login-actions {
  display: flex;
  gap: 10px;
}

.error {
  color: #c2410c;
}
</style>
