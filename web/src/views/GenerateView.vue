<template>
  <main class="chat-workspace">
    <aside class="app-rail">
      <div class="rail-brand">C</div>
      <nav class="rail-nav" aria-label="主导航">
        <button class="rail-item" type="button" title="返回项目" @click="router.back()">
          <ArrowLeft />
          <span>返回</span>
        </button>
        <button class="rail-item is-active" type="button" title="AI 创作">
          <MessageSquareText />
          <span>创作</span>
        </button>
      </nav>
    </aside>

    <aside class="conversation-sidebar">
      <header class="sidebar-header">
        <div>
          <span class="eyebrow">CONTENTFLOW</span>
          <h1>AI 创作</h1>
        </div>
        <button class="icon-button" type="button" title="返回项目" @click="router.back()">
          <X />
        </button>
      </header>

      <form class="new-conversation" @submit.prevent="createConversation">
        <input
          v-model="newTitle"
          data-testid="new-title"
          aria-label="新会话标题"
          placeholder="新会话标题"
          maxlength="80"
        />
        <button
          class="icon-button primary"
          data-testid="create-conversation"
          type="button"
          title="新建会话"
          :disabled="creating"
          @click="createConversation"
        >
          <LoaderCircle v-if="creating" class="spin" />
          <Plus v-else />
        </button>
      </form>

      <div class="conversation-list" aria-label="会话列表">
        <button
          v-for="item in conversations"
          :key="item.id"
          class="conversation-item"
          :class="{ 'is-active': item.id === selectedConversationId }"
          :data-conversation-id="item.id"
          type="button"
          @click="selectConversation(item.id)"
        >
          <MessageSquare />
          <span>
            <strong>{{ item.title }}</strong>
            <small>{{ formatTime(item.updatedAt) }}</small>
          </span>
        </button>
        <p v-if="!loadingConversations && conversations.length === 0" class="empty-sidebar">
          暂无会话
        </p>
      </div>
    </aside>

    <section class="chat-panel">
      <header class="chat-header">
        <div>
          <p class="chat-kicker">项目 #{{ projectId }}</p>
          <h2>{{ selectedConversation?.title || '新建会话开始创作' }}</h2>
        </div>
        <button
          v-if="savedDraftId"
          class="draft-button"
          data-testid="open-draft"
          type="button"
          @click="router.push(`/contents/${savedDraftId}`)"
        >
          <FileText />
          查看草稿
        </button>
      </header>

      <div class="message-scroll">
        <div v-if="loadingMessages" class="center-state">
          <LoaderCircle class="spin" />
        </div>
        <div v-else-if="!selectedConversationId" class="center-state empty-state">
          <MessageSquareText />
          <h3>创建一个会话</h3>
          <p>为当前项目开启新的内容创作。</p>
        </div>
        <div v-else-if="messages.length === 0" class="center-state empty-state">
          <Sparkles />
          <h3>准备好了</h3>
          <p>输入你的内容目标。</p>
        </div>
        <div v-else class="message-list" aria-live="polite">
          <article
            v-for="message in messages"
            :key="message.id"
            class="message"
            :class="message.role === 'USER' ? 'is-user' : 'is-assistant'"
          >
            <div class="message-avatar">
              <User v-if="message.role === 'USER'" />
              <Sparkles v-else />
            </div>
            <div class="message-body">
              <strong>{{ message.role === 'USER' ? '你' : 'ContentFlow' }}</strong>
              <div class="message-content">{{ message.content }}</div>
            </div>
          </article>
          <article v-if="sending" class="message is-assistant">
            <div class="message-avatar"><Sparkles /></div>
            <div class="message-body typing"><i></i><i></i><i></i></div>
          </article>
        </div>
      </div>

      <footer class="composer-area">
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <form class="chat-composer" @submit.prevent="sendMessage">
          <textarea
            v-model="composer"
            data-testid="composer"
            aria-label="消息内容"
            placeholder="描述你想创作的内容"
            rows="3"
            :disabled="!selectedConversationId || sending"
            @keydown.ctrl.enter.prevent="sendMessage"
          ></textarea>
          <button
            class="send-button"
            data-testid="send-message"
            type="button"
            title="发送"
            :disabled="!canSend"
            @click="sendMessage"
          >
            <LoaderCircle v-if="sending" class="spin" />
            <Send v-else />
          </button>
        </form>
      </footer>
    </section>
  </main>
