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
        <div class="login-actions">
          <el-button :icon="LogIn" type="primary" :loading="loading" @click="submitLogin">登录</el-button>
          <el-button :icon="UserPlus" :loading="loading" @click="submitRegister">注册</el-button>
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

const auth = useAuthStore()
const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function submitLogin() {
  await submit(() => auth.login(username.value, password.value))
}

async function submitRegister() {
  await submit(() => auth.register(username.value, password.value))
}

async function submit(action: () => Promise<void>) {
  loading.value = true
  error.value = ''
  try {
    await action()
    await router.push('/')
  } catch {
    error.value = '账号或密码无效'
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
