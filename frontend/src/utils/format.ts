import dayjs from 'dayjs'

export function fmtMoney(v?: string | number | null): string {
  if (v === null || v === undefined || v === '') return '-'
  const n = typeof v === 'string' ? Number(v) : v
  if (Number.isNaN(n)) return '-'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function fmtDate(v?: string | null): string {
  if (!v) return '-'
  return dayjs(v).format('YYYY-MM-DD')
}

export function fmtDateTime(v?: string | null): string {
  if (!v) return '-'
  return dayjs(v).format('YYYY-MM-DD HH:mm')
}

export const STATUS_MAP = {
  EXECUTING: { label: '执行中', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  EXPIRED: { label: '已到期', type: 'warning' },
  VOIDED: { label: '已作废', type: 'info' },
} as const

export const PLAN_STATUS_MAP = {
  UNPAID: { label: '未回款', type: 'info' },
  PARTIAL: { label: '部分回款', type: 'warning' },
  SETTLED: { label: '已结清', type: 'success' },
} as const
