import { test, expect, type Page } from '@playwright/test'

const now = '2026-11-05T08:00:00'
async function setup(page: Page, role = 'DISPATCHER') {
  const order = { id: 101, orderNo: 'TR-DISPATCH-101', userName: '张先生', userPhone: '13800000000', originAddress: '第一医院', destAddress: '康复中心', serviceLevel: 'MONITORING', status: role === 'DRIVER' ? 'DISPATCHED' : 'CONFIRMED', assignedCompanyId: 10, assignedCompanyName: '安心车行', finalAmount: 350, assignedVehicleId: role === 'DRIVER' ? 20 : null as number | null, assignedPlateNo: '京A12345', assignedDriverName: '司机王师傅', assignedNurseName: '护士李女士', createdAt: now }
  const state = { order, actions: [] as string[], dispatches: [] as object[], conflict: false, resourceReads: 0, failAction: false }
  const timeline = [{ id: 1, fromStatus: null as string | null, toStatus: order.status, eventType: 'STATUS_CHANGE', remark: '已选定车行', createdAt: now }]
  await page.addInitScript(() => sessionStorage.setItem('ambulance.access-token', 'test-token'))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    const send = (data: unknown, status = 200) => route.fulfill({ status, json: data })
    if (path === '/api/auth/me') return send({ id: 30, name: '测试用户', username: 'test', role, companyId: 10 })
    if (path.startsWith('/api/health')) return send({ status: 'ok' })
    if (path.endsWith('/resources')) {
      state.resourceReads++
      return send({ vehicles: [{ id: 20, plateNo: '京A12345' }], drivers: [{ id: 30, name: '司机王师傅' }], nurses: [{ id: 40, name: '护士李女士' }] })
    }
    if (path.endsWith('/dispatch')) {
      state.dispatches.push(route.request().postDataJSON())
      if (state.conflict) { state.conflict = false; return send({ message: '车辆已被占用，请重新选择' }, 409) }
      Object.assign(order, { status: 'DISPATCHED', assignedVehicleId: 20 }); timeline.push({ ...timeline[0]!, id: 2, toStatus: 'DISPATCHED', remark: '派车成功' }); return send(order)
    }
    const action = path.split('/').at(-1)!
    const statuses: Record<string, string> = { depart: 'EN_ROUTE', arrive: 'ARRIVED', pickup: 'ON_BOARD', complete: 'COMPLETED' }
    if (statuses[action]) {
      state.actions.push(action)
      if (state.failAction) { state.failAction = false; return send({ message: '任务状态已变化，请刷新后按顺序操作' }, 409) }
      order.status = statuses[action]!
      timeline.push({ ...timeline[0]!, id: timeline.length + 1, toStatus: order.status, remark: '任务节点已记录' }); return send(order)
    }
    if (path.endsWith('/101')) return send({ order, quotes: [], timeline })
    if (path.endsWith('/assigned') || path === '/api/driver/tasks') return send({ items: [order], total: 1, page: 1, size: 10 })
    return send({ message: '未知测试接口' }, 404)
  })
  return state
}
async function selectResources(page: Page) {
  for (const [label, option] of [['派车车辆', '京A12345'], ['任务司机', '司机王师傅'], ['随车护士', '护士李女士']]) {
    await page.getByRole('combobox', { name: label!, exact: true }).press('ArrowDown')
    await page.getByRole('option', { name: option!, exact: true }).click()
  }
}

test('monitoring dispatch requires all resources and shows the assigned team', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/company/dispatch')
  await page.getByRole('button', { name: '查看详情', exact: true }).click()
  await page.getByRole('button', { name: '确认派车', exact: true }).click()
  await expect(page.getByText('监护型转运必须配备护士', { exact: true })).toBeVisible()
  expect(state.dispatches).toHaveLength(0)
  await selectResources(page)
  await page.getByRole('button', { name: '确认派车', exact: true }).click()
  await expect(page.getByRole('button', { name: '确认派车', exact: true })).toHaveCount(0)
  await expect(page.getByText('司机王师傅', { exact: true })).toBeVisible()
  expect(state.dispatches).toEqual([{ vehicleId: 20, driverId: 30, nurseId: 40 }])
})

test('stale dispatch resources are refreshed and require a new choice', async ({ page }) => {
  const state = await setup(page); state.conflict = true
  await page.goto('/company/dispatch')
  await page.getByRole('button', { name: '查看详情', exact: true }).click()
  await selectResources(page)
  await page.getByRole('button', { name: '确认派车', exact: true }).click()
  await expect(page.getByText('车辆已被占用，请重新选择', { exact: true })).toBeVisible()
  await expect.poll(() => state.resourceReads).toBe(2)
  await page.getByRole('button', { name: '确认派车', exact: true }).click()
  await expect(page.getByText('请选择空闲车辆', { exact: true })).toBeVisible()
  expect(state.dispatches).toHaveLength(1)
  await selectResources(page)
  await page.getByRole('button', { name: '确认派车', exact: true }).click()
  await expect(page.getByText('派车成功，司机可在任务列表查看', { exact: true })).toBeVisible()
})

