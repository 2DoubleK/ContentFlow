import { defineStore } from 'pinia'
import { login, register, type AuthResponse } from '../api/auth'

const TOKEN_KEY = 'contentflow_token'
const USER_KEY = 'contentflow_user'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser()
  }),
  getters: {
    isAuthed: (state) => Boolean(state.token)
  },
  actions: {
    setSession(data: AuthResponse) {
      this.token = data.token
      this.user = { id: data.userId, username: data.username }
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },
    async login(username: string, password: string) {
      const response = await login(username, password)
      this.setSession(response.data.data)
    },
    async register(username: string, password: string) {
      const response = await register(username, password)
      this.setSession(response.data.data)
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})

function readUser(): { id: number; username: string } | null {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? JSON.parse(raw) : null
}
