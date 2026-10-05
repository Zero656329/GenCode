import axios, { type AxiosRequestConfig } from 'axios'
import { message } from 'ant-design-vue'
import { TOKEN_KEY } from '@/settings'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

let redirecting = false

/** 清除凭证并跳转登录页（携带 redirect） */
function redirectToLogin(): void {
  clearToken()
  const { pathname, search } = window.location
  if (pathname === '/login') return
  if (redirecting) return
  redirecting = true
  window.location.href = `/login?redirect=${encodeURIComponent(pathname + search)}`
}

const service = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截：附加 Bearer Token
service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：解包 R<T>；code=0 返回 data；401 跳登录；其他报错提示
service.interceptors.response.use(
  (response): any => {
    const res = response.data as R<unknown>
    if (res.code === 0) {
      return res.data
    }
    if (res.code === 401) {
      message.error('登录已过期，请重新登录')
      redirectToLogin()
    } else {
      message.error(res.msg || '请求失败')
    }
    return Promise.reject(new Error(res.msg || '请求失败'))
  },
  (error) => {
    if (error && error.response) {
      const status: number = error.response.status
      const data = error.response.data as R<unknown> | undefined
      if (status === 401) {
        message.error('登录已过期，请重新登录')
        redirectToLogin()
      } else {
        message.error((data && data.msg) || `请求失败（${status}）`)
      }
    } else {
      message.error('网络异常，请检查网络或稍后重试')
    }
    return Promise.reject(error)
  }
)

/** 通用请求，直接解包返回 data */
export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return service.request(config) as unknown as Promise<T>
}

export function get<T = unknown>(url: string, params?: object): Promise<T> {
  return request<T>({ url, method: 'get', params })
}

export function post<T = unknown>(url: string, data?: unknown): Promise<T> {
  return request<T>({ url, method: 'post', data })
}

export function put<T = unknown>(url: string, data?: unknown): Promise<T> {
  return request<T>({ url, method: 'put', data })
}

export function del<T = unknown>(url: string): Promise<T> {
  return request<T>({ url, method: 'delete' })
}