</template>

<script setup lang="ts">
import {
  ArrowLeft,
  FileText,
  LoaderCircle,
  MessageSquare,
  MessageSquareText,
  Plus,
  Send,
  Sparkles,
  User,
  X
} from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { conversationApi, type AgentMessage, type Conversation } from '../api/conversations'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.id))
const conversations = ref<Conversation[]>([])
const messages = ref<AgentMessage[]>([])
const selectedConversationId = ref<number | null>(null)
const newTitle = ref('')
const composer = ref('')
const savedDraftId = ref<number | null>(null)
const loadingConversations = ref(false)
const loadingMessages = ref(false)
const creating = ref(false)
const sending = ref(false)
const error = ref('')
let conversationLoadSequence = 0
let messageLoadSequence = 0
let workspaceSequence = 0
const pendingRequests = new Map<number, { content: string; requestId: string }>()

const selectedConversation = computed(() =>
  conversations.value.find((item) => item.id === selectedConversationId.value)
)
const canSend = computed(() => Boolean(selectedConversationId.value && composer.value.trim() && !sending.value))

onMounted(loadConversations)
watch(projectId, () => {
  resetWorkspace()
  void loadConversations()
})

async function loadConversations() {
  const loadingProjectId = projectId.value
  const loadSequence = ++conversationLoadSequence
  loadingConversations.value = true
  error.value = ''
  try {
    const response = await conversationApi.list(loadingProjectId)
    if (loadSequence !== conversationLoadSequence || loadingProjectId !== projectId.value) return
    conversations.value = response.data.data
    if (conversations.value.length > 0) {
      await selectConversation(conversations.value[0].id)
    }
  } catch {
    if (loadSequence === conversationLoadSequence) error.value = '会话列表加载失败'
  } finally {
    if (loadSequence === conversationLoadSequence) loadingConversations.value = false
  }
}

async function selectConversation(conversationId: number) {
  const loadSequence = ++messageLoadSequence
  selectedConversationId.value = conversationId
  loadingMessages.value = true
  savedDraftId.value = null
  error.value = ''
  try {
    const response = await conversationApi.messages(conversationId)
    if (loadSequence !== messageLoadSequence || selectedConversationId.value !== conversationId) return
    messages.value = response.data.data
    savedDraftId.value = latestSavedDraftId(messages.value)
  } catch {
    if (loadSequence === messageLoadSequence) error.value = '消息记录加载失败'
  } finally {
    if (loadSequence === messageLoadSequence) loadingMessages.value = false
  }
}

async function createConversation() {
  const title = newTitle.value.trim()
  if (!title || creating.value) return
  const operationSequence = workspaceSequence
  const operationProjectId = projectId.value
  creating.value = true
  error.value = ''
  try {
    const response = await conversationApi.create({ projectId: operationProjectId, title })
    if (operationSequence !== workspaceSequence || operationProjectId !== projectId.value) return
    conversations.value = [response.data.data, ...conversations.value]
    newTitle.value = ''
    await selectConversation(response.data.data.id)
  } catch {
    if (operationSequence === workspaceSequence) error.value = '新建会话失败'
  } finally {
    if (operationSequence === workspaceSequence) creating.value = false
  }
}

async function sendMessage() {
  const content = composer.value.trim()
  const conversationId = selectedConversationId.value
  if (!content || !conversationId || sending.value) return
  const operationSequence = workspaceSequence
  const pending = pendingRequests.get(conversationId)
  const requestId = pending?.content === content ? pending.requestId : createRequestId()
  pendingRequests.set(conversationId, { content, requestId })
  sending.value = true
  error.value = ''
  try {
    const response = await conversationApi.send(conversationId, content, requestId)
    if (operationSequence !== workspaceSequence) return
    pendingRequests.delete(conversationId)
    if (selectedConversationId.value === conversationId) {
      messages.value.push(response.data.data.userMessage, response.data.data.assistantMessage)
      savedDraftId.value = response.data.data.savedDraftId
      composer.value = ''
    }
    const current = conversations.value.find((item) => item.id === conversationId)
    if (current) current.updatedAt = new Date().toISOString()
  } catch {
    if (operationSequence === workspaceSequence) {
      error.value = '生成失败，已保留你的消息，请稍后重试'
      if (selectedConversationId.value === conversationId) {
        await reloadMessagesAfterFailure(conversationId, operationSequence)
      }
    }
  } finally {
    if (operationSequence === workspaceSequence) sending.value = false
  }
}

