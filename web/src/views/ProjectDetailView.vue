<template>
  <main class="page">
    <div class="topbar">
      <div>
        <h2>{{ project?.name }}</h2>
        <span class="muted">{{ project?.description }}</span>
      </div>
      <el-button type="primary" @click="router.push(`/projects/${projectId}/generate`)">生成内容</el-button>
    </div>

    <section class="panel">
      <h3>参考资料</h3>
      <el-upload :auto-upload="false" :show-file-list="false" :on-change="upload">
        <el-button>上传 TXT / MD</el-button>
      </el-upload>
      <el-table :data="documents" style="width: 100%; margin-top: 12px">
        <el-table-column prop="filename" label="文件" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="errorMessage" label="错误" />
      </el-table>
    </section>

    <section class="panel content-panel">
      <h3>生成内容</h3>
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
  </main>
</template>

<script setup lang="ts">
import type { UploadFile } from 'element-plus'
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

<style scoped>
.content-panel {
  margin-top: 16px;
}
</style>
