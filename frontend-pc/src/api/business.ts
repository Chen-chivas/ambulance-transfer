import { request } from './http'

export interface Page<T> { items: T[]; total: number; page: number; size: number }
export type AuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED'
export interface CompanyInput { name: string; licenseNo: string; licenseExpireAt: string | null; contactPhone: string }
export interface Company extends CompanyInput { id: number; auditStatus: AuditStatus; auditRemark: string | null; createdAt: string; updatedAt: string }
export interface VehicleInput { plateNo: string; serviceLevel: 'BASIC' | 'MONITORING'; status: 'IDLE' | 'MAINTENANCE' }
export interface Vehicle { id: number; companyId: number; plateNo: string; serviceLevel: 'BASIC' | 'MONITORING'; status: 'IDLE' | 'TASK' | 'MAINTENANCE' }
export interface Overview { companies: number; pending: number; approved: number; vehicles: number; idleVehicles: number }
export const auditLabels: Record<AuditStatus, string> = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回', EXPIRED: '资质过期' }
export const auditTypes = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', EXPIRED: 'info' } as const
export const vehicleLabels = { IDLE: '空闲', TASK: '执行任务', MAINTENANCE: '维修中' }
export function query(values: Record<string, string | number>) { return new URLSearchParams(Object.entries(values).map(([key, value]) => [key, String(value)])).toString() }
export const listCompanies = (values: Record<string, string | number>) => request<Page<Company>>(`/admin/companies?${query(values)}`)
export const saveCompany = (id: number | null, value: CompanyInput) => request<Company>(`/admin/companies${id ? `/${id}` : ''}`, id ? 'PUT' : 'POST', value)
export const vehicleBase = (companyId?: number) => companyId ? `/admin/companies/${companyId}/vehicles` : '/company/vehicles'
