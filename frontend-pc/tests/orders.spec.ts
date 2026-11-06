import { test, expect, type Page } from '@playwright/test'

const now = '2026-11-05T08:00:00'
function makeOrder() {
  return { id: 101, orderNo: 'TR-TEST-101', userName: '张先生', userPhone: '13800000000', originAddress: '第一医院', destAddress: '第二医院', serviceLevel: 'BASIC', patientNote: '需要轮椅', status: 'PENDING_QUOTE', assignedCompanyId: null as number | null, assignedCompanyName: null as string | null, finalAmount: null as number | null, myAmount: null as number | null, myQuoteId: null as number | null, myQuoteStatus: null as string | null, createdAt: now, updatedAt: now }
}
async function setup(page: Page, role = 'ADMIN', enabled = true) {
  const state = { order: makeOrder(), created: false, quote: null as null | { id: number; companyId: number; companyName: string; amount: number; remark: string; status: string; eligible: boolean; updatedAt: string }, poolReads: 0, detailReads: 0, failSelection: false }
  const user = { id: 1, name: '测试账号', username: 'test', role, companyId: role === 'ADMIN' ? null : 10 }
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    const method = route.request().method()
    const send = (data: unknown, status = 200) => route.fulfill({ status, json: data })
    if (path.startsWith('/api/health')) return send({ status: 'ok', database: 'ambulance_transfer' })
    if (path === '/api/auth/me') return send(user)
    if (path === '/api/admin/features') return send({ mockOrdersEnabled: enabled })
    if (path === '/api/mock/orders' && method === 'POST') {
      state.created = true
      Object.assign(state.order, route.request().postDataJSON())
      return send(state.order, 201)
    }
    if (path.endsWith('/select')) {
      if (state.failSelection) {
        state.failSelection = false
        state.quote!.amount = 500
        return send({ message: '报价金额已更新，请刷新后重新确认' }, 409)
      }
      Object.assign(state.order, { status: 'CONFIRMED', assignedCompanyId: 10, assignedCompanyName: '安心车行', finalAmount: state.quote!.amount })
      state.quote!.status = 'WON'
      return send(state.order)
    }
    if (path.endsWith('/quotes') && method === 'POST') {
      const payload = route.request().postDataJSON()
      state.quote = { id: 11, companyId: 10, companyName: '安心车行', amount: payload.amount, remark: payload.remark || '', status: 'PENDING', eligible: true, updatedAt: now }
      Object.assign(state.order, { status: 'QUOTING', myQuoteId: 11, myAmount: payload.amount, myQuoteStatus: 'PENDING' })
      return send(state.quote)
    }
    if (/\/orders\/101$/.test(path)) {
      state.detailReads++
      return send({ order: state.order, quotes: state.quote ? [state.quote] : [], timeline: [{ id: 1, toStatus: 'PENDING_QUOTE', fromStatus: null, eventType: 'STATUS_CHANGE', remark: '创建模拟转运订单', createdAt: now }] })
    }
    if (path.endsWith('/assigned')) return send({ items: [], total: 0, page: 1, size: 10 })
    if (path === '/api/mock/orders' || path === '/api/admin/orders' || path.endsWith('/pool') || path.endsWith('/mine')) {
      state.poolReads++
      const visible = path.startsWith('/api/company') ? path.endsWith('/mine') ? !!state.quote : state.order.status !== 'CONFIRMED' : state.created
      return send({ items: visible ? [state.order] : [], total: visible ? 1 : 0, page: 1, size: 10 })
    }
    return send({ message: '未知测试接口' }, 404)
  })
  await page.addInitScript(() => sessionStorage.setItem('ambulance.access-token', 'test-token'))
  return state
}

