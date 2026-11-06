import { query, type Page } from './business'
import { request } from './http'

export const orderLabels = { PENDING_QUOTE: '等待报价', QUOTING: '报价中', CONFIRMED: '已确认', DISPATCHED: '已派车', EN_ROUTE: '前往上车点', ARRIVED: '已到上车点', ON_BOARD: '患者已上车', COMPLETED: '已完成', CANCELLED: '已取消' }
export type OrderStatus = keyof typeof orderLabels
export type OrderMode = 'MOCK' | 'ADMIN' | 'COMPANY' | 'DISPATCH' | 'DRIVER'
export const quoteLabels = { PENDING: '待选择', WON: '已中标', LOST: '未中标' }
export interface TransferOrder {
  id: number; orderNo: string; userName: string; userPhone: string; originAddress: string; destAddress: string
  serviceLevel: 'BASIC' | 'MONITORING'; patientNote: string | null; status: OrderStatus
  assignedCompanyId: number | null; assignedCompanyName: string | null; finalAmount: number | null
  assignedVehicleId: number | null; assignedDriverId: number | null; assignedNurseId: number | null
  assignedPlateNo: string | null; assignedDriverName: string | null; assignedNurseName: string | null
  myQuoteId: number | null; myAmount: number | null; myQuoteStatus: keyof typeof quoteLabels | null
  createdAt: string; updatedAt: string
}
export interface Quote { id: number; companyId: number; companyName: string; amount: number; remark: string | null; status: keyof typeof quoteLabels; eligible: boolean; updatedAt: string }
export interface OrderLog { id: number; fromStatus: OrderStatus | null; toStatus: OrderStatus; eventType: 'STATUS_CHANGE' | 'FEE_CHANGE'; remark: string; createdAt: string }
export interface OrderDetail { order: TransferOrder; quotes: Quote[]; timeline: OrderLog[] }
export const orderBase = (mode: OrderMode) => mode === 'MOCK' ? '/mock/orders' : mode === 'ADMIN' ? '/admin/orders' : mode === 'DRIVER' ? '/driver/tasks' : '/company/orders'
export const listOrders = (mode: OrderMode, tab: string, params: Record<string, string | number>) => request<Page<TransferOrder>>(`${orderBase(mode)}${mode === 'COMPANY' ? `/${tab}` : mode === 'DISPATCH' ? '/assigned' : ''}?${query(params)}`)
export function money(value: number | null) { return value === null ? '—' : `¥${value.toFixed(2)}` }
export function orderTime(value: string) { return new Date(/(?:Z|[+-]\d\d:\d\d)$/.test(value) ? value : `${value}Z`).toLocaleString('zh-CN', { hour12: false }) }
export function accepting(status: OrderStatus) { return status === 'PENDING_QUOTE' || status === 'QUOTING' }
