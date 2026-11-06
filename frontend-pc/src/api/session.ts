import { ref } from 'vue'

export type Role = 'ADMIN' | 'DISPATCHER' | 'DRIVER' | 'NURSE'
export interface User { id: number; companyId: number | null; name: string; role: Role; username: string }
const TOKEN_KEY = 'ambulance.access-token'
export const token = ref(sessionStorage.getItem(TOKEN_KEY) || '')
export const currentUser = ref<User | null>(null)

export function setSession(value: string, user: User) {
  sessionStorage.setItem(TOKEN_KEY, value)
  token.value = value
  currentUser.value = user
}

export function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY)
  token.value = ''
  currentUser.value = null
}
