<template>
  <main class="login-page">
    <section class="login-box">
      <h1>ContentFlow</h1>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password />
        </el-form-item>
        <div class="login-actions">
          <el-button type="primary" :loading="loading" @click="submitLogin">登录</el-button>
          <el-button :loading="loading" @click="submitRegister">注册</el-button>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
      </el-form>
    </section>
  </main>
</template>

<script setup lang="ts">
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
  place-items: center;
  padding: 24px;
}

.login-box {
  width: min(380px, 100%);
  padding: 24px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.login-actions {
  display: flex;
  gap: 10px;
}

.error {
  color: #c2410c;
}
</style>
