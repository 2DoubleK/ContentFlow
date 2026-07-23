import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from './auth'

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('persists token and user when session is set', () => {
    const store = useAuthStore()
    store.setSession({ token: 'abc', userId: 1, username: 'demo' })

    expect(localStorage.getItem('contentflow_token')).toBe('abc')
    expect(store.user?.username).toBe('demo')
  })

  it('clears persisted session on logout', () => {
    const store = useAuthStore()
    store.setSession({ token: 'abc', userId: 1, username: 'demo' })

    store.logout()

    expect(store.token).toBe('')
    expect(localStorage.getItem('contentflow_token')).toBeNull()
  })
})
