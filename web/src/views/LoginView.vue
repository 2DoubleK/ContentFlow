<template>
  <main class="account-page">
    <header class="account-header">
      <RouterLink class="brand" to="/" aria-label="ContentFlow 首页">
        <span class="brand-mark">C</span>
        <span>Content<span>Flow</span></span>
      </RouterLink>
      <p class="header-prompt">
        {{ registerMode ? '已有 ContentFlow 账号？' : '还没有 ContentFlow 账号？' }}
        <button class="text-action" type="button" @click="setRegisterMode(!registerMode)">
          {{ registerMode ? '登录' : '注册' }}
        </button>
      </p>
    </header>

    <section class="account-stage" aria-labelledby="account-heading">
      <div class="account-box">
        <div class="account-title">
          <p class="eyebrow">CONTENTFLOW ACCOUNT</p>
          <h1 id="account-heading" data-testid="account-heading">{{ registerMode ? '注册 ContentFlow' : '登录 ContentFlow' }}</h1>
          <p>{{ registerMode ? '创建账号，开始组织内容工作流。' : '使用你的账号进入内容工作台。' }}</p>
        </div>

        <div class="mode-tabs" role="tablist" aria-label="账号操作">
          <button :class="{ active: !registerMode }" role="tab" :aria-selected="!registerMode" type="button" @click="setRegisterMode(false)">登录</button>
          <button :class="{ active: registerMode }" role="tab" :aria-selected="registerMode" type="button" @click="setRegisterMode(true)">注册</button>
        </div>

        <el-form class="account-form" label-position="top" @submit.prevent>
          <el-form-item label="账号" required>
            <el-input v-model="username" placeholder="请输入账号" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码" required>
            <el-input v-model="password" type="password" show-password placeholder="请输入密码" autocomplete="current-password" />
          </el-form-item>
          <template v-if="registerMode">
            <el-form-item label="确认密码" required>
              <el-input v-model="confirmPassword" type="password" show-password placeholder="请再次输入密码" autocomplete="new-password" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="email" placeholder="邮箱和手机号至少填写一项" autocomplete="email" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="phone" placeholder="请输入手机号" autocomplete="tel" />
            </el-form-item>
          </template>

          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <el-button class="submit-button" :icon="registerMode ? UserPlus : LogIn" type="primary" :loading="loading" @click="registerMode ? submitRegister() : submitLogin()">
            {{ registerMode ? '注册' : '登录' }}
          </el-button>
        </el-form>

        <p class="account-notice">继续即表示你同意 ContentFlow 的服务条款与隐私政策。</p>
      </div>
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

function setRegisterMode(value: boolean) {
  registerMode.value = value
  error.value = ''
}

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
.account-page {
  min-height: 100vh;
  background: var(--cf-soft);
  color: var(--cf-text);
}

.account-header {
  height: 68px;
  padding: 0 max(28px, calc((100% - 1280px) / 2));
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--cf-line);
  background: rgba(255, 255, 255, 0.86);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--cf-text);
  font-size: 20px;
  font-weight: 700;
  text-decoration: none;
}

.brand > span:last-child span {
  color: var(--cf-cyan);
}

.brand-mark {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 7px;
  background: var(--cf-blue);
  color: #ffffff;
  font-size: 17px;
}

.header-prompt,
.account-notice {
  margin: 0;
  color: var(--cf-muted);
  font-size: 14px;
}

.text-action {
  margin-left: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--cf-blue);
  cursor: pointer;
  font: inherit;
}

.text-action:hover {
  color: var(--cf-cyan);
}

.account-stage {
  display: grid;
  min-height: calc(100vh - 68px);
  place-items: center;
  padding: 48px 24px 72px;
}

.account-box {
  width: min(410px, 100%);
  padding: 34px 42px 28px;
  border: 1px solid var(--cf-line);
  border-radius: 8px;
  background: var(--cf-panel);
  box-shadow: var(--cf-shadow);
}

.account-title {
  margin-bottom: 26px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--cf-cyan);
  font-size: 11px;
  font-weight: 700;
}

.account-title h1 {
  margin: 0;
  color: var(--cf-text);
  font-size: 26px;
  line-height: 1.3;
}

.account-title > p:last-child {
  margin: 8px 0 0;
  color: var(--cf-muted);
  font-size: 14px;
}

.mode-tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  margin-bottom: 24px;
  border-bottom: 1px solid var(--cf-line);
}

.mode-tabs button {
  position: relative;
  padding: 0 0 11px;
  border: 0;
  background: transparent;
  color: var(--cf-muted);
  cursor: pointer;
  font: inherit;
  font-size: 15px;
}

.mode-tabs button.active {
  color: var(--cf-blue);
  font-weight: 600;
}

.mode-tabs button.active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: var(--cf-cyan);
  content: '';
}

.account-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.account-form :deep(.el-form-item__label) {
  color: var(--cf-text);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
}

.account-form :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: 3px;
  box-shadow: 0 0 0 1px var(--cf-line) inset;
}

.account-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--cf-cyan) inset;
}

.form-error {
  margin: -2px 0 16px;
  padding: 9px 10px;
  border-left: 3px solid #dc5a45;
  background: #fff5f2;
  color: #b53d2d;
  font-size: 13px;
}

.submit-button {
  width: 100%;
  min-height: 42px;
  border-radius: 3px;
  background: var(--cf-blue);
  border-color: var(--cf-blue);
  font-size: 15px;
}

.submit-button:hover,
.submit-button:focus {
  background: var(--cf-cyan);
  border-color: var(--cf-cyan);
}

.account-notice {
  margin-top: 20px;
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 560px) {
  .account-header {
    height: 60px;
    padding: 0 18px;
  }

  .brand {
    font-size: 18px;
  }

  .brand-mark {
    width: 27px;
    height: 27px;
  }

  .header-prompt {
    font-size: 12px;
  }

  .account-stage {
    min-height: calc(100vh - 60px);
    display: block;
    padding: 28px 16px 44px;
  }

  .account-box {
    width: 100%;
    padding: 28px 22px 24px;
    box-sizing: border-box;
  }
}
</style>
