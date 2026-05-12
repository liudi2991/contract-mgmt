export interface PageResult<T> {
  total: number
  records: T[]
}

export interface User {
  id: number
  username: string
  name: string
  email?: string
  role: 'SALES' | 'ADMIN'
  status: number
  mustChangePwd: boolean
  lastLoginAt?: string
  createdAt?: string
}

export interface LoginResponse {
  token: string
  expiresIn: number
  user: User
}

export interface Contract {
  id: number
  contractNo: string
  name: string
  customerName: string
  amount: string
  currency: string
  signDate: string
  effectiveDate: string
  expireDate: string
  ownerId: number
  ownerName?: string
  status: 'EXECUTING' | 'COMPLETED' | 'EXPIRED' | 'VOIDED'
  voidReason?: string
  paidAmount: string
  remainingAmount: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface PaymentPlanItem {
  id: number
  contractId: number
  periodNo: number
  plannedDate: string
  plannedAmount: string
  receivedAmount: string
  status: 'UNPAID' | 'PARTIAL' | 'SETTLED'
  remark?: string
}

export interface Payment {
  id: number
  contractId: number
  contractNo?: string
  customerName?: string
  planItemId?: number
  receivedDate: string
  amount: string
  payer?: string
  bankSerial?: string
  remark?: string
  createdBy?: number
  createdByName?: string
  createdAt?: string
  settlements?: Settlement[]
  contractPaidAmount?: string
  contractRemainingAmount?: string
}

export interface Settlement {
  planItemId: number
  periodNo: number
  settledAmount: string
  planItemStatus: string
}

export interface DashboardStats {
  totalCount: number
  totalAmount: string
  paidAmount: string
  remainingAmount: string
  executingCount: number
  expiredCount: number
  completedCount: number
}
