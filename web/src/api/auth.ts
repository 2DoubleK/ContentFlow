import { http, type ApiResponse } from './http'

export interface AuthResponse {
  token: string
  userId: number
  username: string
}

export function login(username: string, password: string) {
  return http.post<ApiResponse<AuthResponse>>('/api/auth/login', { username, password })
}

export function register(username: string, password: string) {
  return http.post<ApiResponse<AuthResponse>>('/api/auth/register', { username, password })
}
