<template>
  <main class="page">
    <div class="topbar">
      <div>
        <h2>项目</h2>
        <span class="muted">{{ auth.user?.username }}</span>
      </div>
      <div>
        <el-button :icon="Plus" type="primary" @click="createProject">新建</el-button>
        <el-button :icon="LogOut" @click="logout" />
      </div>
    </div>

    <section class="grid">
      <article v-for="project in projects" :key="project.id" class="panel project" @click="router.push(`/projects/${project.id}`)">
        <h3>{{ project.name }}</h3>
        <p class="muted">{{ project.description || '无描述' }}</p>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
import { LogOut, Plus } from 'lucide-vue-next'
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
  cursor: pointer;
}

.project h3 {
  margin: 0 0 8px;
}
</style>
