<template>
  <main class="page">
    <div class="topbar">
      <h2>生成内容</h2>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <section class="panel">
      <el-input v-model="prompt" type="textarea" :rows="8" placeholder="输入创作要求" />
      <div class="actions">
        <el-button type="primary" :loading="loading" @click="generate">生成并保存</el-button>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </section>
  </main>
</template>

<script setup lang="ts">
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

<style scoped>
.actions {
  margin-top: 12px;
}

.error {
  color: #c2410c;
}
</style>
