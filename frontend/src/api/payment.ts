import { del, get, post } from '@/utils/request'
import type { PageResult, Payment, PaymentPlanItem } from './types'

export interface PaymentPlanGenerate {
  mode: 'MANUAL' | 'RATIO' | 'UNIFORM'
  periods?: number
  ratios?: number[]
  intervalDays?: number
  firstDate?: string
  items?: Array<{
    periodNo: number
    plannedDate: string
    plannedAmount: number | string
    remark?: string
  }>
}

export interface PaymentForm {
  contractId: number
  planItemId?: number | null
  receivedDate: string
  amount: number | string
  payer?: string
  bankSerial?: string
  remark?: string
}

export const paymentPlanApi = {
  list: (contractId: number) =>
    get<PaymentPlanItem[]>(`/contracts/${contractId}/payment-plan`),
  generate: (contractId: number, data: PaymentPlanGenerate) =>
    post<PaymentPlanItem[]>(`/contracts/${contractId}/payment-plan`, data),
}

export const paymentApi = {
  page: (params: { page?: number; size?: number; contractId?: number; dateFrom?: string; dateTo?: string }) =>
    get<PageResult<Payment>>('/payments', params as never),
  create: (data: PaymentForm) => post<Payment>('/payments', data),
  detail: (id: number) => get<Payment>(`/payments/${id}`),
  delete: (id: number) => del(`/payments/${id}`),
}
