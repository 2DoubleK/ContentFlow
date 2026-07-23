import axios from 'axios'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || ''
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('contentflow_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}
