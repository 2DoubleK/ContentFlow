<template>
  <main class="workspace projects-page">
    <aside class="app-rail projects-rail">
      <a class="rail-brand" href="#" aria-label="ContentFlow 项目" @click.prevent>C</a>
      <nav class="rail-nav" aria-label="主导航">
        <a class="rail-item is-active" href="#" @click.prevent>
          <FolderOpen />
          <span>项目</span>
        </a>
        <a class="rail-item" href="#" @click.prevent="openCreateDialog">
          <MessageSquarePlus />
          <span>创建</span>
        </a>
      </nav>
      <button class="rail-profile" type="button" title="退出登录" @click="logout">
        <span>{{ auth.user?.username?.slice(0, 1).toUpperCase() || 'U' }}</span>
        <LogOut />
      </button>
    </aside>

    <section class="workspace-main projects-main">
      <header class="projects-topbar">
        <div>
          <p class="section-kicker">CONTENTFLOW</p>
          <h1>项目工作台</h1>
        </div>
        <el-button :icon="MessageSquarePlus" type="primary" @click="openCreateDialog">AI 创建项目</el-button>
      </header>

      <section class="projects-brief">
        <div>
          <p class="section-kicker">PROJECT LIBRARY</p>
          <h2>把资料、对话和内容放进同一个项目。</h2>
          <p>从一句目标开始，让 AI 帮你整理项目，再持续沉淀生成内容。</p>
        </div>
        <div class="quick-row" aria-label="项目能力">
          <span class="quick-chip"><Sparkles />AI 生成</span>
          <span class="quick-chip"><FileText />资料引用</span>
          <span class="quick-chip"><FolderOpen />项目归档</span>
        </div>
      </section>

      <section class="projects-section" aria-labelledby="projects-heading">
        <div class="projects-section-heading">
          <div>
            <p class="section-kicker">YOUR PROJECTS</p>
            <h2 id="projects-heading">项目列表</h2>
          </div>
          <span class="muted">{{ projects.length }} 个项目</span>
        </div>
        <div class="grid projects-grid">
          <article v-for="project in projects" :key="project.id" class="panel project" @click="router.push(`/projects/${project.id}`)">
            <div class="project-icon"><FolderOpen /></div>
            <h3>{{ project.name }}</h3>
            <p class="muted">{{ project.description || '暂无描述' }}</p>
          </article>
          <button v-if="projects.length === 0" class="empty-project" type="button" @click="openCreateDialog">
            <MessageSquarePlus />
            <span>和 AI 对话创建第一个项目</span>
          </button>
        </div>
      </section>
    </section>

    <el-dialog v-model="createDialogVisible" class="ai-project-dialog" width="min(620px, calc(100% - 32px))" :close-on-click-modal="false" @closed="resetCreateForm">
      <template #header>
        <div class="dialog-heading">
          <div class="dialog-avatar"><Sparkles /></div>
          <div>
            <h2>AI 帮你创建项目</h2>
            <p>先说说你要完成什么，我会整理成可用项目。</p>
          </div>
        </div>
      </template>

      <div class="conversation">
        <div class="ai-message">
          <div class="message-avatar"><Sparkles /></div>
          <p>描述你想创建的项目，包括目标、主题或要处理的资料。</p>
        </div>
        <template v-if="projectPreview">
          <div class="user-message"><p>{{ projectBrief }}</p></div>
          <div class="ai-message">
            <div class="message-avatar"><Sparkles /></div>
            <div class="project-preview">
              <p>我已为你整理好项目：</p>
              <strong>{{ projectPreview.name }}</strong>
              <span>{{ projectPreview.description }}</span>
            </div>
          </div>
        </template>
      </div>

      <div v-if="!projectPreview" class="dialog-composer">
        <label for="project-brief">描述你想创建的项目</label>
        <el-input id="project-brief" v-model="projectBrief" type="textarea" :rows="4" placeholder="例如：为秋季新品发布整理产品资料，并生成社媒内容" maxlength="500" show-word-limit />
        <p v-if="createError" class="form-error" role="alert">{{ createError }}</p>
        <el-button class="dialog-submit" type="primary" :icon="Sparkles" @click="generateProjectPreview">生成项目</el-button>
      </div>
      <div v-else class="dialog-actions">
        <el-button @click="projectPreview = null">重新描述</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreateProject">确认创建</el-button>
      </div>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { FileText, FolderOpen, LogOut, MessageSquarePlus, Sparkles } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { projectApi, type Project } from '../api/projects'
import { useAuthStore } from '../stores/auth'

type ProjectPreview = { name: string; description: string }

const router = useRouter()
const auth = useAuthStore()
const projects = ref<Project[]>([])
const createDialogVisible = ref(false)
const projectBrief = ref('')
const projectPreview = ref<ProjectPreview | null>(null)
const creating = ref(false)
const createError = ref('')

onMounted(load)

async function load() {
  const response = await projectApi.list()
  projects.value = response.data.data
}

function openCreateDialog() {
  createError.value = ''
  createDialogVisible.value = true
}

function resetCreateForm() {
  projectBrief.value = ''
  projectPreview.value = null
  createError.value = ''
}

function generateProjectPreview() {
  const description = projectBrief.value.trim()
  if (!description) {
    createError.value = '请先描述你想创建的项目'
    return
  }

  const name = description.split(/[。！？.!?\n]/)[0].trim().slice(0, 40)
  projectPreview.value = {
    name: name || '未命名项目',
    description
  }
  createError.value = ''
}

