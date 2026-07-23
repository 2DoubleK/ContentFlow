<template>
  <main class="workspace">
    <aside class="app-rail">
      <div class="rail-brand">C</div>
      <nav class="rail-nav" aria-label="主导航">
        <a class="rail-item" href="#" @click.prevent="router.push('/')">
          <ArrowLeft />
          <span>返回</span>
        </a>
        <a class="rail-item is-active" href="#" @click.prevent>
          <FolderOpen />
          <span>项目</span>
        </a>
      </nav>
    </aside>

    <section class="workspace-main">
      <div class="notice-bar">上传 TXT 或 MD 资料后，ContentFlow 会把它们作为项目上下文。</div>

      <div class="topbar">
        <div>
          <h2>{{ project?.name }}</h2>
          <span class="muted">{{ project?.description || '暂无描述' }}</span>
        </div>
        <div class="topbar-actions">
          <el-button :icon="Sparkles" type="primary" @click="router.push(`/projects/${projectId}/generate`)">生成内容</el-button>
        </div>
      </div>

      <section class="panel">
        <div class="panel-heading">
          <h3>参考资料</h3>
          <el-upload :auto-upload="false" :show-file-list="false" :on-change="upload">
            <el-button :icon="Upload">上传 TXT / MD</el-button>
          </el-upload>
        </div>
        <el-table :data="documents" style="width: 100%">
          <el-table-column prop="filename" label="文件" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="errorMessage" label="错误" />
        </el-table>
      </section>

      <section class="panel">
        <div class="panel-heading">
          <h3>生成内容</h3>
          <span class="muted">{{ contents.length }} 篇</span>
        </div>
        <el-table :data="contents" style="width: 100%">
          <el-table-column prop="title" label="标题" />
          <el-table-column prop="summary" label="摘要" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/contents/${row.id}`)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import type { UploadFile } from 'element-plus'
import { ArrowLeft, FolderOpen, Sparkles, Upload } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { projectApi, type ContentItem, type DocumentItem, type Project } from '../api/projects'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.id))
const project = ref<Project>()
const documents = ref<DocumentItem[]>([])
const contents = ref<ContentItem[]>([])

onMounted(load)

async function load() {
  const [projectResponse, documentResponse, contentResponse] = await Promise.all([
    projectApi.detail(projectId.value),
    projectApi.documents(projectId.value),
    projectApi.contents(projectId.value)
  ])
  project.value = projectResponse.data.data
  documents.value = documentResponse.data.data
  contents.value = contentResponse.data.data
}

async function upload(file: UploadFile) {
  if (!file.raw) return
  await projectApi.uploadDocument(projectId.value, file.raw)
  await load()
}
</script>
