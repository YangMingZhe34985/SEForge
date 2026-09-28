import { expect, test, type Page } from '@playwright/test'

const admin = { id: '1', username: 'admin', email: 'admin@example.test', displayName: '平台管理员', accountType: 'PLATFORM', roles: ['ADMIN', 'USER'], enabled: true }
const semester = { id: 's1', code: 'SEM-2026-01', name: '2026 秋季学期', startsOn: '2026-09-01', endsOn: '2027-01-31', status: 'ACTIVE' }
const audit = { id: 'a1', actorId: '1', actorUsername: 'admin', action: 'ADMIN_USER_CREATE', targetType: 'USER', targetId: '2', outcome: 'SUCCEEDED', traceId: 'admin-design-trace', occurredAt: '2026-09-27T02:00:00Z' }
const pageOf = (items: unknown[]) => ({ items, page: 0, size: 20, total: items.length })

async function mockAdmin(page: Page, options: { anonymous?: boolean; failed?: boolean } = {}) {
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    let data: unknown = null
    let status = 200
    if (path === '/auth/csrf') data = { token: 'design-csrf', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }
    else if (path === '/auth/me') { data = admin; if (options.anonymous) status = 401 }
    else if (options.failed && path === '/admin/overview') {
      await route.fulfill({ status: 503, json: { code: 'SERVICE_UNAVAILABLE', message: '管理服务暂时不可用', traceId: 'admin-design-error' } })
      return
    } else if (path === '/admin/overview') data = { teachers: 12, students: 248, activeCourses: 8, archivedCourses: 3, currentSemester: semester }
    else if (path === '/admin/audit-logs') data = pageOf([audit])
    else if (path === '/admin/users') data = pageOf([admin])
    else if (path === '/semesters') data = [semester]
    else if (path === '/courses') data = pageOf([])
    await route.fulfill({ status, json: { code: status === 200 ? 'OK' : 'UNAUTHORIZED', data, message: status === 200 ? 'ok' : '请登录', traceId: 'admin-design' } })
  })
}

test('管理员桌面：概览、三个管理页签、审计与导航', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  await mockAdmin(page)
  await page.goto('/admin')
  await expect(page.getByRole('heading', { name: '管理概览', exact: true })).toBeVisible()
  await expect(page.locator('.stat-grid')).toContainText('248')
  await expect(page.getByText('2026 秋季学期', { exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('admin-overview.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('link', { name: '用户与学期', exact: true }).click()
  await expect(page.getByRole('heading', { name: '平台管理' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'admin@example.test' })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('admin-users.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('tab', { name: '学期管理' }).click()
  await page.getByRole('button', { name: '修改', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('修改学期')
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '新建学期' }).click()
  await expect(page.getByRole('dialog')).toContainText('创建学期')
  await expect(page.getByPlaceholder('2026 秋季学期')).toHaveValue('')
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('tab', { name: '全局课程' }).click()
  await expect(page.getByText('暂无课程', { exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('admin-courses.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('link', { name: '审计日志', exact: true }).click()
  await expect(page.getByText('admin-design-trace')).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('admin-audit.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('button', { name: '收起导航' }).click()
  await expect(page.getByRole('button', { name: '展开导航' })).toBeVisible()
  await page.getByRole('button', { name: '展开导航' }).click()
})

test('管理员登录样式独立，保留校验和普通登录入口', async ({ page }, testInfo) => {
  await mockAdmin(page, { anonymous: true })
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/login/admin')
  await page.getByRole('button', { name: '进入后台' }).click()
  await expect(page.getByText('请输入登录标识', { exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('admin-login.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('link', { name: '返回普通登录' }).click()
  await expect(page.locator('.admin-auth')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '学生注册', exact: true })).toBeVisible()
})

test('管理员窄屏可操作，错误可诊断并重试', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const options = { failed: true }
  await mockAdmin(page, options)
  await page.goto('/admin')
  await expect(page.locator('.admin-error')).toContainText('admin-design-error')
  await expect(page.locator('.stat-grid')).not.toContainText('248')
  options.failed = false
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.locator('.stat-grid')).toContainText('248')
  await page.getByRole('link', { name: '用户与学期', exact: true }).click()
  await expect(page.getByRole('heading', { name: '平台管理' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.getByRole('button', { name: '创建账号', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('admin-mobile.png'), fullPage: true, animations: 'disabled' })
})