async function submitCreateProject() {
  if (!projectPreview.value) return

  creating.value = true
  createError.value = ''
  try {
    const response = await projectApi.create(projectPreview.value)
    createDialogVisible.value = false
    await router.push(`/projects/${response.data.data.id}`)
  } catch {
    createError.value = '创建项目失败，请稍后重试'
  } finally {
    creating.value = false
  }
}

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.projects-page {
  min-height: 100vh;
}

.projects-rail {
  border-right: 1px solid var(--cf-line);
  background: rgba(255, 255, 255, 0.58);
}

.rail-profile {
  position: relative;
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 1px solid var(--cf-line);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.8);
  color: var(--cf-blue);
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}

.rail-profile svg {
  position: absolute;
  width: 14px;
  height: 14px;
  right: -5px;
  bottom: -5px;
  padding: 2px;
  border-radius: 50%;
  background: #ffffff;
  color: var(--cf-muted);
}

.projects-main {
  width: min(1120px, calc(100vw - 116px));
  padding-top: 40px;
}

.projects-topbar,
.projects-section-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
}

.section-kicker {
  margin: 0 0 9px;
  color: var(--cf-cyan);
  font-size: 12px;
  font-weight: 700;
}

.projects-topbar h1,
.projects-brief h2,
.projects-section-heading h2 {
  margin: 0;
  line-height: 1.18;
}

.projects-topbar h1 {
  font-size: 26px;
}

.projects-brief {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(290px, 0.8fr);
  align-items: end;
  gap: 32px;
  margin-top: 40px;
  padding: 42px;
  border: 1px solid var(--cf-line);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.66);
}

.projects-brief h2 {
  max-width: 610px;
  font-size: 36px;
}

.projects-brief p:not(.section-kicker) {
  max-width: 590px;
  margin: 14px 0 0;
  color: #475569;
  line-height: 1.8;
}

.quick-row {
  justify-content: flex-end;
  margin: 0;
}

.projects-section {
  padding-top: 42px;
}

.projects-section-heading {
  margin-bottom: 20px;
}

.projects-section-heading h2 {
  font-size: 24px;
}

.projects-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.project {
  min-height: 184px;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.project:hover {
  transform: translateY(-2px);
  border-color: rgba(6, 182, 212, 0.6);
  box-shadow: 0 26px 70px rgba(37, 99, 235, 0.11);
}

.project:nth-child(3n + 2) {
  transform: translateY(14px);
}

.project:nth-child(3n + 2):hover {
  transform: translateY(12px);
}

.project h3 {
  margin: 0 0 8px;
  font-size: 20px;
}

.project-icon,
.dialog-avatar,
.message-avatar {
  display: grid;
  place-items: center;
  color: var(--cf-blue);
  background: var(--cf-cyan-soft);
}

.project-icon {
  width: 38px;
  height: 38px;
  margin-bottom: 18px;
  border: 1px solid rgba(125, 211, 252, 0.44);
  border-radius: 8px;
}

.project-icon svg,
.empty-project svg {
  width: 19px;
  height: 19px;
}

.empty-project {
  min-height: 184px;
  display: grid;
  place-items: center;
  gap: 8px;
  border: 1px dashed rgba(6, 182, 212, 0.64);
  border-radius: 8px;
  background: rgba(236, 254, 255, 0.44);
  color: var(--cf-blue);
  cursor: pointer;
  font: inherit;
}

.dialog-heading {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dialog-heading h2 {
  margin: 0;
  font-size: 20px;
}

.dialog-heading p {
  margin: 4px 0 0;
  color: var(--cf-muted);
  font-size: 13px;
}

.dialog-avatar {
  width: 36px;
  height: 36px;
  border-radius: 8px;
}

.dialog-avatar svg,
.message-avatar svg {
  width: 18px;
  height: 18px;
}

.conversation {
  display: grid;
  gap: 16px;
  margin-bottom: 20px;
}

.ai-message,
.user-message {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.ai-message > p,
.project-preview,
.user-message p {
  margin: 0;
  padding: 12px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.65;
}

.ai-message > p,
.project-preview {
  background: var(--cf-soft);
  color: var(--cf-text);
}

.user-message {
  justify-content: flex-end;
}

.user-message p {
  max-width: 80%;
  background: var(--cf-blue);
  color: #ffffff;
}

.message-avatar {
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  border-radius: 50%;
}

.project-preview {
  display: grid;
  gap: 5px;
}

.project-preview p {
  margin: 0;
  color: var(--cf-muted);
}

.project-preview span {
  color: #475569;
}

.dialog-composer {
  display: grid;
  gap: 10px;
}

.dialog-composer label {
  color: var(--cf-text);
  font-size: 14px;
  font-weight: 600;
}

.dialog-submit {
  justify-self: end;
}

.form-error {
  margin: 0;
  padding: 9px 10px;
  border-left: 3px solid #dc5a45;
  background: #fff5f2;
  color: #b53d2d;
  font-size: 13px;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 900px) {
  .projects-brief {
    grid-template-columns: 1fr;
  }

  .quick-row {
    justify-content: flex-start;
  }

  .projects-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 780px) {
  .projects-main {
    padding-top: 24px;
  }
}

@media (max-width: 580px) {
  .projects-topbar,
  .projects-section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .projects-brief {
    margin-top: 26px;
    padding: 26px 22px;
  }

  .projects-brief h2 {
    font-size: 30px;
  }

  .projects-grid {
    grid-template-columns: 1fr;
  }

  .project:nth-child(3n + 2) {
    transform: none;
  }
}
</style>
