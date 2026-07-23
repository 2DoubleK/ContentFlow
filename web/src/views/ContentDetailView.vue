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
          <FileText />
          <span>内容</span>
        </a>
      </nav>
    </aside>

    <section class="workspace-main">
      <div class="notice-bar">以下内容由 AI 生成，请结合项目资料复核事实和措辞。</div>

      <div class="topbar">
        <div>
          <h2>{{ content?.title }}</h2>
          <span class="muted">{{ content?.summary }}</span>
        </div>
        <el-button :icon="ArrowLeft" @click="router.back()">返回</el-button>
      </div>

      <article class="panel markdown article-panel">{{ content?.markdown }}</article>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ArrowLeft, FileText } from 'lucide-vue-next'
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

<style scoped>
.article-panel {
  min-height: 420px;
}
</style>
