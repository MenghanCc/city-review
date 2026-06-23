import axios from 'axios'
import router from '../router'

// 创建 Axios 实例，baseURL 为 /api（开发时代理到 8081，生产环境中同一域名）
const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：自动注入 Token
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = token
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器：统一错误处理
request.interceptors.response.use(
  response => {
    const data = response.data
    if (data.code === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    return response
  },
  error => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export default request
