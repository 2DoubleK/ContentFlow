import { http, type ApiResponse } from './http'

export interface Project {
  id: number
  name: string
  description?: string
}

export interface DocumentItem {
  id: number
  projectId: number
  filename: string
  status: string
  errorMessage?: string
}

export interface ContentItem {
  id: number
  projectId: number
  title: string
  summary?: string
  markdown: string
}

export const projectApi = {
  list: () => http.get<ApiResponse<Project[]>>('/api/projects'),
  create: (payload: Pick<Project, 'name' | 'description'>) => http.post<ApiResponse<Project>>('/api/projects', payload),
  detail: (id: number) => http.get<ApiResponse<Project>>(`/api/projects/${id}`),
  documents: (id: number) => http.get<ApiResponse<DocumentItem[]>>(`/api/projects/${id}/documents`),
  uploadDocument: (id: number, file: File) => {
    const data = new FormData()
    data.append('file', file)
    return http.post<ApiResponse<DocumentItem>>(`/api/projects/${id}/documents`, data)
  },
  generate: (id: number, prompt: string) => http.post<ApiResponse<ContentItem>>(`/api/projects/${id}/generate`, { prompt }),
  contents: (id: number) => http.get<ApiResponse<ContentItem[]>>(`/api/projects/${id}/contents`),
  content: (id: number) => http.get<ApiResponse<ContentItem>>(`/api/contents/${id}`)
}
