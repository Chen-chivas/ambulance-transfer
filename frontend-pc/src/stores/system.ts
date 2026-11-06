import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDatabaseHealth, getHealth } from '../api/health'

type ConnectionStatus = 'unchecked' | 'ok' | 'error'

export const useSystemStore = defineStore('system', () => {
  const backend = ref<ConnectionStatus>('unchecked')
  const database = ref<ConnectionStatus>('unchecked')
  const backendMessage = ref('尚未检查')
  const databaseMessage = ref('尚未检查')
  const loading = ref(false)
  const checkedAt = ref('')

  async function refresh() {
    if (loading.value) return
    loading.value = true
    try {
      const results = await Promise.allSettled([getHealth(), getDatabaseHealth()])
      const [backendResult, databaseResult] = results
      backend.value = backendResult.status === 'fulfilled' && backendResult.value.status === 'ok' ? 'ok' : 'error'
      database.value = databaseResult.status === 'fulfilled' && databaseResult.value.status === 'ok' ? 'ok' : 'error'
      backendMessage.value = backend.value === 'ok' ? '服务运行正常' : '连接失败，请检查后端服务'
      databaseMessage.value = database.value === 'ok' ? 'ambulance_transfer 连接正常' : '连接失败，请检查数据库配置'
      checkedAt.value = new Date().toLocaleTimeString('zh-CN')
    } finally {
      loading.value = false
    }
  }

  return { backend, database, backendMessage, databaseMessage, loading, checkedAt, refresh }
})