function resetWorkspace() {
  workspaceSequence += 1
  conversationLoadSequence += 1
  messageLoadSequence += 1
  pendingRequests.clear()
  conversations.value = []
  messages.value = []
  selectedConversationId.value = null
  savedDraftId.value = null
  newTitle.value = ''
  composer.value = ''
  creating.value = false
  sending.value = false
  error.value = ''
}

function createRequestId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function reloadMessagesAfterFailure(conversationId: number, operationSequence: number) {
  try {
    const response = await conversationApi.messages(conversationId)
    if (operationSequence === workspaceSequence && selectedConversationId.value === conversationId) {
      messages.value = response.data.data
    }
  } catch {
    // Keep the original generation error visible.
  }
}

function latestSavedDraftId(items: AgentMessage[]) {
  for (let index = items.length - 1; index >= 0; index -= 1) {
    const metadata = items[index].metadata
    if (!metadata) continue
    try {
      const parsed = JSON.parse(metadata) as { savedDraftId?: number }
      if (parsed.savedDraftId) return parsed.savedDraftId
    } catch {
      continue
    }
  }
  return null
}

function formatTime(value: string) {
  if (!value) return ''
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(new Date(value))
}
</script>

<style scoped>
.chat-workspace {
  height: 100vh;
  min-height: 620px;
  display: grid;
  grid-template-columns: 76px 286px minmax(0, 1fr);
  overflow: hidden;
  background: #f7fbff;
}

.app-rail {
  position: static;
  height: 100%;
  border-right: 1px solid #dbeafe;
  background: #ffffff;
}

.rail-item {
  border: 0;
  cursor: pointer;
}

.conversation-sidebar {
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 22px 16px;
  border-right: 1px solid #dbeafe;
  background: #f0f9ff;
}

.sidebar-header,
.chat-header,
.new-conversation,
.conversation-item,
.message,
.chat-composer {
  display: flex;
  align-items: center;
}

.sidebar-header,
.chat-header {
  justify-content: space-between;
  gap: 16px;
}

.eyebrow,
.chat-kicker {
  margin: 0 0 4px;
  color: #0891b2;
  font-size: 11px;
  font-weight: 700;
}

.sidebar-header h1,
.chat-header h2 {
  margin: 0;
  letter-spacing: 0;
}

.sidebar-header h1 {
  font-size: 21px;
}

.icon-button,
.send-button {
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  display: grid;
  place-items: center;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #ffffff;
  color: #1e40af;
  cursor: pointer;
}

.icon-button svg,
.send-button svg,
.draft-button svg {
  width: 18px;
  height: 18px;
}

