<template>
  <main class="workspace">
    <aside class="app-rail">
      <div class="rail-brand">C</div>
      <nav class="rail-nav" aria-label="主导航">
        <a class="rail-item is-active" href="#" @click.prevent>
          <FolderOpen />
          <span>项目</span>
        </a>
        <a class="rail-item" href="#" @click.prevent="createProject">
          <Plus />
          <span>新建</span>
        </a>
      </nav>
    </aside>

    <section class="workspace-main">
      <div class="notice-bar">内容由 AI 生成后建议复核，项目资料会作为生成上下文。</div>

      <div class="topbar">
        <div>
          <h2 class="brand-title">Content<span class="accent">Flow</span></h2>
          <span class="muted">{{ auth.user?.username }}</span>
        </div>
        <div class="topbar-actions">
          <el-button :icon="Plus" type="primary" @click="createProject">新建项目</el-button>
          <el-button :icon="LogOut" @click="logout">退出</el-button>
        </div>
      </div>

      <section class="hero">
        <h1>从资料到内容，一步进入创作。</h1>
        <p>上传项目参考资料，生成草稿，并把每一篇内容保存在对应项目里。</p>
      </section>

      <div class="quick-row">
        <span class="quick-chip"><Sparkles />AI 生成</span>
        <span class="quick-chip"><FileText />资料引用</span>
        <span class="quick-chip"><FolderOpen />项目归档</span>
      </div>

      <section class="grid">
        <article v-for="project in projects" :key="project.id" class="panel project" @click="router.push(`/projects/${project.id}`)">
          <div class="project-icon"><FolderOpen /></div>
          <h3>{{ project.name }}</h3>
          <p class="muted">{{ project.description || '暂无描述' }}</p>
        </article>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { FileText, FolderOpen, LogOut, Plus, Sparkles } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { projectApi, type Project } from '../api/projects'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const projects = ref<Project[]>([])

onMounted(load)

async function load() {
  const response = await projectApi.list()
  projects.value = response.data.data
}

async function createProject() {
  const name = window.prompt('项目名称')
  if (!name) return
  const response = await projectApi.create({ name, description: '' })
  await router.push(`/projects/${response.data.data.id}`)
}

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.project {
  min-height: 174px;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.project:hover {
  transform: translateY(-2px);
  border-color: rgba(6, 182, 212, 0.6);
  box-shadow: 0 26px 70px rgba(37, 99, 235, 0.11);
}

.project h3 {
  margin: 0 0 8px;
  font-size: 20px;
}

.project-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  margin-bottom: 18px;
  color: #2563eb;
  background: #ecfeff;
  border: 1px solid rgba(125, 211, 252, 0.44);
  border-radius: 14px;
}

.project-icon svg {
  width: 19px;
  height: 19px;
}
</style>