test('driver can only advance in sequence with confirmation and sees the timeline', async ({ page }) => {
  const state = await setup(page, 'DRIVER')
  await page.goto('/driver/tasks')
  await expect(page.getByRole('link', { name: /派车调度/ })).toHaveCount(0)
  await page.getByRole('button', { name: '查看详情', exact: true }).click()
  await expect(page.getByText('收到的报价', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '确认送达', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '确认出发', exact: true }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '确认出发', exact: true })).toBeHidden()
  expect(state.actions).toHaveLength(0)
  for (const label of ['确认出发', '到达上车点', '确认患者上车', '确认送达']) {
    await page.getByRole('dialog', { name: '转运订单详情', exact: true }).getByRole('button', { name: label, exact: true }).click()
    await page.getByRole('dialog', { name: label, exact: true }).getByRole('button', { name: label, exact: true }).click()
    await expect(page.getByRole('dialog', { name: label, exact: true })).toBeHidden()
    await expect(page.getByRole('button', { name: label, exact: true })).toHaveCount(0)
  }
  expect(state.actions).toEqual(['depart', 'arrive', 'pickup', 'complete'])
  await expect(page.getByRole('heading', { name: '已完成', exact: true })).toBeVisible()
  await expect(page.locator('.el-timeline-item')).toHaveCount(5)
  await page.goto('/company/dispatch')
  await expect(page.getByText('无访问权限', { exact: true })).toBeVisible()
})

test('driver conflicts refresh task state without sending the next action', async ({ page }) => {
  const state = await setup(page, 'DRIVER'); state.failAction = true
  await page.goto('/driver/tasks')
  await page.getByRole('button', { name: '查看详情', exact: true }).click()
  await page.getByRole('button', { name: '确认出发', exact: true }).click()
  await page.getByRole('dialog', { name: '确认出发', exact: true }).getByRole('button', { name: '确认出发', exact: true }).click()
  await expect(page.getByText('任务状态已变化，请刷新后按顺序操作', { exact: true })).toBeVisible()
  await expect(page.getByRole('dialog', { name: '确认出发', exact: true })).toBeHidden()
  await expect(page.getByRole('button', { name: '确认出发', exact: true })).toBeEnabled()
  expect(state.actions).toEqual(['depart'])
})

test('staff creation validates driver credentials and prevents editing busy personnel', async ({ page }) => {
  const state = await setup(page)
  const rows: object[] = [{ id: 1, name: '忙碌司机', role: 'DRIVER', phone: '13800000000', username: 'busy', certNo: 'CERT-1', certExpireAt: '2099-12-31', enabled: true, busy: true }]
  let payload: Record<string, unknown> | undefined
  await page.route('**/api/company/staff**', async route => {
    if (route.request().method() === 'POST') { payload = route.request().postDataJSON(); rows.push({ ...payload, id: 2, enabled: true, busy: false }); return route.fulfill({ status: 201, json: rows[1] }) }
    return route.fulfill({ json: { items: rows, total: rows.length, page: 1, size: 10 } })
  })
  await page.goto('/company/staff')
  const busy = page.getByRole('row').filter({ hasText: '忙碌司机' })
  await expect(busy.getByRole('button', { name: '编辑', exact: true })).toBeDisabled()
  await expect(busy.getByRole('button', { name: '删除', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '新增人员', exact: true }).click()
  await page.getByRole('button', { name: '保存人员', exact: true }).click()
  await expect(page.getByText('请输入证书编号', { exact: true })).toBeVisible()
  await page.getByLabel('姓名', { exact: true }).fill('新司机')
  await page.getByLabel('联系电话', { exact: true }).fill('13800000000')
  await page.getByLabel('证书编号', { exact: true }).fill('CERT-2')
  await page.getByLabel('证书到期日', { exact: true }).fill('2099-12-31')
  await page.getByLabel('证书到期日', { exact: true }).press('Tab')
  await page.getByLabel('司机登录账号', { exact: true }).fill('driver02')
  await page.getByLabel('初始密码', { exact: true }).fill('long-password-42')
  await page.getByRole('button', { name: '保存人员', exact: true }).click()
  await expect(page.getByText('新司机', { exact: true })).toBeVisible()
  expect(payload?.role).toBe('DRIVER'); expect(payload?.password).toBe('long-password-42')
  expect(state.actions).toHaveLength(0)
})
