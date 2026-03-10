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
    console.error('Request Error:', error)
    if (error.response && error.response.status === 403) {
      // 检查当前路径，如果是/product或/sales，不跳转到登录页面
      const currentPath = window.location.pathname
      if (currentPath !== '/product' && currentPath !== '/sales') {
        localStorage.removeItem('token')
        window.location.href = '/login'
      }
    }
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request