.icon-button.primary,
.send-button {
  border-color: #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.icon-button:disabled,
.send-button:disabled {
  cursor: not-allowed;
  opacity: 0.48;
}

.new-conversation {
  gap: 8px;
  margin: 22px 0 16px;
}

.new-conversation input {
  width: 100%;
  min-width: 0;
  height: 38px;
  padding: 0 11px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  outline: none;
  background: #ffffff;
}

.new-conversation input:focus,
.chat-composer:focus-within {
  border-color: #06b6d4;
  box-shadow: 0 0 0 3px rgba(6, 182, 212, 0.12);
}

.conversation-list {
  min-height: 0;
  display: grid;
  align-content: start;
  gap: 6px;
  overflow-y: auto;
}

.conversation-item {
  width: 100%;
  gap: 10px;
  padding: 11px;
  border: 1px solid transparent;
  border-radius: 8px;
  color: #334155;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.conversation-item:hover,
.conversation-item.is-active {
  border-color: #bae6fd;
  background: #ffffff;
  color: #0f172a;
}

.conversation-item > svg {
  width: 17px;
  flex: 0 0 17px;
  color: #0891b2;
}

.conversation-item span {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.conversation-item strong {
  overflow: hidden;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-item small,
.empty-sidebar {
  color: #64748b;
  font-size: 12px;
}

.empty-sidebar {
  padding: 18px 10px;
  text-align: center;
}

.chat-panel {
  min-width: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  background: #ffffff;
}

.chat-header {
  min-height: 80px;
  padding: 16px 28px;
  border-bottom: 1px solid #e2e8f0;
}

.chat-header h2 {
  max-width: min(620px, 55vw);
  overflow: hidden;
  font-size: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.draft-button {
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 14px;
  border: 1px solid #a5f3fc;
  border-radius: 8px;
  color: #155e75;
  background: #ecfeff;
  cursor: pointer;
}

.message-scroll {
  min-height: 0;
  overflow-y: auto;
}

.message-list {
  width: min(820px, calc(100% - 48px));
  margin: 0 auto;
  padding: 30px 0;
}

.message {
  align-items: flex-start;
  gap: 13px;
  padding: 18px 0;
  border-bottom: 1px solid #edf2f7;
}

.message-avatar {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  color: #ffffff;
  background: #2563eb;
}

.is-assistant .message-avatar {
  background: #0891b2;
}

.message-avatar svg {
  width: 16px;
  height: 16px;
}

.message-body {
  min-width: 0;
  flex: 1;
}

.message-body > strong {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
}

.message-content {
  overflow-wrap: anywhere;
  color: #1f2937;
  font-size: 15px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.center-state {
  height: 100%;
  min-height: 300px;
  display: grid;
  place-content: center;
  justify-items: center;
  color: #0891b2;
}

.empty-state svg {
  width: 34px;
  height: 34px;
}

.empty-state h3 {
  margin: 14px 0 5px;
  color: #0f172a;
  font-size: 18px;
}

.empty-state p {
  margin: 0;
  color: #64748b;
}

.composer-area {
  padding: 12px 28px 22px;
  border-top: 1px solid #e2e8f0;
  background: #ffffff;
}

.chat-composer {
  width: min(820px, 100%);
  margin: 0 auto;
  align-items: flex-end;
  gap: 12px;
  padding: 12px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #ffffff;
}

.chat-composer textarea {
  min-width: 0;
  min-height: 66px;
  flex: 1;
  padding: 4px;
  border: 0;
  outline: 0;
  resize: none;
  line-height: 1.55;
}

.error {
  width: min(820px, 100%);
  margin: 0 auto 8px;
  color: #b91c1c;
  font-size: 13px;
}

.typing {
  display: flex;
  gap: 5px;
  padding-top: 10px;
}

.typing i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #06b6d4;
  animation: pulse 1s infinite alternate;
}

.typing i:nth-child(2) { animation-delay: 0.15s; }
.typing i:nth-child(3) { animation-delay: 0.3s; }

.spin {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes pulse {
  to { opacity: 0.3; transform: translateY(-2px); }
}

@media (max-width: 900px) {
  .chat-workspace {
    grid-template-columns: 64px 230px minmax(0, 1fr);
  }

  .conversation-sidebar {
    padding-right: 10px;
    padding-left: 10px;
  }

  .chat-header,
  .composer-area {
    padding-right: 18px;
    padding-left: 18px;
  }
}

@media (max-width: 680px) {
  .chat-workspace {
    height: auto;
    min-height: 100vh;
    grid-template-columns: 1fr;
    grid-template-rows: auto auto minmax(620px, 1fr);
    overflow: visible;
  }

  .app-rail {
    height: 62px;
    flex-direction: row;
    justify-content: space-between;
    padding: 10px 14px;
    border-right: 0;
    border-bottom: 1px solid #dbeafe;
  }

  .rail-nav {
    display: flex;
    margin: 0;
  }

  .conversation-sidebar {
    max-height: 285px;
    border-right: 0;
    border-bottom: 1px solid #dbeafe;
  }

  .conversation-list {
    max-height: 130px;
  }

  .message-list {
    width: calc(100% - 28px);
  }

  .chat-header h2 {
    max-width: 62vw;
  }
}
</style>
