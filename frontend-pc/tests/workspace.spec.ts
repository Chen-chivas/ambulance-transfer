import { test, expect, type Page } from '@playwright/test'

async function mockApi(page: Page, role = 'ADMIN') {
  const user = { id: 1, name: '测试账号', username: 'test', role, companyId: role === 'ADMIN' ? null : 10 }
  const company = { id: 10, name: '安心车行', licenseNo: 'LIC-10', licenseExpireAt: '2099-12-31', contactPhone: '13800000000', auditStatus: 'PENDING', auditRemark: null }
  const companies: object[] = []
  let expired = false
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (!path.startsWith('/api/')) return route.continue()
    const method = route.request().method()
    const send = (json: unknown, status = 200) => route.fulfill({ status, json })
    if (path.startsWith('/api/health')) return send({ status: 'ok', database: 'ambulance_transfer' })
    if (path === '/api/auth/login') {
      if (route.request().postDataJSON().password === 'incorrect') return send({ message: '账号或密码错误' }, 401)
      expired = false
      return send({ token: 'test-access-token', user })
    }
    if (expired) return send({ message: '登录已失效，请重新登录' }, 401)
    if (path === '/api/auth/me') return send(user)
    if (path === '/api/admin/overview') return send({ companies: 0, approved: 0, pending: 0, vehicles: 0, idleVehicles: 0 })
    if (path === '/api/admin/companies') {
      if (method === 'POST') { const created = { ...company, ...route.request().postDataJSON() }; companies.push(created); return send(created, 201) }
      return send({ items: companies, total: companies.length, page: 1, size: 10 })
    }
    if (path.endsWith('/audit')) {
      Object.assign(companies[0]!, { auditStatus: route.request().postDataJSON().status, auditRemark: route.request().postDataJSON().remark })
      return send(companies[0])
    }
    if (path === '/api/company/profile') return send(company)
    if (path.endsWith('/vehicles')) return send({ items: [], total: 0, page: 1, size: 10 })
    return send({ message: '未知测试接口' }, 404)
  })
  return { expire: () => { expired = true } }
}

async function login(page: Page) {
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill('test')
  await page.getByLabel('密码', { exact: true }).fill('test-password-123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
}

test('login errors, session restoration and logout', async ({ page }) => {
  await mockApi(page)
  await page.goto('/admin/companies')
  await expect(page).toHaveURL(/login/)
  await page.getByLabel('账号', { exact: true }).fill('test')
  await page.getByLabel('密码', { exact: true }).fill('incorrect')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByText('账号或密码错误', { exact: true })).toBeVisible()
  await page.getByLabel('密码', { exact: true }).fill('test-password-123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/admin\/companies$/)
  await expect(page.getByRole('heading', { name: '车行管理', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '车行管理', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/login$/)
  expect(await page.evaluate(() => sessionStorage.getItem('ambulance.access-token'))).toBeNull()
})

test('company form validation, creation and mandatory rejection reason', async ({ page }) => {
  await mockApi(page)
  await login(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: /车行管理/ }).click()
  await page.getByRole('button', { name: '新增车行', exact: true }).click()
  await page.getByRole('button', { name: '保存并送审' }).click()
  await expect(page.getByText('请输入车行名称', { exact: true })).toBeVisible()
  await page.getByLabel('车行名称', { exact: true }).fill('安心车行')
  await page.getByLabel('许可证号', { exact: true }).fill('LIC-10')
  await page.getByLabel('联系电话', { exact: true }).fill('13800000000')
  await page.getByRole('button', { name: '保存并送审' }).click()
  await expect(page.getByRole('button', { name: '安心车行', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '审核', exact: true }).click()
  await page.getByRole('dialog', { name: '车行资质审核' }).getByText('驳回', { exact: true }).click()
  await expect(page.getByRole('radio', { name: '驳回', exact: true })).toBeChecked()
  await page.getByRole('button', { name: '提交审核' }).click()
  await expect(page.getByText('请填写驳回原因', { exact: true })).toBeVisible()
  await page.getByRole('textbox', { name: '驳回原因（必填）' }).fill('请补齐资质材料')
  await page.getByRole('button', { name: '提交审核' }).click()
  await expect(page.getByText('已驳回', { exact: true }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: '审核', exact: true })).toBeDisabled()
})

test('dispatcher navigation and direct route protection', async ({ page }) => {
  await mockApi(page, 'DISPATCHER')
  await login(page)
  await expect(page).toHaveURL(/company\/vehicles$/)
  await expect(page.getByRole('link', { name: /车行管理/ })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '安心车行', exact: true })).toBeVisible()
  await page.goto('/admin/companies')
  await expect(page.getByText('无访问权限', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '返回工作空间' }).click()
  await expect(page).toHaveURL(/company\/vehicles$/)
})

test('expired session returns to login without retaining credentials', async ({ page }) => {
  const api = await mockApi(page)
  await login(page)
  await expect(page.getByRole('heading', { name: '系统概览', exact: true })).toBeVisible()
  api.expire()
  await page.getByRole('button', { name: '刷新数据' }).click()
  await expect(page).toHaveURL(/login/)
  expect(await page.evaluate(() => sessionStorage.getItem('ambulance.access-token'))).toBeNull()
})
