import { get, post } from '@/utils/request'
import type { LoginResponse, User } from './types'

export const authApi = {
  login: (data: { username: string; password: string }) =>
    post<LoginResponse>('/auth/login', data),

  logout: () => post('/auth/logout'),

  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    post('/auth/change-password', data),

  me: () => get<User>('/auth/me'),
}
