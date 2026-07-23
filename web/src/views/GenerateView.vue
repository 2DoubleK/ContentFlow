<template>
  <main class="workspace">
    <aside class="app-rail">
      <div class="rail-brand">C</div>
      <nav class="rail-nav" aria-label="主导航">
        <a class="rail-item" href="#" @click.prevent="router.back()">
          <ArrowLeft />
          <span>返回</span>
        </a>
        <a class="rail-item is-active" href="#" @click.prevent>
          <WandSparkles />
          <span>生成</span>
        </a>
      </nav>
    </aside>

    <section class="workspace-main">
      <div class="notice-bar">输入创作要求，系统会结合当前项目资料生成并保存 Markdown 草稿。</div>

      <div class="topbar">
        <h2>生成内容</h2>
        <el-button :icon="ArrowLeft" @click="router.back()">返回</el-button>
      </div>

      <section class="hero center-hero">
        <h1>想生成什么内容？</h1>
        <p>像写给助手的一句话那样描述目标，剩下的交给项目上下文。</p>
      </section>

      <section class="composer">
        <el-input v-model="prompt" type="textarea" :rows="8" placeholder="例如：根据资料生成一篇产品介绍，语气专业但不要太硬。" />
        <div class="composer-footer">
          <div class="pill-group">
            <span class="quick-chip"><WandSparkles />AI 草稿</span>
            <span class="quick-chip"><FileText />项目资料</span>
          </div>
          <el-button :icon="Send" type="primary" :loading="loading" @click="generate">生成并保存</el-button>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ArrowLeft, FileText, Send, WandSparkles } from 'lucide-vue-next'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { projectApi } from '../api/projects'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.id))
const prompt = ref('')
const loading = ref(false)
const error = ref('')

async function generate() {
  if (!prompt.value.trim()) return
  loading.value = true
  error.value = ''
  try {
    const response = await projectApi.generate(projectId.value, prompt.value)
    await router.push(`/contents/${response.data.data.id}`)
  } catch {
    error.value = '生成失败'
  } finally {
    loading.value = false
  }
}
</script>
