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

// 401 去重标记（模块级）：同一批失效请求只走一次登出/跳转
let redirecting401 = false

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body
      }
      if (!resp.config || !resp.config.silent) {
        ElMessage.error({ message: body.message || '错误码 ' + body.code, grouping: true })
      }
      return Promise.reject(new Error(body.message || 'biz error'))
    }
    return body
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      // 并发多请求同时 401 只处理一次，避免 toast 风暴与重复跳转
      if (!redirecting401) {
        redirecting401 = true
        // 跳转完成后允许下一次 401 再触发（如再次会话过期）
        setTimeout(() => { redirecting401 = false }, 2000)
        // 清 Pinia 状态 + 走 router 守卫（动态 import 打破 client→store→api→client 循环依赖；
        // 仅清 localStorage 会导致 auth store 残留旧值，路由守卫仍放行受保护页）
        import('../store/auth').then(({ useAuthStore }) => useAuthStore().logout()).catch(() => {})
        import('../router').then(({ default: r }) => {
          if (r.currentRoute.value.name !== 'login') {
            r.replace({ name: 'login', query: { redirect: r.currentRoute.value.fullPath } })
          }
        }).catch(() => {})
        ElMessage.error({ message: '登录已失效，请重新登录', grouping: true })
      }
    } else if (!err.config || !err.config.silent) {
      ElMessage.error({ message: friendlyError(err), grouping: true })
    }
    return Promise.reject(err)
  }
)

/**
 * 请求竞态防护：包装一个异步取数函数，内部自增序号；
 * 仅当本次是最新一次调用时才返回结果，过期结果返回 null（调用方跳过回写）。
 * 用于列表/看板等热点加载：快速切项目或连续翻页时，旧响应不会覆盖新数据。
 *
 * 用法：const fetchList = createGuard((pid) => api.projects.list(pid))
 *       const res = await fetchList(pid); if (res) list.value = res.data
 */
export function createGuard(fetcher) {
  let seq = 0
  return async (...args) => {
    const my = ++seq
    const res = await fetcher(...args)
    if (my !== seq) return null
    return res
  }
}

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
