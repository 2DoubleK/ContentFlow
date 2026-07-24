import { http, type ApiResponse } from './http'

export interface Conversation {
  id: number
  projectId: number
  threadId: string
  title: string
  status: 'ACTIVE' | 'ARCHIVED'
  createdAt: string
  updatedAt: string
}

export interface AgentMessage {
  id: number
  conversationId: number
  requestId?: string
  role: 'SYSTEM' | 'USER' | 'ASSISTANT' | 'TOOL'
  content: string
  metadata: string | null
  createdAt?: string
}

export interface SendMessageResponse {
  userMessage: AgentMessage
  assistantMessage: AgentMessage
  savedDraftId: number | null
}

export const conversationApi = {
  create: (payload: { projectId: number; title: string }) =>
    http.post<ApiResponse<Conversation>>('/api/agent/conversations', payload),
  list: (projectId: number) =>
    http.get<ApiResponse<Conversation[]>>(`/api/agent/conversations?projectId=${projectId}`),
  messages: (conversationId: number) =>
    http.get<ApiResponse<AgentMessage[]>>(`/api/agent/conversations/${conversationId}/messages`),
  send: (conversationId: number, content: string, requestId: string) =>
    http.post<ApiResponse<SendMessageResponse>>(`/api/agent/conversations/${conversationId}/messages`, {
      content,
      requestId
    })
}
