import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    // 返回数据部分
    return response.data
  },
  error => {
    const skipMsg = (error.config as any)?.skipErrorMsg
    const skipLog = (error.config as any)?.skipErrorLog || skipMsg
    if (!skipLog) {
      console.error('Request Error:', error)
    }
    const status = error.response?.status
    // 仅 401 视为登录态失效；403 多为业务权限不足，不应清空 token 导致反复登录
    const isAuthError = status === 401
    if (isAuthError) {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      localStorage.removeItem('companyTitle')
      window.location.href = '/login'
    }
    // 若请求配置了 skipErrorMsg，则不弹出全局错误（如仅用于展示的 partner-info/by-username）
    if (!skipMsg) {
      const responseData = error.response?.data
      const backendMessage =
        (responseData && typeof responseData === 'object' && 'message' in responseData && (responseData as any).message)
          ? String((responseData as any).message)
          : (typeof responseData === 'string' ? responseData : '')
      ElMessage.error(backendMessage || error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request