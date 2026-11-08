import { defineStore } from 'pinia'
import { api } from '../api'

export const TOKEN_KEY = 'mirage_token'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
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
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem('mirage_uid', String(this.userId))
      localStorage.setItem('mirage_user', this.username)
      localStorage.setItem('mirage_admin', this.isAdmin ? '1' : '0')
    },
    logout() {
      this.token = ''
      this.userId = 0
      this.username = ''
      this.isAdmin = false
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('mirage_uid')
      localStorage.removeItem('mirage_user')
      localStorage.removeItem('mirage_admin')
    },
    /** 从 localStorage 重建状态（另一标签页登入/登出后同步用） */
    syncFromStorage() {
      this.token = localStorage.getItem(TOKEN_KEY) || ''
      this.userId = localStorage.getItem('mirage_uid') || ''
      this.username = localStorage.getItem('mirage_user') || ''
      this.isAdmin = localStorage.getItem('mirage_admin') === '1'
    }
  }
})

// ===== 多标签页同步 =====
// storage 事件只在其他标签页修改 localStorage 时触发（本标签写入不触发，天然去重）。
// 另一标签登出 → 本标签同步登出并跳登录页；另一标签登入 → 同步 token/身份。
let storageBound = false
function bindCrossTabSync() {
  if (storageBound || typeof window === 'undefined') return
  storageBound = true
  window.addEventListener('storage', (e) => {
    if (e.key !== TOKEN_KEY) return
    try {
      const store = useAuthStore()
      const loggedOut = !e.newValue
      store.syncFromStorage()
      if (loggedOut && store.token === '') {
        // 动态 import 打破 auth→router→auth 循环依赖（与 client.js 同款做法）
        import('../router').then(({ default: r }) => {
          if (r.currentRoute.value.name !== 'login') {
            r.replace({ name: 'login', query: { redirect: r.currentRoute.value.fullPath } })
          }
        }).catch(() => {})
      }
    } catch (err) {
      // pinia 未就绪等极端时序：忽略本次事件，等下一标签操作再同步
    }
  })
}
bindCrossTabSync()
