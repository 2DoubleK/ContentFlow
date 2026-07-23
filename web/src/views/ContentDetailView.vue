<template>
  <main class="page">
    <div class="topbar">
      <div>
        <h2>{{ content?.title }}</h2>
        <span class="muted">{{ content?.summary }}</span>
      </div>
      <el-button @click="router.back()">返回</el-button>
    </div>
    <article class="panel markdown">{{ content?.markdown }}</article>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { projectApi, type ContentItem } from '../api/projects'

const route = useRoute()
const router = useRouter()
const content = ref<ContentItem>()

onMounted(async () => {
  const response = await projectApi.content(Number(route.params.id))
  content.value = response.data.data
})
</script>
