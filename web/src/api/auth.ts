import { http, type ApiResponse } from './http'

export interface AuthResponse {
  token: string
  userId: number
  username: string
}

export function login(username: string, password: string) {
  return http.post<ApiResponse<AuthResponse>>('/api/auth/login', { username, password })
}

export interface RegisterRequest {
  username: string
  password: string
  confirmPassword: string
  email: string
  phone: string
}

export function register(request: RegisterRequest) {
  return http.post<ApiResponse<AuthResponse>>('/api/auth/register', request)
}
