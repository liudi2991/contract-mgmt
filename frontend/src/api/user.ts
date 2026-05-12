import { get } from '@/utils/request'
import type { User } from './types'

export const userApi = {
  options: () => get<User[]>('/users/options'),
}
