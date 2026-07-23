import { defineStore } from 'pinia'
import { api } from '../api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('mirage_token') || '',
    // userId 以字符串保存：雪花 id 超过 JS Number.MAX_SAFE_INTEGER，转 Number 会丢精度
    userId: localStorage.getItem('mirage_uid') || '',
    username: localStorage.getItem('mirage_user') || '',
    isAdmin: localStorage.getItem('mirage_admin') === '1'
  }),
  getters: {
    logged: (s) => !!s.token
  },
  actions: {
    async login(username, password) {
      const res = await api.auth.login(username, password)
      const d = res.data
      this.token = d.token
      this.userId = d.userId
      this.username = d.username
      this.isAdmin = !!d.admin
      localStorage.setItem('mirage_token', this.token)
      localStorage.setItem('mirage_uid', String(this.userId))
      localStorage.setItem('mirage_user', this.username)
      localStorage.setItem('mirage_admin', this.isAdmin ? '1' : '0')
    },
    logout() {
      this.token = ''
      this.userId = 0
      this.username = ''
      this.isAdmin = false
      localStorage.removeItem('mirage_token')
      localStorage.removeItem('mirage_uid')
      localStorage.removeItem('mirage_user')
      localStorage.removeItem('mirage_admin')
    }
  }
})
