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
      ElMessage.error(body.message || '错误码 ' + body.code)
      return Promise.reject(new Error(body.message || 'biz error'))
    }
    return body
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('mirage_token')
      if (location.hash !== '#/login') {
        location.hash = '#/login'
      }
      ElMessage.error('登录已失效，请重新登录')
    } else {
      ElMessage.error(err.response?.data?.message || err.message || '请求失败')
    }
    return Promise.reject(err)
  }
)

export default http