test('mock ordering validates addresses and selects a quote after polling', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/mock/order')
  await page.getByRole('button', { name: '新建转运需求' }).click()
  await page.getByRole('button', { name: '提交转运需求', exact: true }).click()
  await expect(page.getByText('请输入联系人姓名', { exact: true })).toBeVisible()
  await page.getByLabel('联系人姓名', { exact: true }).fill('张先生')
  await page.getByLabel('联系电话', { exact: true }).fill('13800000000')
  await page.getByLabel('转运起点', { exact: true }).fill('第一医院')
  await page.getByLabel('转运终点', { exact: true }).fill('第一医院')
  await page.getByRole('button', { name: '提交转运需求', exact: true }).click()
  await expect(page.getByText('起点与终点不能相同', { exact: true })).toBeVisible()
  await page.getByLabel('转运终点', { exact: true }).fill('第二医院')
  await page.getByRole('button', { name: '提交转运需求', exact: true }).click()
  await expect(page.getByText('等待符合条件的车行报价', { exact: true })).toBeVisible()
  state.order.status = 'QUOTING'
  state.quote = { id: 11, companyId: 10, companyName: '安心车行', amount: 350, remark: '含轮椅服务', status: 'PENDING', eligible: true, updatedAt: now }
  await expect(page.getByText('¥350.00', { exact: true })).toBeVisible({ timeout: 15000 })
  await page.getByRole('button', { name: '选择该报价' }).click()
  await page.getByRole('button', { name: '确认选标', exact: true }).click()
  await expect(page.getByText('已选定 安心车行', { exact: true })).toBeVisible()
  await expect(page.getByText('确认金额 ¥350.00 · 等待车行派车')).toBeVisible()
  await expect(page.getByRole('button', { name: '选择该报价' })).toHaveCount(0)
})

test('dispatcher submits and revises quote and sees the winning result', async ({ page }) => {
  const state = await setup(page, 'DISPATCHER')
  await page.goto('/company/orders')
  await page.getByRole('button', { name: '查看 / 报价', exact: true }).click()
  await page.getByRole('button', { name: '提交报价', exact: true }).click()
  await page.getByRole('spinbutton', { name: '报价金额（元）' }).fill('300')
  await page.getByLabel('报价说明', { exact: true }).fill('基础转运')
  await page.getByRole('button', { name: '保存报价', exact: true }).click()
  await expect(page.getByText('¥300.00', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: '修改报价', exact: true }).click()
  await page.getByRole('spinbutton', { name: '报价金额（元）' }).fill('450.5')
  await page.getByRole('button', { name: '保存报价', exact: true }).click()
  await expect(page.getByText('¥450.50', { exact: true }).first()).toBeVisible()
  Object.assign(state.order, { status: 'CONFIRMED', assignedCompanyId: 10, assignedCompanyName: '安心车行', finalAmount: 450.5, myQuoteStatus: 'WON' })
  state.quote!.status = 'WON'
  await page.getByRole('button', { name: '刷新详情', exact: true }).click()
  await expect(page.getByRole('button', { name: '修改报价', exact: true })).toHaveCount(0)
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: '转运订单详情' })).toBeHidden()
  await page.getByRole('tab', { name: '我的报价与中标', exact: true }).click()
  await expect(page.getByText('已中标', { exact: true })).toBeVisible()
})

test('changed price requires a fresh confirmation and invalid offers cannot be selected', async ({ page }) => {
  const state = await setup(page)
  state.created = true; state.order.status = 'QUOTING'; state.failSelection = true
  state.quote = { id: 11, companyId: 10, companyName: '安心车行', amount: 350, remark: '', status: 'PENDING', eligible: true, updatedAt: now }
  await page.goto('/mock/order')
  await page.getByRole('button', { name: '查看报价', exact: true }).click()
  await page.getByRole('button', { name: '选择该报价' }).click()
  await page.getByRole('button', { name: '确认选标', exact: true }).click()
  await expect(page.getByText('报价金额已更新，请刷新后重新确认', { exact: true })).toBeVisible()
  await expect(page.getByText('¥500.00', { exact: true })).toBeVisible()
  state.quote.eligible = false
  await page.getByRole('button', { name: '刷新详情', exact: true }).click()
  await expect(page.getByRole('button', { name: '选择该报价' })).toBeDisabled()
  await expect(page.getByText('当前资质或车辆条件不满足，暂不可选', { exact: true })).toBeVisible()
})

test('disabled mock feature provides no ordering controls', async ({ page }) => {
  await setup(page, 'ADMIN', false)
  await page.goto('/mock/order')
  await expect(page.getByText('模拟下单尚未启用，请联系系统管理员。', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '新建转运需求' })).toHaveCount(0)
})

test('leaving the order pool stops its polling', async ({ page }) => {
  const state = await setup(page, 'DISPATCHER')
  await page.goto('/company/orders')
  await expect(page.getByText('TR-TEST-101', { exact: true })).toBeVisible()
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: /派车调度/ }).click()
  await expect(page.getByRole('heading', { name: '派车调度', exact: true })).toBeVisible()
  await expect(page.getByText('暂无符合条件的订单', { exact: true })).toBeVisible()
  const reads = state.poolReads
  await page.clock.install()
  await page.clock.runFor(11000)
  expect(state.poolReads).toBe(reads)
})
