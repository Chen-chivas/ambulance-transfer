import { clearSession, token } from './session'

export class ApiError extends Error {
  constructor(message: string, public status: number) { super(message) }
}

export async function request<Response>(path: string, method = 'GET', data?: unknown): Promise<Response> {
  const authenticated = !!token.value && path !== '/auth/login' && !path.startsWith('/health')
  let response: globalThis.Response
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers: { Accept: 'application/json', ...(data === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(authenticated ? { Authorization: `Bearer ${token.value}` } : {}) },
      body: data === undefined ? undefined : JSON.stringify(data),
      signal: AbortSignal.timeout(10000),
    })
  } catch {
    throw new ApiError('无法连接服务，请检查网络或稍后重试', 0)
  }
  const body = response.status === 204 ? undefined : await response.json().catch(() => undefined)
  if (!response.ok) {
    if (response.status === 401 && authenticated) {
      clearSession()
      window.dispatchEvent(new Event('session-expired'))
    }
    throw new ApiError(body?.message || `请求失败（${response.status}）`, response.status)
  }
  return body as Response
}

export const getJson = <Response>(path: string) => request<Response>(path)
export function messageOf(error: unknown) { return error instanceof Error ? error.message : '操作失败，请稍后重试' }
