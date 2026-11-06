import { createRouter, createWebHistory } from 'vue-router'
import type { Role, User } from '../api/session'
import { clearSession, currentUser, token } from '../api/session'
import { ApiError, request } from '../api/http'

export const navigation: { path: string; title: string; roles: Role[]; description: string; pending?: boolean }[] = [
  { path: '/admin/dashboard', title: '系统概览', roles: ['ADMIN'], description: '查看车行、车辆统计与服务状态。' },
  { path: '/admin/companies', title: '车行管理', roles: ['ADMIN'], description: '车行资料、资质审核与车辆维护。' },
  { path: '/admin/orders', title: '订单监管', roles: ['ADMIN'], description: '查询订单、报价与状态记录。' },
  { path: '/company/vehicles', title: '车辆管理', roles: ['DISPATCHER'], description: '维护本车行车辆资料与状态。' },
  { path: '/company/orders', title: '订单池', roles: ['DISPATCHER'], description: '匹配转运需求，提交报价并查看选标结果。' },
  { path: '/company/staff', title: '人员管理', roles: ['DISPATCHER'], description: '维护司机、护士及证书资料。' },
  { path: '/company/dispatch', title: '派车调度', roles: ['DISPATCHER'], description: '分配车辆、司机与护士并跟进转运。' },
  { path: '/driver/tasks', title: '司机任务', roles: ['DRIVER'], description: '记录出发、到达、上车和送达节点。' },
  { path: '/mock/order', title: '模拟下单', roles: ['ADMIN'], description: '模拟转运下单、等待报价与选择车行。' },
]

export function homeFor(role?: Role) { return role === 'ADMIN' ? '/admin/dashboard' : role === 'DISPATCHER' ? '/company/vehicles' : role === 'DRIVER' ? '/driver/tasks' : '/forbidden' }

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: () => import('../views/Login.vue'), meta: { title: '登录', public: true } },
    ...navigation.map((item) => ({
      path: item.path,
      component: item.path === '/admin/dashboard' ? () => import('../views/admin/Dashboard.vue')
        : item.path === '/admin/companies' ? () => import('../views/admin/Companies.vue')
        : item.path === '/company/vehicles' ? () => import('../views/company/Vehicles.vue')
        : item.path === '/company/orders' ? () => import('../views/company/OrderPool.vue')
        : item.path === '/company/staff' ? () => import('../views/company/Staff.vue')
        : item.path === '/company/dispatch' ? () => import('../views/company/Dispatch.vue')
        : item.path === '/driver/tasks' ? () => import('../views/driver/Tasks.vue')
        : item.path === '/admin/orders' ? () => import('../views/admin/Orders.vue')
        : item.path === '/mock/order' ? () => import('../views/mock/MockOrders.vue')
        : () => import('../views/ModulePlaceholder.vue'),
      meta: { title: item.title, roles: item.roles, role: item.roles[0] === 'ADMIN' ? '平台管理员' : item.roles[0] === 'DISPATCHER' ? '车行调度员' : '司机', description: item.description },
    })),
    { path: '/forbidden', component: () => import('../views/Forbidden.vue'), meta: { title: '无访问权限' } },
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFound.vue'), meta: { title: '页面不存在' } },
  ],
})

router.beforeEach(async (to) => {
  if (token.value && !currentUser.value) {
    try { currentUser.value = await request<User>('/auth/me') }
    catch (error) {
      if (error instanceof ApiError && error.status === 401) clearSession()
      else return to.path === '/login' ? true : { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (!currentUser.value && !to.meta.public) return { path: '/login', query: { redirect: to.fullPath } }
  if (currentUser.value && to.path === '/login') return homeFor(currentUser.value.role)
  const roles = to.meta.roles as Role[] | undefined
  if (roles && currentUser.value && !roles.includes(currentUser.value.role)) return '/forbidden'
  document.title = `${String(to.meta.title || '工作台')} · 安心转运`
})

window.addEventListener('session-expired', () => { if (router.currentRoute.value.path !== '/login') void router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } }) })
export default router
