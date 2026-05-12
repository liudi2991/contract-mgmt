import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
}

const service: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

service.interceptors.response.use(
  (resp) => {
    const body = resp.data as ApiResult
    if (body && typeof body.code === 'number') {
      if (body.code === 0) return body
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(new Error(body.message || 'API error'))
    }
    return resp.data
  },
  (err) => {
    const status = err.response?.status
    const body = err.response?.data as { code?: number; message?: string } | undefined
    const bizCode = body?.code
    const message = body?.message || err.message || '请求失败'

    if (status === 401) {
      const SESSION_EXPIRED_CODE = 20001
      const isLoginRequest = (err.config?.url || '').includes('/auth/login')
      const isSessionExpired =
        bizCode === SESSION_EXPIRED_CODE || (bizCode === undefined && !isLoginRequest)

      if (isSessionExpired) {
        const auth = useAuthStore()
        auth.clear()
        if (router.currentRoute.value.path !== '/login') {
          router.replace('/login')
        }
        ElMessage.warning('登录已过期，请重新登录')
      } else {
        ElMessage.error(message)
      }
    } else if (status === 403) {
      ElMessage.error(message || '无权访问该资源')
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(err)
  },
)

export default service

export function get<T>(url: string, params?: Record<string, unknown>) {
  return service.get<unknown, ApiResult<T>>(url, { params })
}

export function post<T>(url: string, data?: unknown) {
  return service.post<unknown, ApiResult<T>>(url, data)
}

export function put<T>(url: string, data?: unknown) {
  return service.put<unknown, ApiResult<T>>(url, data)
}

export function del<T>(url: string) {
  return service.delete<unknown, ApiResult<T>>(url)
}
