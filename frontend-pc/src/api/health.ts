import { getJson } from './http'

export interface HealthResponse {
  status: 'ok' | 'error'
  database?: string
}

export const getHealth = () => getJson<HealthResponse>('/health')
export const getDatabaseHealth = () => getJson<HealthResponse>('/health/db')
