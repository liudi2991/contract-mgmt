import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import type { User } from '@/api/types'

const TOKEN_KEY = 'contract-mgmt-token'
const USER_KEY = 'contract-mgmt-user'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null') as User | null,
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    isAdmin: (s) => s.user?.role === 'ADMIN',
    mustChangePwd: (s) => !!s.user?.mustChangePwd,
  },
  actions: {
    async login(username: string, password: string) {
      const resp = await authApi.login({ username, password })
      this.token = resp.data.token
      this.user = resp.data.user
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },

    async logout() {
      try {
        await authApi.logout()
      } catch (_) {}
      this.clear()
    },

    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },

    async fetchMe() {
      const resp = await authApi.me()
      this.user = resp.data
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },

    setPasswordChanged() {
      if (this.user) {
        this.user.mustChangePwd = false
        localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      }
    },
  },
})
