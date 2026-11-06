<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { navigation } from './router'
import { useSystemStore } from './stores/system'
import { clearSession, currentUser } from './api/session'

const route = useRoute()
const router = useRouter()
const system = useSystemStore()
const visibleNavigation = computed(() => navigation.filter(item => currentUser.value && item.roles.includes(currentUser.value.role)))
const roleLabel = computed(() => ({ ADMIN: '平台管理员', DISPATCHER: '车行调度员', DRIVER: '司机', NURSE: '护士' })[currentUser.value?.role || 'ADMIN'])
function logout() { clearSession(); void router.replace('/login') }
onMounted(() => system.refresh())
</script>

<template>
  <RouterView v-if="route.path === '/login'" />
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">+</span><div>安心转运<small>救护车调度工作台</small></div></div>
      <div class="nav-label">工作空间</div>
      <nav aria-label="主导航">
        <RouterLink v-for="(item, index) in visibleNavigation" :key="item.path" :to="item.path" class="nav-item">
          <span class="nav-number">{{ String(index + 1).padStart(2, '0') }}</span>{{ item.title }}<small v-if="item.pending">待开发</small>
        </RouterLink>
      </nav>
      <div class="sidebar-footer"><span class="status-dot"></span> {{ roleLabel }}工作空间</div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <div>工作台 <span class="separator">/</span> {{ route.meta.title }}</div>
        <div class="account-actions"><span>{{ currentUser?.name }}</span><el-tag effect="plain">{{ roleLabel }}</el-tag><el-button text @click="logout">退出登录</el-button></div>
      </header>
      <main><RouterView /></main>
      <footer>救护车转运调度系统 · PC 端优先，共用后端服务</footer>
    </div>
  </div>
</template>
