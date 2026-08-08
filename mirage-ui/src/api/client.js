import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 20000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('mirage_token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body
      }
      if (!resp.config || !resp.config.silent) {
        ElMessage.error(body.message || '错误码 ' + body.code)
      }
      return Promise.reject(new Error(body.message || 'biz error'))
    }
    return body
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      // 清 Pinia 状态 + 走 router 守卫（动态 import 打破 client→store→api→client 循环依赖；
      // 仅清 localStorage 会导致 auth store 残留旧值，路由守卫仍放行受保护页）
      import('../store/auth').then(({ useAuthStore }) => useAuthStore().logout()).catch(() => {})
      import('../router').then(({ default: r }) => {
        if (r.currentRoute.value.name !== 'login') {
          r.replace({ name: 'login', query: { redirect: r.currentRoute.value.fullPath } })
        }
      }).catch(() => {})
      ElMessage.error('登录已失效，请重新登录')
    } else if (!err.config || !err.config.silent) {
      ElMessage.error(friendlyError(err))
    }
    return Promise.reject(err)
  }
)

/** 把 axios 原生错误转成对用户友好的中文提示 */
function friendlyError(err) {
  if (err.response) {
    const msg = err.response.data && err.response.data.message
    if (msg) return msg
    const s = err.response.status
    if (s >= 500) return '服务器异常（' + s + '），请联系管理员或查看服务端日志'
    if (s === 403) return '没有权限执行该操作'
    if (s === 404) return '资源不存在'
    return '请求失败（' + s + '）'
  }
  if (err.code === 'ECONNABORTED') return '请求超时，请稍后重试'
  if (err.message === 'Network Error') return '网络不可用，请检查连接'
  return err.message || '请求失败'
}

export default http
