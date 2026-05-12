import { get, post, put } from '@/utils/request'
import type { Contract, DashboardStats, PageResult } from './types'

export interface ContractQuery {
  page?: number
  size?: number
  keyword?: string
  customerName?: string
  ownerId?: number
  status?: string
  signDateFrom?: string
  signDateTo?: string
  expireDateFrom?: string
  expireDateTo?: string
}

export interface ContractFormData {
  name: string
  customerName: string
  amount: number | string
  currency?: string
  signDate: string
  effectiveDate: string
  expireDate: string
  ownerId: number
  remark?: string
}

export const contractApi = {
  page: (params: ContractQuery) => get<PageResult<Contract>>('/contracts', params as never),
  detail: (id: number) => get<Contract>(`/contracts/${id}`),
  create: (data: ContractFormData) => post<Contract>('/contracts', data),
  update: (id: number, data: ContractFormData) => put<Contract>(`/contracts/${id}`, data),
  voidContract: (id: number, reason: string) =>
    post(`/contracts/${id}/void`, { reason }),
  dashboard: () => get<DashboardStats>('/contracts/dashboard'),
}
