import { Buffer } from 'node:buffer'
import { expect, test, type Page, type Request } from '@playwright/test'

type UserKind = 'admin' | 'teacher' | 'student'

interface Course {
  id: string
  code: string
  name: string
  description: string
  semesterId: string
  semesterName: string
  role: 'TEACHER' | 'STUDENT'
  memberCount: number
  createdAt: string
}

interface MockResponse {
  data?: unknown
  status?: number
  body?: string
  contentType?: string
}

type ApiHandler = (path: string, method: string, request: Request) => MockResponse | undefined | Promise<MockResponse | undefined>

const now = '2026-09-22T02:00:00Z'

test('本人会话支持重命名与确认删除', async ({ page }) => {
  const course: Course = { id: 'personal', code: 'P', name: '个人会话课程', description: '', semesterId: 'semester-1', semesterName: '秋季', role: 'STUDENT', memberCount: 1, createdAt: now }
  let title = '原会话'
  let removed = false
  await mockPlatform(page, 'student', [course], (path, method, request) => {
    if (path === '/courses/personal') return { data: course }
    if (path.endsWith('/conversations')) return { data: pageOf(removed ? [] : [{ id: 'own-chat', title, updatedAt: now }]) }
    if (path.endsWith('/messages')) return { data: pageOf([]) }
    if (path.endsWith('/own-chat') && method === 'PUT') { title = request.postDataJSON().title; return { data: { id: 'own-chat', title, updatedAt: now } } }
    if (path.endsWith('/own-chat') && method === 'DELETE') { removed = true; return { data: null } }
    return undefined
  })
  await page.goto('/student/courses/personal/assistant')
  await page.getByRole('button', { name: '重命名会话 原会话', exact: true }).click()
  const rename = page.getByRole('dialog', { name: '重命名会话' })
  await rename.locator('input').fill('章节学习记录')
  await rename.getByRole('button', { name: 'OK', exact: true }).click()
  await expect(page.getByRole('button', { name: '重命名会话 章节学习记录', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '删除会话 章节学习记录', exact: true }).click()
  const deletion = page.getByRole('dialog', { name: '删除会话' })
  await expect(deletion).toContainText('永久删除')
  await deletion.getByRole('button', { name: 'OK', exact: true }).click()
  await expect(page.getByRole('button', { name: '删除会话 章节学习记录', exact: true })).toHaveCount(0)
  expect(removed).toBe(true)
})

test('章节创建同时上传资料，AI 草稿经教师编辑确认才成为知识点', async ({ page }) => {
  const course: Course = { id: 'teaching', code: 'TC', name: '教学内容课程', description: '', semesterId: 'semester-1', semesterName: '秋季', role: 'TEACHER', memberCount: 2, createdAt: now }
  let chapter: Record<string, unknown> | null = null
  let uploaded = false
  let confirmation: { points: { name: string }[] } | null = null
  await mockPlatform(page, 'teacher', [course], (path, method, request) => {
    if (path === '/courses/teaching') return { data: course }
    if (path.endsWith('/chapters')) {
      if (method === 'POST') { chapter = { ...request.postDataJSON(), id: 'chapter-1', courseId: course.id }; return { data: chapter } }
      return { data: chapter ? [chapter] : [] }
    }
    const resource = { id: 'resource-1', courseId: course.id, chapterId: 'chapter-1', name: 'lesson.pdf', resourceType: 'DOCUMENT', sizeBytes: 200, status: 'ACTIVE' }
    if (path.endsWith('/resources/upload')) {
      expect(request.postData()).toContain('name="includeInKnowledge"\r\n\r\ntrue')
      uploaded = true; return { data: resource }
    }
    if (path.endsWith('/resources')) return { data: uploaded ? [resource] : [] }
    if (path.endsWith('/knowledge/documents')) return { data: uploaded ? [{ id: 'doc-1', resourceId: resource.id, chapterId: 'chapter-1', originalName: 'lesson.pdf', status: 'READY', sizeBytes: 200 }] : [] }
    if (path.endsWith('/knowledge-points')) return { data: confirmation ? [{ id: 'point-1', chapterId: 'chapter-1', title: confirmation.points[0]!.name, description: '教师确认的说明', sortOrder: 1 }] : [] }
    if (path.endsWith('/knowledge-point-drafts')) return { data: { id: 'draft-1', sources: [{ id: 'chunk-1', documentId: 'doc-1', chapterId: 'chapter-1', name: 'lesson.pdf', page: 1, quote: '模块应保持高内聚。' }], points: [{ name: '内聚', description: '设计原则', importance: 'CORE', sourceIds: ['chunk-1'], manual: false }] } }
    if (path.endsWith('/draft-1/confirm')) { confirmation = request.postDataJSON(); return { data: ['point-1'] } }
    return undefined
  })
  await page.goto('/teacher/courses/teaching')
  await page.getByRole('button', { name: '添加章节', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '添加章节' })
  await dialog.locator('.el-form-item').filter({ hasText: '章节名称' }).locator('input').fill('第一章 模块设计')
  await dialog.locator('input[type=file]').setInputFiles({ name: 'lesson.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 test') })
  await expect(dialog.getByRole('checkbox', { name: '加入 AI 知识库' })).toBeChecked()
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await expect(page.getByText('READY', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'AI 生成知识点', exact: true }).click()
  await page.getByPlaceholder('知识点名称').fill('教师修订：高内聚')
  expect(confirmation).toBeNull()
  await page.getByText('来源引用', { exact: true }).click()
  await expect(page.getByText('模块应保持高内聚。')).toBeVisible()
  await page.getByRole('button', { name: '确认添加所选知识点' }).click()
  await expect(page.getByText('教师修订：高内聚', { exact: true })).toBeVisible()
  expect(uploaded).toBe(true)
  expect(confirmation).not.toBeNull()
})

for (const mode of ['route', 'refresh', 'cancel'] as const) {
  test(`BUG-7 ${mode}: durable recovery, no duplicate generation, Markdown with citations`, async ({ page }) => {
    const course: Course = { id: 'recovery', code: 'REC', name: '恢复课程', description: '', semesterId: 'semester-1', semesterName: '秋季', role: 'STUDENT', memberCount: 1, createdAt: now }
    let state: Record<string, unknown> | null = null
    let posts = 0
    let cancellations = 0
    const answer = '# 完整回答\n\n- **课程依据**\n\n| 指标 | 值 |\n|---|---|\n| 引用 | 1 |\n\n```java\nint value = 1;\n```\n\n[资料](https://example.org)'
    await mockPlatform(page, 'student', [course], (path, method, request) => {
      if (path === '/courses/recovery') return { data: course }
      if (path.endsWith('/conversations')) return { data: pageOf([{ id: 'recover-chat', title: '恢复会话', updatedAt: now }]) }
      if (path.endsWith('/generation')) return { data: state }
      if (path.endsWith('/messages') && method === 'GET') return { data: pageOf(state ? [
        { id: 'question', role: 'USER', content: '解释课程内容', createdAt: now },
        ...(state.status === 'COMPLETED' ? [{ id: 'answer', role: 'ASSISTANT', content: answer, createdAt: now,
          citations: [{ documentId: 'pdf', source: '课程.pdf', page: 2, quote: '课程来源片段' }] }] : []),
      ] : []) }
      if (path.endsWith('/messages') && method === 'POST') {
        posts += 1
        state = { requestId: request.postDataJSON().requestId, status: 'PROCESSING', question: '解释课程内容', traceId: 'recover-trace' }
        // Disconnect only the display stream; the simulated server remains PROCESSING.
        return { contentType: 'text/event-stream', body: 'event: message.delta\ndata: {"delta":"# 正在生成"}\n\n' }
      }
      if (method === 'DELETE' && path.includes('/requests/')) {
        cancellations += 1
        state = { ...state, status: 'CANCELLED', errorCode: 'CANCELLED', errorMessage: '用户已取消生成' }
        return { data: { cancelled: true } }
      }
      return undefined
    })
    await page.goto('/student/courses/recovery/assistant')
    await expect(page.getByText('从课程资料开始提问')).toBeVisible()
    await page.getByPlaceholder('输入与本课程相关的问题…').fill('解释课程内容')
    await page.getByRole('button', { name: '发送', exact: true }).click()
    await expect(page.locator('.generation-status')).toBeVisible()
    if (mode === 'refresh') {
      await page.reload()
      await expect(page.locator('.generation-status')).toBeVisible()
    } else {
      for (let i = 0; i < 3; i++) {
        await page.getByRole('link', { name: '课程内容', exact: true }).click()
        await page.getByRole('link', { name: '课程助手', exact: true }).click()
        await expect(page.locator('.generation-status')).toBeVisible()
      }
    }
    expect(posts).toBe(1)
    expect(cancellations).toBe(0)
    if (mode === 'cancel') {
      await page.getByRole('button', { name: '停止', exact: true }).click()
      await expect(page.locator('.stream-error')).toContainText('CANCELLED')
      await expect(page.getByRole('button', { name: '重试', exact: true })).toBeEnabled()
      expect(cancellations).toBe(1)
      await expect(page.locator('.message--assistant')).toHaveCount(0)
    } else {
      state = { ...state, status: 'COMPLETED', assistantMessageId: 'answer' }
      await expect(page.getByRole('heading', { name: '完整回答' })).toBeVisible()
      await expect(page.locator('.safe-markdown table')).toBeVisible()
      await expect(page.locator('.safe-markdown pre')).toContainText('int value')
      await expect(page.getByText('课程.pdf', { exact: true })).toBeVisible()
      await expect(page.getByText('课程来源片段', { exact: true })).toBeVisible()
      await page.reload()
      await expect(page.locator('.message--assistant')).toHaveCount(1)
      expect(posts).toBe(1)
    }
  })
}

test('Dashboard 班级筛选、历史分页与错误重试', async ({ page }) => {
  const course: Course = { id: 'p6', code: 'P6', name: 'Phase 6', description: '', semesterId: 'semester-1', semesterName: '2026 秋季', role: 'TEACHER', memberCount: 2, createdAt: now }
  let fail = false
  const requests: string[] = []
  const dashboard = { courseId: 'p6', generatedAt: now, overview: { students: 2, assignments: 1, expectedSubmissions: 2, completedSubmissions: 1, completionRate: 50, averageFinalScore: 80 }, gradeDistribution: [{ bucket: '80-89', count: 1 }], knowledgePoints: [{ title: '事务', scoreRate: 50, weak: true }], frequentQuestions: [{ excerpt: '如何设计事务？', count: 3 }], tutor: { total: 3, failed: 1, byOperation: { HINT: 3 } }, qaFeedback: { helpful: 2, notHelpful: 0, helpfulRate: 100 }, errors: { tutorFailures: 1, reviewFailures: 0 } }
  await mockPlatform(page, 'teacher', [course], (path, _method, request) => {
    if (path === '/courses/p6') return { data: course }
    if (path.endsWith('/classes')) return { data: [{ id: 'class-p6', code: 'P6-A', name: '一班' }] }
    if (path.endsWith('/analytics/dashboard')) return fail ? { status: 503, body: JSON.stringify({ code: 'UNAVAILABLE', message: '统计暂不可用' }) } : { data: dashboard }
    if (path.endsWith('/analytics/snapshots')) {
      requests.push(request.url())
      const index = new URL(request.url()).searchParams.get('page') || '0'
      return { data: { items: [{ id: `snapshot-${index}`, metricType: 'DASHBOARD_V1', generatedAt: now, periodEnd: now, payload: dashboard }], page: Number(index), size: 10, total: 21 } }
    }
    return undefined
  })
  await page.goto('/teacher/courses/p6/dashboard')
  await expect(page.getByText('snapshot-0', { exact: true })).toBeVisible()
  await page.locator('.el-pagination').getByText('2', { exact: true }).click()
  await expect(page.getByText('snapshot-1', { exact: true })).toBeVisible()
  await page.getByText('全部教学班', { exact: true }).click()
  await page.getByRole('option', { name: '一班 (P6-A)' }).click()
  await expect.poll(() => requests.some(url => url.includes('classId=class-p6') && url.includes('page=0'))).toBe(true)
  fail = true
  await page.getByRole('button', { name: '刷新数据' }).click()
  await expect(page.getByText('统计暂不可用', { exact: true })).toBeVisible()
  fail = false
  await page.getByRole('button', { name: '刷新数据' }).click()
  await expect(page.getByText('snapshot-0', { exact: true })).toBeVisible()
})

const users = {
  admin: { id: '1', email: 'admin@seforge.test', username: 'admin', displayName: '平台管理员', accountType: 'PLATFORM', roles: ['ADMIN', 'USER'], enabled: true },
  teacher: { id: '2', email: 'teacher@seforge.test', username: 'teacher', displayName: '任课教师', accountType: 'TEACHER', roles: ['USER'], enabled: true },
  student: { id: '3', email: 'student@seforge.test', username: 'student', studentNo: '2026-003', displayName: '学生甲', accountType: 'STUDENT', roles: ['USER'], enabled: true },
} as const

function pageOf<T>(items: T[]) {
  return { items, page: 0, size: 100, total: items.length }
}

function envelope(data: unknown, message = 'OK') {
  return { code: 'OK', message, data, traceId: 'e2e-trace' }
}

async function mockPlatform(page: Page, kind: UserKind, courses: Course[], handler?: ApiHandler) {
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/api\/v1/, '')
    const method = request.method()
    const custom = await handler?.(path, method, request)

    if (custom?.body !== undefined) {
      await route.fulfill({
        status: custom.status ?? 200,
        contentType: custom.contentType ?? 'application/json',
        body: custom.body,
      })
      return
    }
    if (custom) {
      await route.fulfill({
        status: custom.status ?? 200,
        contentType: 'application/json',
        body: JSON.stringify(envelope(custom.data)),
      })
      return
    }

    let data: unknown
    if (path === '/auth/csrf' && method === 'GET') {
      data = { headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'e2e-csrf' }
    } else if (path === '/auth/me' && method === 'GET') {
      data = users[kind]
    } else if (path === '/courses' && method === 'GET') {
      data = pageOf(courses)
    } else if (path === '/semesters' && method === 'GET') {
      data = [{ id: 'semester-1', code: '2026-FALL', name: '2026 秋季', status: 'ACTIVE' }]
    } else if (/^\/courses\/[^/]+\/(resources|knowledge\/documents|chapters|knowledge-points)$/.test(path) && method === 'GET') {
      data = []
    } else if (/^\/courses\/[^/]+\/announcements$/.test(path) && method === 'GET') {
      data = pageOf([])
    } else if (/^\/courses\/[^/]+\/(classes|members|invites)$/.test(path) && method === 'GET') {
      data = []
    } else if (/^\/courses\/[^/]+\/assignments$/.test(path) && method === 'GET') {
      data = pageOf([])
    } else if (path.endsWith('/generation') && method === 'GET') {
      data = null
    } else if (/^\/jobs\/[^/]+$/.test(path) && method === 'GET') {
      data = { id: path.split('/').at(-1), type: 'INGEST_DOCUMENT', status: 'QUEUED', attempts: 0, maxAttempts: 3, cancelRequested: false, createdAt: now, updatedAt: now }
    } else {
      await route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'E2E_UNMOCKED', message: `${method} ${path} was not mocked`, traceId: 'e2e-trace' }),
      })
      return
    }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(envelope(data)) })
  })
}

test('普通登录明确选择学生或教师身份，管理员使用独立入口', async ({ page }) => {
  let authenticated = false
  let receivedPortal = ''
  await mockPlatform(page, 'teacher', [], (path, method, request) => {
    if (path === '/auth/me' && method === 'GET' && !authenticated) return { status: 401, data: null }
    if (path === '/auth/login' && method === 'POST') {
      receivedPortal = (request.postDataJSON() as { portal: string }).portal
      authenticated = true
      return { data: { user: users.teacher } }
    }
    return undefined
  })
  await page.goto('/login')
  await expect(page.getByRole('radio', { name: '学生' })).toBeVisible()
  await page.getByText('教师', { exact: true }).click()
  const form = page.locator('form').filter({ has: page.getByRole('button', { name: '进入工作台' }) })
  await form.locator('.el-form-item').filter({ hasText: '用户名或邮箱' }).locator('input').fill('teacher')
  await form.locator('.el-form-item').filter({ hasText: '密码' }).locator('input').fill('secure-password')
  await form.getByRole('button', { name: '进入工作台' }).click()
  await expect(page).toHaveURL(/\/teacher$/)
  expect(receivedPortal).toBe('TEACHER')
})

test('管理员登录不借用学生或教师选择', async ({ page }) => {
  let authenticated = false
  let receivedPortal = ''
  await mockPlatform(page, 'admin', [], (path, method, request) => {
    if (path === '/auth/me' && method === 'GET' && !authenticated) return { status: 401, data: null }
    if (path === '/auth/login' && method === 'POST') {
      receivedPortal = (request.postDataJSON() as { portal: string }).portal
      authenticated = true
      return { data: { user: users.admin } }
    }
    if (path === '/admin/overview' && method === 'GET') return { data: {
      teachers: 1, students: 1, activeCourses: 0, archivedCourses: 0, currentSemester: null,
    } }
    if (path === '/admin/audit-logs' && method === 'GET') return { data: pageOf([]) }
    return undefined
  })
  await page.goto('/login/admin')
  await expect(page.getByRole('heading', { name: '管理员登录' })).toBeVisible()
  await expect(page.getByRole('radio', { name: '学生' })).toHaveCount(0)
  const form = page.locator('form').filter({ has: page.getByRole('button', { name: '进入后台' }) })
  await form.locator('.el-form-item').filter({ hasText: '用户名或邮箱' }).locator('input').fill('admin')
  await form.locator('.el-form-item').filter({ hasText: '密码' }).locator('input').fill('secure-password')
  await form.getByRole('button', { name: '进入后台' }).click()
  await expect(page).toHaveURL(/\/admin$/)
  expect(receivedPortal).toBe('ADMIN')
})

test('管理员 CSV 先预览后确认并显示逐行结果', async ({ page }) => {
  let confirmed = false
  await mockPlatform(page, 'admin', [], (path, method) => {
    if (path === '/admin/users' && method === 'GET') return { data: pageOf([]) }
    if (path === '/admin/users/import/preview' && method === 'POST') return { data: {
      digest: 'digest-1', total: 2, valid: 1, rejected: 1,
      rows: [
        { line: 2, accountType: 'STUDENT', studentNo: '2026-001', username: 'alice', email: 'alice@example.test', displayName: 'Alice', status: 'VALID', message: 'Ready' },
        { line: 3, accountType: 'STUDENT', studentNo: '2026-001', username: 'bob', email: 'bob@example.test', displayName: 'Bob', status: 'REJECTED', message: 'Duplicate student number in CSV' },
      ],
    } }
    if (path === '/admin/users/import/confirm' && method === 'POST') {
      confirmed = true
      return { data: { total: 2, created: 1, skipped: 1, failed: 0, rows: [
        { line: 2, accountType: 'STUDENT', studentNo: '2026-001', username: 'alice', email: 'alice@example.test', displayName: 'Alice', status: 'CREATED', message: 'Created', initialPassword: 'generated-secret' },
        { line: 3, accountType: 'STUDENT', studentNo: '2026-001', username: 'bob', email: 'bob@example.test', displayName: 'Bob', status: 'SKIPPED', message: 'Duplicate student number in CSV' },
      ] } }
    }
    return undefined
  })
  await page.goto('/admin/users')
  await page.locator('label.import-button input').setInputFiles({ name: 'users.csv', mimeType: 'text/csv',
    buffer: Buffer.from('accountType,studentNo,username,email,displayName\nSTUDENT,2026-001,alice,alice@example.test,Alice\n') })
  const dialog = page.getByRole('dialog', { name: '受控账号导入' })
  await expect(dialog.getByText('待创建 1 行，拒绝 1 行')).toBeVisible()
  expect(confirmed).toBe(false)
  await dialog.getByRole('button', { name: '确认写入' }).click()
  await expect(dialog.getByText('创建 1，跳过 1，失败 0')).toBeVisible()
  await expect(dialog.getByText('generated-secret')).toBeVisible()
  expect(confirmed).toBe(true)
})

test('学生入口展示学号校验、邀请码拒绝与未授权课程提示', async ({ page }) => {
  await mockPlatform(page, 'student', [], (path, method) => {
    if (path === '/auth/me' && method === 'GET') return { status: 401, data: null }
    if (path === '/courses/join' && method === 'POST') return {
      status: 403, body: JSON.stringify({ code: 'ACCESS_DENIED', message: '邀请码无效', traceId: 'e2e-trace' }),
    }
    return undefined
  })
  await page.goto('/login')
  await page.getByRole('button', { name: '学生注册' }).click()
  const form = page.locator('form').filter({ has: page.getByRole('button', { name: '创建学生账号' }) })
  await form.locator('.el-form-item').filter({ hasText: '姓名' }).locator('input').fill('测试学生')
  await form.locator('.el-form-item').filter({ hasText: '用户名' }).locator('input').fill('test.student')
  await form.locator('.el-form-item').filter({ hasText: '邮箱' }).locator('input').fill('test.student@example.test')
  await form.locator('.el-form-item').filter({ hasText: /^密码/ }).locator('input').fill('SecurePass-2026')
  await form.locator('.el-form-item').filter({ hasText: '确认密码' }).locator('input').fill('SecurePass-2026')
  await form.getByRole('button', { name: '创建学生账号' }).click()
  await expect(form.getByText('请输入学号')).toBeVisible()

  // Reload with a valid mocked student session and verify course-scoped denial feedback.
  await page.unroute('**/api/v1/**')
  await mockPlatform(page, 'student', [], (path, method) => {
    if (path === '/courses/join' && method === 'POST') return {
      status: 403, body: JSON.stringify({ code: 'ACCESS_DENIED', message: '邀请码无效', traceId: 'e2e-trace' }),
    }
    return undefined
  })
  await page.goto('/student')
  await page.getByRole('button', { name: '使用邀请码加入' }).click()
  await page.getByPlaceholder('请输入课程邀请码').fill('INVALID')
  await page.getByRole('dialog', { name: '加入课程' }).getByRole('button', { name: '加入', exact: true }).click()
  await expect(page.getByText('邀请码无效')).toBeVisible()
  await page.goto('/student/courses/other-private-course')
  await expect(page).toHaveURL(/\/student\?denied=1/)
  await expect(page.getByText('当前账号无权访问该页面')).toBeVisible()
})

test('管理员创建教师账号', async ({ page }) => {
  let createdBody: Record<string, unknown> | undefined
  let updatedRoles: unknown
  const createdUsers: Array<Record<string, unknown>> = []
  await mockPlatform(page, 'admin', [], (path, method, request) => {
    if (path === '/admin/users' && method === 'GET') return { data: pageOf(createdUsers) }
    if (path === '/admin/users' && method === 'POST') {
      createdBody = request.postDataJSON() as Record<string, unknown>
      const created = {
          id: 'teacher-new',
          ...createdBody,
          roles: ['USER'],
          enabled: true,
      }
      createdUsers.push(created)
      return { data: created }
    }
    if (path === '/admin/users/teacher-new/roles' && method === 'PUT') {
      updatedRoles = (request.postDataJSON() as { roles: unknown }).roles
      createdUsers[0] = { ...createdUsers[0], roles: ['USER', 'ADMIN'] }
      return {
        data: createdUsers[0],
      }
    }
    return undefined
  })

  await page.goto('/admin/users')
  await page.getByRole('button', { name: '创建账号' }).click()
  const dialog = page.getByRole('dialog', { name: '创建平台账号' })
  await dialog.locator('.el-form-item').filter({ hasText: '姓名' }).locator('input').fill('张老师')
  await dialog.locator('.el-form-item').filter({ hasText: '用户名' }).locator('input').fill('zhang.teacher')
  await dialog.locator('.el-form-item').filter({ hasText: '邮箱' }).locator('input').fill('zhang@seforge.test')
  await dialog.locator('.el-form-item').filter({ hasText: '初始密码' }).locator('input').fill('SecurePass-2026')
  await dialog.getByRole('button', { name: '创建', exact: true }).click()

  await expect(page.getByText('张老师')).toBeVisible()
  expect(createdBody).toMatchObject({ accountType: 'TEACHER', username: 'zhang.teacher' })
  await page.getByRole('button', { name: '授予管理员' }).click()
  await page.getByRole('dialog', { name: '平台角色' }).getByRole('button', { name: 'OK' }).click()
  await expect(page.getByText('ADMIN', { exact: true })).toBeVisible()
  expect(updatedRoles).toEqual(['USER', 'ADMIN'])
})

test('创建账号显示用户名规则与服务端字段错误', async ({ page }) => {
  let submissions = 0
  await mockPlatform(page, 'admin', [], (path, method) => {
    if (path === '/admin/users' && method === 'GET') return { data: pageOf([]) }
    if (path === '/admin/users' && method === 'POST') {
      submissions++
      return { status: 400, body: JSON.stringify({ code: 'VALIDATION_FAILED', message: 'Request validation failed', details: { username: '用户名字段错误示例' } }) }
    }
    return undefined
  })
  await page.goto('/admin/users')
  await page.getByRole('button', { name: '创建账号' }).click()
  const dialog = page.getByRole('dialog', { name: '创建平台账号' })
  const field = (name: string) => dialog.locator('.el-form-item').filter({ hasText: name }).locator('input')
  await field('姓名').fill('张老师')
  await field('用户名').fill('中文用户名')
  await field('邮箱').fill('valid@example.invalid')
  await field('初始密码').fill('ValidPassword123!')
  await dialog.getByRole('button', { name: '创建', exact: true }).click()
  await expect(dialog.locator('.el-form-item__error')).toContainText('3–64 位英文字母')
  expect(submissions).toBe(0)
  await field('用户名').fill('valid.teacher')
  await dialog.getByRole('button', { name: '创建', exact: true }).click()
  await expect(dialog.locator('.el-form-item__error')).toHaveText('用户名字段错误示例')
  expect(submissions).toBe(1)
})

test('教师创建课程并上传课程资料', async ({ page }) => {
  const courses: Course[] = []
  const courseClasses: Array<Record<string, unknown>> = []
  const invites: Array<Record<string, unknown>> = []
  let uploaded = false
  let inviteRequest: Record<string, unknown> | undefined
  await mockPlatform(page, 'teacher', courses, (path, method, request) => {
    if (path === '/courses' && method === 'POST') {
      const input = request.postDataJSON() as Record<string, string>
      const course: Course = {
        id: 'course-new',
        code: 'CRS-00000001',
        name: input.name,
        description: input.description,
        semesterId: input.semesterId,
        semesterName: '2026 秋季',
        role: 'TEACHER',
        memberCount: 1,
        createdAt: now,
      }
      courses.unshift(course)
      return { data: course }
    }
    if (path === '/courses/course-new/resources/upload' && method === 'POST') {
      uploaded = true
      return { data: { id: 'resource-1', courseId: 'course-new', name: 'requirements.md',
        resourceType: 'DOCUMENT', objectKey: 'courses/course-new/resources/resource-1',
        contentType: 'text/markdown', sizeBytes: 35, createdAt: now } }
    }
    if (path === '/courses/course-new/resources' && method === 'GET') {
      return { data: uploaded ? [{ id: 'resource-1', courseId: 'course-new', name: 'requirements.md',
        resourceType: 'DOCUMENT', objectKey: 'courses/course-new/resources/resource-1',
        contentType: 'text/markdown', sizeBytes: 35, createdAt: now }] : [] }
    }
    if (path === '/courses/course-new/classes' && method === 'GET') return { data: courseClasses }
    if (path === '/courses/course-new/classes' && method === 'POST') {
      const input = request.postDataJSON() as Record<string, unknown>
      expect(input).not.toHaveProperty('code')
      const courseClass = { id: 'class-1', courseId: 'course-new', ...input, code: 'CLS-00000002', active: true, createdAt: now }
      courseClasses.push(courseClass)
      return { data: courseClass }
    }
    if (path === '/courses/course-new/invites' && method === 'GET') return { data: invites }
    if (path === '/courses/course-new/invites' && method === 'POST') {
      inviteRequest = request.postDataJSON() as Record<string, unknown>
      const invite = {
        id: 'invite-1', courseId: 'course-new', code: 'JOIN-PHASE1',
        classId: inviteRequest.classId ?? null, memberRole: inviteRequest.memberRole,
        maxUses: inviteRequest.maxUses ?? null, usedCount: 0, expiresAt: null, active: true,
      }
      invites.unshift(invite)
      return { data: invite }
    }
    return undefined
  })

  await page.goto('/teacher')
  await page.getByRole('button', { name: '创建课程' }).click()
  const dialog = page.getByRole('dialog', { name: '创建课程' })
  await expect(dialog.getByText('课程编号将在创建后由系统生成。')).toBeVisible()
  await dialog.locator('.el-form-item').filter({ hasText: '课程名称' }).locator('input').fill('软件工程实践')
  await dialog.locator('.el-form-item').filter({ hasText: '所属学期' }).locator('.el-select').click()
  await page.getByRole('option', { name: '2026 秋季' }).click()
  await dialog.getByRole('button', { name: '创建', exact: true }).click()
  await expect(page).toHaveURL(/\/teacher\/courses\/course-new$/)
  await expect(page.getByText('软件工程实践', { exact: true }).first()).toBeVisible()

  await page.getByRole('tab', { name: '参考资料' }).click()
  await page.locator('label.upload-button').filter({ hasText: '上传参考资料' }).locator('input').setInputFiles({
    name: 'requirements.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from('# Software requirements specification'),
  })
  await expect(page.getByText('requirements.md')).toBeVisible()
  expect(uploaded).toBe(true)

  await page.getByRole('tab', { name: '教学管理' }).click()
  await page.getByRole('button', { name: '创建教学班' }).click()
  const classDialog = page.getByRole('dialog', { name: '创建教学班' })
  await expect(classDialog.locator('.el-form-item').filter({ hasText: '教学班代码' }).locator('input')).toBeDisabled()
  await classDialog.locator('.el-form-item').filter({ hasText: '教学班名称' }).locator('input').fill('软件工程 1 班')
  await classDialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('软件工程 1 班', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '创建邀请码' }).click()
  const inviteDialog = page.getByRole('dialog', { name: '创建课程邀请码' })
  await inviteDialog.locator('.el-form-item').filter({ hasText: '加入教学班' }).locator('.el-select').click()
  await page.getByRole('option', { name: '软件工程 1 班 (CLS-00000002)' }).click()
  await inviteDialog.getByRole('button', { name: '生成并复制' }).click()
  await expect(page.getByText('JOIN-PHASE1', { exact: true })).toBeVisible()
  expect(inviteRequest).toMatchObject({ classId: 'class-1', memberRole: 'STUDENT' })
})

test('学生注册、通过邀请码加入并查看授权课程资源', async ({ page }) => {
  const courses: Course[] = []
  let authenticated = false
  let registeredBody: Record<string, unknown> | undefined
  let joinBody: Record<string, unknown> | undefined
  await mockPlatform(page, 'student', courses, (path, method, request) => {
    if (path === '/auth/me' && method === 'GET') {
      if (authenticated) return { data: users.student }
      return { status: 401, data: null }
    }
    if (path === '/auth/register' && method === 'POST') {
      registeredBody = request.postDataJSON() as Record<string, unknown>
      return { data: users.student }
    }
    if (path === '/auth/login' && method === 'POST') {
      authenticated = true
      return { data: { user: users.student } }
    }
    if (path === '/courses/join' && method === 'POST') {
      joinBody = request.postDataJSON() as Record<string, unknown>
      const course: Course = {
        id: 'course-phase1', code: 'SE-102', name: '软件工程基础', description: 'Phase 1 验收课程',
        semesterId: 'semester-1', semesterName: '2026 秋季', role: 'STUDENT', memberCount: 2, createdAt: now,
      }
      courses.push(course)
      return { data: course }
    }
    if (path === '/courses/course-phase1/resources' && method === 'GET') {
      return { data: [{
        id: 'resource-1', courseId: 'course-phase1', name: '课程大纲.pdf', description: '课程资源',
        resourceType: 'DOCUMENT', objectKey: 'courses/course-phase1/resources/syllabus.pdf',
        contentType: 'application/pdf', sizeBytes: 1024, createdAt: now,
      }] }
    }
    return undefined
  })

  await page.goto('/login')
  await page.getByRole('button', { name: '学生注册' }).click()
  const registerForm = page.locator('form').filter({ has: page.getByRole('button', { name: '创建学生账号' }) })
  await registerForm.locator('.el-form-item').filter({ hasText: '姓名' }).locator('input').fill('学生乙')
  await registerForm.locator('.el-form-item').filter({ hasText: '用户名' }).locator('input').fill('student.two')
  await registerForm.locator('.el-form-item').filter({ hasText: '邮箱' }).locator('input').fill('student.two@seforge.test')
  await registerForm.locator('.el-form-item').filter({ hasText: '学号' }).locator('input').fill('2026-003')
  await registerForm.locator('.el-form-item').filter({ hasText: /^密码/ }).locator('input').fill('SecurePass-2026')
  await registerForm.locator('.el-form-item').filter({ hasText: '确认密码' }).locator('input').fill('SecurePass-2026')
  await registerForm.getByRole('button', { name: '创建学生账号' }).click()
  await expect(page.getByText('注册成功，请使用新账号登录')).toBeVisible()
  expect(registeredBody).toMatchObject({ username: 'student.two', studentNo: '2026-003', displayName: '学生乙', email: 'student.two@seforge.test' })

  const loginForm = page.locator('form').filter({ has: page.getByRole('button', { name: '进入工作台' }) })
  await loginForm.locator('.el-form-item').filter({ hasText: '密码' }).locator('input').fill('SecurePass-2026')
  await loginForm.getByRole('button', { name: '进入工作台' }).click()
  await expect(page).toHaveURL(/\/student$/)

  await page.getByRole('button', { name: '使用邀请码加入' }).click()
  const joinDialog = page.getByRole('dialog', { name: '加入课程' })
  await joinDialog.getByPlaceholder('请输入课程邀请码').fill('JOIN-SE-102')
  await joinDialog.getByRole('button', { name: '加入', exact: true }).click()
  await expect(page).toHaveURL(/\/student\/courses\/course-phase1$/)
  await expect(page.getByText('软件工程基础', { exact: true }).first()).toBeVisible()
  await page.getByRole('tab', { name: '参考资料' }).click()
  await expect(page.getByText('课程大纲.pdf', { exact: true })).toBeVisible()
  expect(joinBody).toEqual({ inviteCode: 'JOIN-SE-102' })
})

test('学生通过邀请码加入课程并完成带引用问答', async ({ page }) => {
  const courses: Course[] = []
  let questionRequest: Record<string, unknown> | undefined
  await mockPlatform(page, 'student', courses, (path, method, request) => {
    if (path === '/courses/join' && method === 'POST') {
      const course: Course = {
        id: 'course-1', code: 'SE-101', name: '软件工程导论', description: '需求与设计基础', semesterId: 'semester-1',
        semesterName: '2026 秋季', role: 'STUDENT', memberCount: 32, createdAt: now,
      }
      courses.push(course)
      return { data: course }
    }
    if (path === '/courses/course-1/conversations' && method === 'GET') {
      return { data: pageOf([{ id: 'conversation-1', courseId: 'course-1', title: '课程问答', updatedAt: now }]) }
    }
    if (path === '/courses/course-1/conversations/conversation-1/messages' && method === 'GET') {
      return { data: pageOf([]) }
    }
    if (path === '/courses/course-1/conversations/conversation-1/messages' && method === 'POST') {
      questionRequest = request.postDataJSON() as Record<string, unknown>
      const sse = [
        'event: citation\nid: evt-1\ndata: {"type":"citation","requestId":"e2e-request","traceId":"e2e-trace","eventId":"evt-1","data":{"id":"citation-1","documentId":"document-1","source":"SRS.md","page":2,"quote":"需求应当可验证。"}}\n\n',
        'event: message.delta\nid: evt-2\ndata: {"type":"message.delta","requestId":"e2e-request","traceId":"e2e-trace","eventId":"evt-2","data":{"delta":"可追踪矩阵把需求与验证证据关联起来。"}}\n\n',
        `event: done\nid: evt-3\ndata: {"type":"done","requestId":"e2e-request","traceId":"e2e-trace","eventId":"evt-3","data":{"message":{"id":"message-2","role":"ASSISTANT","content":"可追踪矩阵把需求与验证证据关联起来。","citations":[],"createdAt":"${now}"}}}\n\n`,
      ].join('')
      return { body: sse, contentType: 'text/event-stream' }
    }
    return undefined
  })

  await page.goto('/student')
  await page.getByRole('button', { name: '使用邀请码加入' }).click()
  const joinDialog = page.getByRole('dialog', { name: '加入课程' })
  await joinDialog.getByPlaceholder('请输入课程邀请码').fill('JOIN-SE-101')
  await joinDialog.getByRole('button', { name: '加入', exact: true }).click()
  await expect(page).toHaveURL(/\/student\/courses\/course-1$/)
  await expect(page.getByText('软件工程导论', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: '进入课程助手' }).click()

  await page.getByPlaceholder('输入与本课程相关的问题…').fill('可追踪矩阵有什么作用？')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByText('可追踪矩阵把需求与验证证据关联起来。')).toBeVisible()
  await expect(page.getByText('SRS.md')).toBeVisible()
  await expect(page.getByText('需求应当可验证。')).toBeVisible()
  expect(questionRequest?.content).toBe('可追踪矩阵有什么作用？')
  expect(questionRequest?.requestId).toEqual(expect.any(String))
})

test('统一资源入口显示知识库错误、加入、重建与移出状态', async ({ page }) => {
  const course: Course = { id: 'p3', code: 'P3', name: 'Phase 3 课程', description: '', semesterId: 'semester-1', semesterName: '2026 秋季', role: 'TEACHER', memberCount: 1, createdAt: now }
  let unavailable = true, exists = false, fileExists = false
  let status = 'FAILED'
  const resource = { id: 'p3-resource', courseId: 'p3', name: 'phase3.md', sizeBytes: 10, resourceType: 'DOCUMENT', objectKey: 'key', createdAt: now }
  const job = () => ({ id: 'p3-job', status: status === 'READY' ? 'COMPLETED' : 'FAILED', error: status === 'READY' ? null : '可控摄取故障', cancelRequested: false })
  const document = () => ({ id: 'p3-doc', resourceId: 'p3-resource', courseId: 'p3', name: 'phase3.md', sizeBytes: 10, status, job: job() })
  await mockPlatform(page, 'teacher', [course], (path, method) => {
    if (path === '/courses/p3/resources' && method === 'GET') return { data: fileExists ? [resource] : [] }
    if (path === '/courses/p3/resources/upload' && method === 'POST') { fileExists = true; return { data: resource } }
    if (path === '/courses/p3/resources/p3-resource/knowledge' && method === 'POST') { exists = true; return { data: { document: document(), job: job(), duplicate: false } } }
    if (path === '/courses/p3/knowledge/documents' && method === 'GET') {
      if (unavailable) return { status: 503, body: JSON.stringify({ code: 'UNAVAILABLE', message: '知识库暂不可用' }) }
      return { data: exists ? [document()] : [] }
    }
    if (path === '/courses/p3/knowledge/documents/p3-doc/reindex' && method === 'POST') { status = 'READY'; return { data: job() } }
    if (path === '/jobs/p3-job' && method === 'GET') return { data: job() }
    if (path === '/courses/p3/knowledge/documents/p3-doc' && method === 'DELETE') { exists = false; return { data: null } }
    if (path === '/courses/p3/resources/p3-resource' && method === 'DELETE') { fileExists = false; return { data: null } }
    return undefined
  })
  await page.goto('/teacher/courses/p3')
  await page.getByRole('tab', { name: '参考资料' }).click()
  await expect(page.getByText('知识库暂不可用', { exact: true })).toBeVisible()
  unavailable = false
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.getByText('知识库暂不可用', { exact: true })).not.toBeVisible()
  await page.locator('label.upload-button').filter({ hasText: '上传参考资料' }).locator('input').setInputFiles({ name: 'phase3.md', mimeType: 'text/markdown', buffer: Buffer.from('# cohesion') })
  const row = page.locator('.document-table .el-table__row').filter({ hasText: 'phase3.md' })
  await expect(row.getByText('普通资源', { exact: true })).toBeVisible()
  await row.getByRole('button', { name: '加入知识库' }).click()
  await expect(row.getByText('FAILED', { exact: true })).toBeVisible()
  await row.getByRole('button', { name: '重建索引 / 重试' }).click()
  await expect(row.getByText('READY', { exact: true })).toBeVisible()
  await row.getByRole('button', { name: '移出知识库' }).click()
  await page.getByRole('dialog', { name: '移出知识库' }).getByRole('button', { name: 'OK', exact: true }).click()
  await expect(row.getByText('普通资源', { exact: true })).toBeVisible()
  expect(fileExists).toBe(true); expect(exists).toBe(false)
  await row.getByRole('button', { name: '删除资料' }).click()
  await page.getByRole('dialog', { name: '删除资料' }).getByRole('button', { name: 'OK', exact: true }).click()
  await expect(row).toHaveCount(0)
})

test('Phase 3 中断回答可重试并反馈，不显示内部推理', async ({ page }) => {
  const course: Course = { id: 'p3', code: 'P3', name: 'Phase 3 课程', description: '', semesterId: 'semester-1', semesterName: '2026 秋季', role: 'STUDENT', memberCount: 1, createdAt: now }
  let attempts = 0
  let feedback = false
  await mockPlatform(page, 'student', [course], (path, method) => {
    if (path === '/courses/p3/conversations' && method === 'GET') return { data: pageOf([{ id: 'p3-chat', title: '历史会话', updatedAt: now }]) }
    if (path.endsWith('/p3-chat/messages') && method === 'GET') return { data: pageOf([{ id: 'old', role: 'USER', content: '历史问题', createdAt: now }]) }
    if (path.endsWith('/p3-chat/messages') && method === 'POST') {
      attempts += 1
      const delta = 'event: message.delta\ndata: {"delta":"课程依据回答"}\n\n'
      return { contentType: 'text/event-stream', body: attempts === 1 ? delta : delta + 'event: done\ndata: {"message":{"id":"saved"}}\n\n' }
    }
    if (path.endsWith('/saved/feedback') && method === 'POST') { feedback = true; return { data: null } }
    return undefined
  })
  await page.goto('/student/courses/p3/assistant')
  await expect(page.getByText('历史问题', { exact: true })).toBeVisible()
  await page.getByPlaceholder('输入与本课程相关的问题…').fill('cohesion')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByText('流式响应意外中断，请重试（STREAM_INTERRUPTED）', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '有帮助', exact: true })).not.toBeVisible()
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await page.getByRole('button', { name: '有帮助', exact: true }).click()
  await expect(page.getByText('反馈已记录')).toBeVisible()
  expect(feedback).toBe(true)
  expect(attempts).toBe(2)
})

test('判断题初始不选中，保留学生 false；Tutor 显示追踪号并安全恢复同一请求', async ({ page }) => {
  const course: Course = { id: 'bug20', code: 'B20', name: '判断题回归', description: '', semesterId: 's', semesterName: '秋季', role: 'STUDENT', memberCount: 1, createdAt: now }
  const assignment = { id: 'bug20-work', courseId: course.id, title: '判断作业', status: 'PUBLISHED', maxAttempts: 1,
    questions: [{ id: 'bool-1', type: 'TRUE_FALSE', prompt: '判断命题', points: 5, orderIndex: 0 }] }
  let saved: unknown = null
  const keys: string[] = []
  await mockPlatform(page, 'student', [course], (path, method, request) => {
    if (path === '/courses/bug20/assignments') return { data: pageOf([assignment]) }
    if (path === '/assignments/bug20-work') return { data: assignment }
    if (path.endsWith('/submissions/me')) return { data: saved }
    if (path.endsWith('/submissions/draft')) {
      saved = { id: 'draft', assignmentId: assignment.id, status: 'DRAFT', answers: request.postDataJSON().answers, attemptNumber: 1, updatedAt: now }
      return { data: saved }
    }
    if (path.endsWith('/tutor')) {
      const key = request.postDataJSON().requestKey
      expect(request.headers()['x-trace-id']).toBe(key)
      keys.push(key)
      if (keys.length === 1) return { status: 409, body: JSON.stringify({ code: 'TUTOR_PROCESSING', message: '仍在处理，不会重复调用模型', traceId: key, details: { stage: 'PROCESSING' } }) }
      return { data: { interactionId: 'one', action: 'HINT', content: '已恢复原请求的提示', allowed: true } }
    }
    return undefined
  })
  await page.goto('/student/courses/bug20/assignments')
  const yes = page.getByRole('radio', { name: '正确', exact: true })
  const no = page.getByRole('radio', { name: '错误', exact: true })
  await expect(yes).not.toBeChecked()
  await expect(no).not.toBeChecked()
  await page.locator('label.el-radio').filter({ hasText: '错误' }).click()
  await expect.poll(() => saved).not.toBeNull()
  await page.reload()
  await expect(no).toBeChecked()
  await page.getByRole('button', { name: '请求辅导', exact: true }).click()
  await expect(page.getByText('Tutor 请求未完成', { exact: true })).toBeVisible()
  await expect(page.getByText(/POST \/api\/v1\/assignments\/bug20-work\/tutor/)).toContainText(keys[0])
  await page.getByRole('button', { name: '重试 / 恢复结果', exact: true }).click()
  await expect(page.getByText('已恢复原请求的提示')).toBeVisible()
  expect(keys).toHaveLength(2)
  expect(keys[1]).toBe(keys[0])
})

test('学生保存作业草稿、获取 Tutor 提示并正式提交', async ({ page }) => {
  const course: Course = {
    id: 'course-1', code: 'SE-101', name: '软件工程导论', description: '需求与设计基础', semesterId: 'semester-1',
    semesterName: '2026 秋季', role: 'STUDENT', memberCount: 32, createdAt: now,
  }
  let draftSaved = false
  let submitted = false
  await mockPlatform(page, 'student', [course], (path, method) => {
    if (path === '/courses/course-1/assignments' && method === 'GET') {
      return { data: pageOf([{ id: 'assignment-1', courseId: 'course-1', title: '需求分析作业', description: '完成场景分析', status: 'PUBLISHED', maxAttempts: 2 }]) }
    }
    if (path === '/assignments/assignment-1' && method === 'GET') {
      return { data: { id: 'assignment-1', courseId: 'course-1', title: '需求分析作业', description: '完成场景分析', status: 'PUBLISHED', maxAttempts: 2, questions: [{ id: 'question-1', type: 'ANALYSIS', prompt: '说明用例的价值', points: 20, orderIndex: 1 }] } }
    }
    if (path === '/assignments/assignment-1/submissions/me' && method === 'GET') return { data: null }
    if (path === '/assignments/assignment-1/submissions/draft' && method === 'PUT') {
      draftSaved = true
      return { data: { id: 'submission-1', assignmentId: 'assignment-1', status: 'DRAFT', answers: [{ questionId: 'question-1', answer: '连接需求与参与者目标' }], attemptNumber: 0, updatedAt: now } }
    }
    if (path === '/assignments/assignment-1/tutor' && method === 'POST') {
      return { data: { interactionId: 'tutor-1', action: 'HINT', allowed: true, content: '先识别参与者、目标和成功场景。' } }
    }
    if (path === '/assignments/assignment-1/submissions' && method === 'POST') {
      submitted = true
      return { data: { id: 'submission-1', assignmentId: 'assignment-1', status: 'SUBMITTED', answers: [{ questionId: 'question-1', answer: '连接需求与参与者目标' }], attemptNumber: 1, updatedAt: now, submittedAt: now } }
    }
    return undefined
  })

  await page.goto('/student/courses/course-1/assignments')
  await page.getByPlaceholder('输入你的答案和推理过程').fill('连接需求与参与者目标')
  await expect.poll(() => draftSaved).toBe(true)
  await page.getByRole('button', { name: '请求辅导' }).click()
  await expect(page.getByText('先识别参与者、目标和成功场景。')).toBeVisible()
  await page.getByRole('button', { name: '提交作业' }).click()
  await page.getByRole('dialog', { name: '提交作业' }).getByRole('button', { name: 'OK' }).click()
  await expect.poll(() => submitted).toBe(true)
  await expect(page.getByText('作业已提交')).toBeVisible()
  await page.getByRole('button', { name: '开始重交' }).click()
  await expect(page.getByRole('button', { name: '提交新尝试' })).toBeVisible()
})

test('学生在 debounce 前刷新后恢复最后一次编辑并补存服务器草稿', async ({ page }) => {
  const course: Course = {
    id: 'course-1', code: 'SE-101', name: '软件工程导论', description: '需求与设计基础', semesterId: 'semester-1',
    semesterName: '2026 秋季', role: 'STUDENT', memberCount: 32, createdAt: now,
  }
  let draft: string | null = null
  let writes = 0
  await mockPlatform(page, 'student', [course], (path, method, request) => {
    if (path === '/courses/course-1/assignments' && method === 'GET') return { data: pageOf([
      { id: 'assignment-1', courseId: 'course-1', title: '需求分析作业', status: 'PUBLISHED', maxAttempts: 2 },
    ]) }
    if (path === '/assignments/assignment-1' && method === 'GET') return { data: {
      id: 'assignment-1', courseId: 'course-1', title: '需求分析作业', status: 'PUBLISHED', maxAttempts: 2,
      questions: [{ id: 'question-1', type: 'ANALYSIS', prompt: '说明用例的价值', points: 20, orderIndex: 1 }],
    } }
    if (path === '/assignments/assignment-1/submissions/me' && method === 'GET') return { data: draft
      ? { id: 'submission-1', assignmentId: 'assignment-1', status: 'DRAFT', attemptNumber: 1,
        answers: [{ questionId: 'question-1', answer: draft }], updatedAt: now } : null }
    if (path === '/assignments/assignment-1/submissions/draft' && method === 'PUT') {
      writes += 1
      draft = request.postDataJSON().answers[0].answer
      return { data: { id: 'submission-1', assignmentId: 'assignment-1', status: 'DRAFT',
        attemptNumber: 1, answers: [{ questionId: 'question-1', answer: draft }], updatedAt: now } }
    }
    return undefined
  })
  await page.goto('/student/courses/course-1/assignments')
  const input = page.getByPlaceholder('输入你的答案和推理过程')
  await input.fill('最后一次修改')
  expect(writes).toBe(0)
  await page.reload()
  await expect(input).toHaveValue('最后一次修改')
  await expect.poll(() => draft).toBe('最后一次修改')
  expect(writes).toBeGreaterThanOrEqual(1)
  await input.fill('离开页面前的最后修改')
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '我的课程' }).click()
  await expect(page).toHaveURL(/\/student$/)
  expect(draft).toBe('离开页面前的最后修改')
})

test('结构化图片答案在刷新前保留 Markdown 最后修改与附件 ID', async ({ page }) => {
  const course: Course = { id: 'course-1', code: 'SE-101', name: '软件工程', description: '', semesterId: 'semester-1', semesterName: '秋季', role: 'STUDENT', memberCount: 2, createdAt: now }
  let value: { text: string; assetIds: string[] } = { text: '', assetIds: [] }
  let uploads = 0
  let attempt = 0
  const current = () => ({ id: 's-1', assignmentId: 'a-1', status: 'DRAFT', attemptNumber: attempt, updatedAt: now, answers: [{ questionId: 'q-1', answer: value }] })
  await mockPlatform(page, 'student', [course], (path, method, request) => {
    if (path === '/courses/course-1/assignments') return { data: pageOf([{ id: 'a-1', courseId: 'course-1', title: '图片设计题', status: 'PUBLISHED', maxAttempts: 1 }]) }
    if (path === '/assignments/a-1') return { data: { id: 'a-1', courseId: 'course-1', title: '图片设计题', status: 'PUBLISHED', maxAttempts: 1, questions: [{ id: 'q-1', type: 'DESIGN', prompt: '# 设计说明', points: 10, orderIndex: 0, config: { schemaVersion: 1 } }] } }
    if (path === '/assignments/a-1/submissions/me') return { data: attempt ? current() : null }
    if (path === '/assignments/a-1/media' && method === 'POST') { uploads++; attempt = 1; return { data: { id: '77', fileName: 'diagram.png', mediaType: 'image/png', sizeBytes: 68, submissionId: 's-1' } } }
    if (path === '/assignments/a-1/media/77') return { data: { id: '77', fileName: 'diagram.png', mediaType: 'image/png', sizeBytes: 68 } }
    if (path === '/assignments/a-1/submissions/draft' && method === 'PUT') { attempt = 1; value = request.postDataJSON().answers[0].answer; return { data: current() } }
    return undefined
  })
  const image = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6AAAAAElFTkSuQmCC', 'base64')
  await page.route('**/assignments/a-1/media/77/download', route => route.fulfill({ status: 200, contentType: 'image/png', body: image }))
  await page.goto('/student/courses/course-1/assignments')
  await page.locator('input[type="file"]').setInputFiles({ name: 'diagram.png', mimeType: 'image/png', buffer: image })
  await expect.poll(() => value.assetIds).toEqual(['77'])
  const input = page.getByPlaceholder('输入你的答案和推理过程')
  await input.fill('## 最后一次设计修改')
  await page.reload()
  await expect(input).toHaveValue('## 最后一次设计修改')
  await expect.poll(() => value).toEqual({ text: '## 最后一次设计修改', assetIds: ['77'] })
  expect(uploads).toBe(1)
})

test('智能导题先编辑确认，缺失分值不自动填充', async ({ page }) => {
  const course: Course = { id: 'imports', code: 'IMP', name: '导题课程', description: '', semesterId: 's', semesterName: '秋季', role: 'TEACHER', memberCount: 1, createdAt: now }
  const assignment = { id: 'import-work', courseId: course.id, title: '导题作业', status: 'DRAFT', questions: [] as unknown[] }
  let confirmed = 0
  await mockPlatform(page, 'teacher', [course], (path, method, request) => {
    if (path === '/courses/imports/assignments') return { data: pageOf([assignment]) }
    if (path === '/assignments/import-work') return { data: assignment }
    if (path.endsWith('/submissions')) return { data: [] }
    if (path.endsWith('/media') && method === 'POST') return { data: { id: 'source', fileName: 'exam.md', mediaType: 'text/markdown', sizeBytes: 30 } }
    if (path.endsWith('/media/source')) return { data: { id: 'source', fileName: 'exam.md', mediaType: 'text/markdown', sizeBytes: 30 } }
    if (path.endsWith('/question-imports') && method === 'POST') return { data: { id: 'draft', sourceFile: 'source', sourceName: 'exam.md', confirmed: false, questions: [{ draftKey: 'first', type: 'SHORT_ANSWER', contentMarkdown: 'Explain cohesion', choices: [], correctAnswer: null, referenceAnswer: '', score: null, order: 0, sourcePage: 1, sourceRegion: [], warnings: ['分值未识别'] }] } }
    if (path.endsWith('/question-imports/draft/confirm')) {
      confirmed++; const question = request.postDataJSON().questions[0].question
      expect(question.prompt).toBe('教师编辑后的题目'); expect(question.points).toBe(8)
      assignment.questions = [{ ...question, id: 'formal' }]
      return { data: assignment.questions }
    }
    return undefined
  })
  await page.route('**/assignments/import-work/media/source/download', route => route.fulfill({ status: 200, contentType: 'text/markdown', body: '# Exam' }))
  await page.goto('/teacher/courses/imports/assignments')
  await page.getByRole('button', { name: '智能导入题目', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '智能导入题目' })
  await dialog.locator('input[type=file]').setInputFiles({ name: 'exam.md', mimeType: 'text/markdown', buffer: Buffer.from('Explain cohesion') })
  await dialog.getByRole('button', { name: '解析题目', exact: true }).click()
  await expect(dialog).toContainText('分值未识别')
  expect(confirmed).toBe(0)
  await dialog.getByRole('button', { name: '确认导入所选题目' }).click()
  await expect(dialog).toContainText('明确确认每题的题型与分值')
  await dialog.getByRole('spinbutton').first().fill('8')
  await dialog.locator('textarea').first().fill('教师编辑后的题目')
  await dialog.getByRole('button', { name: '确认导入所选题目' }).click()
  await expect(dialog).toBeHidden(); expect(confirmed).toBe(1)
  await expect(page.locator('.question-card')).toContainText('教师编辑后的题目')
})

test('BUG-16 三图统一预览、单图重解析保留其他编辑、删除与重排', async ({ page }) => {
  const course: Course = { id: 'batch', code: 'BAT', name: '批量导图', description: '', semesterId: 's', semesterName: '秋季', role: 'TEACHER', memberCount: 1, createdAt: now }
  let uploads = 0
  const calls: string[][] = []
  await mockPlatform(page, 'teacher', [course], (path, method, request) => {
    const draft = { id: 'batch-work', courseId: course.id, title: '多图作业', status: 'DRAFT', questions: [] }
    if (path === '/courses/batch/assignments') return { data: pageOf([draft]) }
    if (path === '/assignments/batch-work') return { data: draft }
    if (path.endsWith('/submissions')) return { data: [] }
    if (path.endsWith('/media') && method === 'POST') return { data: { id: String(++uploads), fileName: `${uploads}.png`, mediaType: 'image/png', sizeBytes: 1 } }
    if (/\/media\/\d+$/.test(path)) return { data: { id: path.split('/').pop(), fileName: 'source.png', mediaType: 'image/png', sizeBytes: 1 } }
    if (path.endsWith('/question-imports') && method === 'POST') {
      const params = new URL(request.url()).searchParams
      const ids = params.get('sourceFile')!.split(',')
      calls.push(ids)
      return { data: { id: 'batch-draft', sourceFile: ids[0], sourceName: 'source.png', sources: ids.map(id => ({ id, name: `${id}.png` })), confirmed: false,
        questions: ids.map((id, order) => ({ draftKey: params.get('reparseFile') === id ? `new-${id}` : `q-${id}`, type: 'SHORT_ANSWER', contentMarkdown: `题目 ${id}`, choices: [], correctAnswer: null, referenceAnswer: '', score: 5, order, sourceFile: id, sourcePage: 1, sourceRegion: [0,0,1,1], warnings: [] })) } }
    }
    return undefined
  })
  await page.goto('/teacher/courses/batch/assignments')
  await page.getByRole('button', { name: '智能导入题目', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '智能导入题目' })
  await dialog.locator('input[type=file]').setInputFiles(['one','two','three'].map(name => ({ name: `${name}.png`, mimeType: 'image/png', buffer: Buffer.from([1]) })))
  await dialog.getByRole('button', { name: '解析题目', exact: true }).click()
  await expect(dialog.locator('.import-question')).toHaveCount(3)
  expect(calls).toEqual([['1','2','3']])
  await dialog.locator('.import-question').first().locator('textarea').first().fill('保留教师编辑')
  await dialog.locator('.source-list li').nth(1).getByRole('button', { name: '重新解析此图（替换该图草稿）' }).click()
  await expect.poll(() => calls.length).toBe(2)
  await expect(dialog.locator('.import-question').first().locator('textarea').first()).toHaveValue('保留教师编辑')
  await dialog.locator('.source-list li').last().getByRole('button', { name: '图片上移' }).click()
  await expect(dialog.locator('.import-question').nth(1)).toContainText('three.png')
  await dialog.locator('.source-list li').last().getByRole('button', { name: '删除来源' }).click()
  await expect(dialog.locator('.import-question')).toHaveCount(2)
  await expect(dialog.locator('.import-question').first().locator('textarea').first()).toHaveValue('保留教师编辑')
})

test('BUG-10 选项序号重排且提交保留稳定 ID', async ({ page }) => {
  const course: Course = { id: 'choices', code: 'CHO', name: '选项回归课程', description: '', semesterId: 's', semesterName: '秋季', role: 'TEACHER', memberCount: 1, createdAt: now }
  const draft = { id: 'choices-work', courseId: course.id, title: '选项作业', status: 'DRAFT', maxAttempts: 1, questions: [] }
  let saved: { options: string[]; config: { choices: { id: string; label: string }[]; answerSpec: { correct: string } } } | undefined
  await mockPlatform(page, 'teacher', [course], (path, method, request) => {
    if (path === '/courses/choices/assignments') return { data: pageOf([draft]) }
    if (path === '/assignments/choices-work') return { data: draft }
    if (path.endsWith('/questions') && method === 'POST') { saved = request.postDataJSON(); return { data: { ...saved, id: 'choice-question' } } }
    if (path.endsWith('/submissions')) return { data: [] }
    return undefined
  })
  await page.goto('/teacher/courses/choices/assignments')
  await page.getByRole('button', { name: '手动添加题目', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '添加题目' })
  await dialog.locator('.el-form-item').filter({ hasText: '题型' }).locator('.el-select__wrapper').click()
  await page.getByRole('option', { name: 'SINGLE_CHOICE', exact: true }).click()
  await expect(dialog.locator('.choice-label')).toHaveText(['A','B','C','D'])
  await dialog.locator('textarea').first().fill('请选择正确的软件工程原则')
  for (const [index, text] of ['高内聚','高耦合','无测试','无需求'].entries()) await dialog.getByPlaceholder('选项内容', { exact: true }).nth(index).fill(text)
  await dialog.locator('.el-form-item').filter({ hasText: '正确选项' }).locator('.el-select__wrapper').click()
  await page.getByRole('option', { name: 'A. 高内聚', exact: true }).click()
  await dialog.getByRole('button', { name: '添加选项', exact: true }).click()
  await expect(dialog.locator('.choice-label')).toHaveText(['A','B','C','D','E'])
  await dialog.locator('.choice-row').last().getByRole('button', { name: '移除', exact: true }).click()
  await dialog.locator('.choice-row').first().getByRole('button', { name: '下移', exact: true }).click()
  await expect(dialog.locator('.choice-label')).toHaveText(['A','B','C','D'])
  await expect(dialog).not.toContainText('opt_')
  await dialog.getByRole('button', { name: '保存题目' }).click()
  await expect(dialog).toBeHidden()
  expect(saved!.config.choices[1]!.label).toBe('高内聚')
  expect(saved!.config.answerSpec.correct).toBe(saved!.options[1])
  await expect(page.locator('.question-card')).toContainText('B. 高内聚')
  await expect(page.locator('.question-card')).not.toContainText('opt_')
})

test('BUG-15/17 参考答案粘贴用途独立，窄屏完整显示题型', async ({ page }) => {
  const course: Course = { id: 'media-fix', code: 'MED', name: '媒体回归', description: '', semesterId: 's', semesterName: '秋季', role: 'TEACHER', memberCount: 1, createdAt: now }
  const purposes: string[] = []
  await mockPlatform(page, 'teacher', [course], (path, method, request) => {
    const draft = { id: 'media-work', courseId: course.id, title: '图片题', status: 'DRAFT', questions: [] }
    if (path === '/courses/media-fix/assignments') return { data: pageOf([draft]) }
    if (path === '/assignments/media-work') return { data: draft }
    if (path.endsWith('/submissions')) return { data: [] }
    if (path.endsWith('/media') && method === 'POST') {
      purposes.push(request.postData()!.match(/name="purpose"\r\n\r\n([^\r]+)/)![1]!)
      return { data: { id: String(purposes.length), fileName: 'paste.png', mediaType: 'image/png', sizeBytes: 1 } }
    }
    if (/\/media\/\d+$/.test(path)) return { data: { id: path.split('/').pop(), fileName: 'paste.png', mediaType: 'image/png', sizeBytes: 1 } }
    return undefined
  })
  await page.goto('/teacher/courses/media-fix/assignments')
  await page.getByRole('button', { name: '手动添加题目', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '添加题目' })
  await page.setViewportSize({ width: 390, height: 844 })
  for (const type of ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'SHORT_ANSWER', 'DOCUMENT_REPORT']) {
    await dialog.locator('.question-type-select .el-select__wrapper').click()
    await page.getByRole('option', { name: type, exact: true }).click()
    const label = dialog.locator('.question-type-select .el-select__selected-item').filter({ hasText: type })
    await expect(label).toBeVisible()
    expect(await label.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
    const box = await dialog.locator('.question-type-select').boundingBox()
    expect(box!.width).toBeGreaterThan(200)
    expect(box!.x + box!.width).toBeLessThanOrEqual(390)
  }
  for (const [step, index] of [0, 1, 1].entries()) {
    await dialog.locator('textarea').nth(index).evaluate(el => {
      const data = new DataTransfer()
      data.items.add(new File([new Uint8Array([1])], 'paste.png', { type: 'image/png' }))
      el.dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }))
    })
    await expect.poll(() => purposes.length).toBe(step + 1)
  }
  await expect.poll(() => purposes).toEqual(['QUESTION_CONTENT', 'REFERENCE_ANSWER', 'REFERENCE_ANSWER'])
  await expect(dialog.getByRole('button', { name: '移除题干附件' })).toHaveCount(1)
  await expect(dialog.getByRole('button', { name: '移除参考附件' })).toHaveCount(2)
})

test('教师配置题目、Rubric 与 Tutor 策略后发布作业', async ({ page }) => {
  const course: Course = {
    id: 'course-1', code: 'SE-101', name: '软件工程导论', description: '需求与设计基础', semesterId: 'semester-1',
    semesterName: '2026 秋季', role: 'TEACHER', memberCount: 32, createdAt: now,
  }
  let questionCreated = false
  let rubricSaved = false
  const rubricItems: Array<{ id: string; questionId: string; title: string; maxScore: number; orderIndex: number }> = []
  let policySaved = false
  let published = false
  const question = { id: 'question-1', type: 'ANALYSIS', prompt: '分析需求可追踪性的价值', points: 20, orderIndex: 0 }
  const draft = { id: 'assignment-1', courseId: 'course-1', title: '需求分析作业', description: '完成场景分析', status: 'DRAFT', maxAttempts: 2, questions: [] as typeof question[], tutorPolicy: { allowFullSolutionBeforeSubmit: false, fullSolutionAfterSubmit: true, fullSolutionAfterDue: true, allowLateSubmission: false, enabledOperations: ['HINT', 'EXPLAIN', 'CHECK_REASONING', 'ANALYZE_ERROR', 'EVALUATE_DRAFT', 'FULL_SOLUTION'], dueAtOverrides: {} } }
  await mockPlatform(page, 'teacher', [course], (path, method, request) => {
    if (path === '/courses/course-1/assignments' && method === 'GET') return { data: pageOf([{ ...draft }]) }
    if (path === '/assignments/assignment-1' && method === 'GET') return { data: draft }
    if (path === '/assignments/assignment-1/submissions' && method === 'GET') return { data: [] }
    if (path === '/assignments/assignment-1/rubric' && method === 'GET') return { data: { id: 'rubric-1', assignmentId: 'assignment-1', title: '评分量表', totalScore: 100, status: 'DRAFT', items: [] } }
    if (path === '/assignments/assignment-1/questions' && method === 'POST') {
      questionCreated = true
      draft.questions.push(question)
      return { data: question }
    }
    if (path === '/assignments/assignment-1/rubric' && method === 'PUT') {
      rubricSaved = true
      return { data: { id: 'rubric-1', assignmentId: 'assignment-1', ...request.postDataJSON(), items: rubricItems } }
    }
    if (path === '/assignments/assignment-1/rubric/items' && method === 'POST') {
      const item = { id: 'item-1', ...request.postDataJSON() }
      expect(item.maxScore).toBe(20)
      rubricItems.push(item)
      return { data: item }
    }
    if (path === '/assignments/assignment-1/tutor-policy' && method === 'PUT') {
      policySaved = true
      return { data: request.postDataJSON() }
    }
    if (path === '/assignments/assignment-1/transition' && method === 'POST') {
      published = true
      return { data: { ...draft, status: 'PUBLISHED' } }
    }
    return undefined
  })

  await page.goto('/teacher/courses/course-1/assignments')
  await page.getByRole('button', { name: '手动添加题目' }).click()
  const questionDialog = page.getByRole('dialog', { name: '添加题目' })
  await questionDialog.locator('.el-form-item').filter({ hasText: '题目' }).locator('textarea').fill('分析需求可追踪性的价值')
  await questionDialog.getByRole('button', { name: '保存题目' }).click()
  await expect(questionDialog).toBeHidden()
  await expect(page.locator('.question-card').getByText('分析需求可追踪性的价值')).toBeVisible()
  await expect(page.getByRole('button', { name: '发布作业' })).toBeDisabled()
  await page.getByRole('button', { name: '综合评分 / 20', exact: true }).click()
  await expect(page.getByText('已分配 20 / 20')).toBeVisible()
  await expect(page.getByText('AI 评分准则', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '添加分项', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '保存并发布评分规则' }).click()
  await page.getByRole('button', { name: '保存策略' }).click()
  await page.getByRole('button', { name: '发布作业' }).click()

  expect(questionCreated).toBe(true)
  expect(rubricSaved).toBe(true)
  expect(policySaved).toBe(true)
  expect(published).toBe(true)
  await expect(page.getByText('PUBLISHED').first()).toBeVisible()
})

for (const mode of ['RULE', 'AI_ASSISTED', 'MANUAL'] as const) {
  test('成绩工作台 ' + mode + ' 初评、确认与发布分离', async ({ page }) => {
    const course: Course = { id: 'grading', code: 'G', name: '评分课程', description: '', semesterId: 's', semesterName: '秋季', role: 'TEACHER', memberCount: 2, createdAt: now }
    let status = mode === 'MANUAL' ? 'WAITING_REVIEW' : 'PENDING_CONFIRMATION', score: number | null = null
    let manualSaved = false, confirmed = false, published = false
    const question = { id: 'q', type: mode === 'RULE' ? 'TRUE_FALSE' : 'SHORT_ANSWER', prompt: '评分测试题', points: 5, orderIndex: 0, referenceAnswer: '教师参考答案', config: { gradingMode: mode, answerSpec: mode === 'RULE' ? { correct: true } : {} } }
    const grade = () => ({ id: 'g', courseId: 'grading', assignmentId: 'a', assignmentTitle: '待批改作业', submissionId: 's', studentName: '学生甲', score, maxScore: 5, status, suggestedScore: mode === 'MANUAL' && !manualSaved ? null : 4, rubricItems: mode === 'MANUAL' && !manualSaved ? [] : [{ questionId: mode === 'AI_ASSISTED' ? undefined : 'q', rubricItemId: mode === 'AI_ASSISTED' ? 'r' : undefined, source: mode, suggestedScore: 4, feedback: '初评意见' }], gradedAt: now })
    await mockPlatform(page, 'teacher', [course], (path, method, request) => {
      if (path === '/grades') return { data: pageOf([grade()]) }
      if (path === '/submissions/s/grading') return { data: { grade: grade(), assignment: { id: 'a', title: '待批改作业', questions: [question] }, submission: { id: 's', assignmentId: 'a', status: 'SUBMITTED', attemptNumber: 1, answers: [{ questionId: 'q', answer: mode === 'RULE' ? true : '学生回答' }] }, targets: [{ rubricItemId: mode === 'AI_ASSISTED' ? 'r' : undefined, questionId: mode === 'AI_ASSISTED' ? undefined : 'q', maximum: 5 }] } }
      if (path === '/assignments/a/rubric') return { data: mode === 'AI_ASSISTED' ? { items: [{ id: 'r', questionId: 'q', title: '综合评分', maxScore: 5 }] } : null }
      if (path === '/submissions/s/grade/manual-review') { expect(request.postDataJSON().score).toBe(4); manualSaved = true; status = 'PENDING_CONFIRMATION'; return { data: grade() } }
      if (path === '/submissions/s/grade/confirm' && method === 'POST') { expect(request.postDataJSON().score).toBe(4); score = 4; status = 'CONFIRMED'; confirmed = true; return { data: grade() } }
      if (path === '/submissions/s/grade/publish') { expect(status).toBe('CONFIRMED'); status = 'PUBLISHED'; published = true; return { data: grade() } }
      return undefined
    })
    await page.goto('/teacher/grades')
    await expect(page.getByText('待确认 / —').first()).toBeVisible()
    await page.getByRole('button', { name: '查看与批改' }).click()
    const dialog = page.getByRole('dialog', { name: '批改与成绩发布' })
    await expect(dialog).toContainText('教师参考')
    if (mode === 'MANUAL') {
      await dialog.getByRole('spinbutton').fill('4')
      await dialog.getByRole('button', { name: '保存人工初评' }).click()
      await expect.poll(() => manualSaved).toBe(true)
      expect(confirmed).toBe(false); expect(published).toBe(false)
    }
    await dialog.getByRole('button', { name: '确认最终成绩' }).click()
    await expect.poll(() => confirmed).toBe(true); expect(published).toBe(false)
    await expect(dialog).toContainText('CONFIRMED')
    await dialog.getByRole('button', { name: '发布成绩给学生' }).click()
    await expect.poll(() => published).toBe(true)
    await expect(dialog).toContainText('PUBLISHED')
  })
}

test('文档初评选择学生报告或独立文件，不请求课程知识文档', async ({ page }) => {
  const course: Course = { id: 'doc', code: 'DOC', name: '文档评审课程', description: '', semesterId: 's', semesterName: '秋季', role: 'TEACHER', memberCount: 2, createdAt: now }
  const targets: unknown[] = []
  let knowledgeReads = 0
  await mockPlatform(page, 'teacher', [course], (path, method, request) => {
    if (path.includes('/knowledge/documents')) { knowledgeReads++; return { data: [] } }
    if (path === '/courses/doc/assignments') return { data: pageOf([{ id: 'a', title: '报告作业', status: 'PUBLISHED' }]) }
    if (path === '/assignments/a') return { data: { id: 'a', questions: [{ id: 'q', type: 'DOCUMENT_REPORT' }] } }
    if (path === '/assignments/a/submissions') return { data: [{ studentName: '学生甲', submission: { id: 's', status: 'SUBMITTED', attemptNumber: 1, answers: [{ questionId: 'q', answer: { text: '', assetIds: ['m'] } }] } }] }
    if (path === '/courses/doc/reviews') return { data: pageOf([]) }
    if (path === '/courses/doc/reviews/artifacts') return { data: { id: 'artifact', fileName: 'review.md' } }
    if (path === '/courses/doc/reviews/documents' && method === 'POST') { targets.push(request.postDataJSON()); return { data: { id: 'r', type: 'DOCUMENT', status: 'QUEUED', createdAt: now } } }
    return undefined
  })
  await page.goto('/teacher/courses/doc/reviews')
  await page.getByRole('tab', { name: '文档 Review' }).click()
  await page.getByText('选择学生报告附件', { exact: true }).click()
  await page.getByRole('option', { name: '学生甲 · 文档附件 #m' }).click()
  await page.getByRole('button', { name: '进入评审队列', exact: true }).click()
  await expect.poll(() => targets.length).toBe(1)
  expect(targets[0]).toMatchObject({ submissionId: 's', questionId: 'q', mediaId: 'm' })
  await page.getByText('上传待评审文档', { exact: true }).click()
  await expect(page.getByRole('radio', { name: '上传待评审文档', exact: true })).toBeChecked()
  await page.locator('input[type=file]').setInputFiles({ name: 'review.md', mimeType: 'text/markdown', buffer: Buffer.from('# Report') })
  await expect(page.getByText('review.md', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '进入评审队列', exact: true }).click()
  await expect.poll(() => targets.length).toBe(2)
  expect(targets[1]).toMatchObject({ artifactId: 'artifact' }); expect(knowledgeReads).toBe(0)
})

test('成绩批量发布先展示人数，再调用发布接口', async ({ page }) => {
  const course: Course = { id: 'batch', code: 'B', name: '发布课程', description: '', semesterId: 's', semesterName: '秋季', role: 'TEACHER', memberCount: 3, createdAt: now }
  let published = false
  await mockPlatform(page, 'teacher', [course], path => {
    if (path === '/courses/batch/assignments') return { data: pageOf([{ id: 'a', title: '测试作业', status: 'PUBLISHED' }]) }
    if (path === '/grades') return { data: pageOf([]) }
    if (path === '/assignments/a/grades/publication') return { data: { confirmed: 1, unconfirmed: 1, publishable: 1, published: 0 } }
    if (path === '/assignments/a/grades/publish') { published = true; return { data: { confirmed: 1, unconfirmed: 1, publishable: 0, published: 1 } } }
    return undefined
  })
  await page.goto('/teacher/grades')
  await page.getByText('选择作业批量发布', { exact: true }).click(); await page.getByRole('option', { name: '测试作业' }).click()
  await page.getByRole('button', { name: '预览并批量发布' }).click()
  const dialog = page.getByRole('dialog', { name: '批量发布成绩' })
  await expect(dialog).toContainText('已确认 1 人，未确认 1 人，可发布 1 人'); expect(published).toBe(false)
  await dialog.getByRole('button', { name: 'OK', exact: true }).click(); await expect.poll(() => published).toBe(true)
})

test('Review 展示 Sonar 权威 finding、失败重试和加载错误', async ({ page }) => {
  const course: Course = { id: 'course-1', code: 'SE-101', name: '软件工程导论', description: '', semesterId: 'semester-1', semesterName: '2026 秋季', role: 'TEACHER', memberCount: 2, createdAt: now }
  let retried = false
  let listFails = true
  await mockPlatform(page, 'teacher', [course], (path, method) => {
    if (path === '/courses/course-1/reviews' && method === 'GET') {
      if (listFails) return { status: 503, body: JSON.stringify({ code: 'UNAVAILABLE', message: '评审服务暂不可用' }) }
      return { data: pageOf([{ id: 'code-1', courseId: 'course-1', submissionId: 'sub-1', type: 'CODE', status: retried ? 'COMPLETED' : 'FAILED', errorMessage: retried ? undefined : '扫描失败', createdAt: now }]) }
    }
    if (path === '/courses/course-1/reviews/code-1/retry') {
      retried = true
      return { data: { id: 'code-1', courseId: 'course-1', submissionId: 'sub-1', type: 'CODE', status: 'COMPLETED', createdAt: now } }
    }
    if (path === '/courses/course-1/reviews/code-1/report') return { data: {
      id: 'report-code', reviewJobId: 'code-1', summary: '静态扫描完成', generatedAt: now, result: {
        sonar: { qualityGate: 'ERROR', projectKey: 'project', analysisId: 'scan' },
        findings: [{ findingKey: 'finding-1', rule: 'python:S1764', type: 'BUG', severity: 'MAJOR', component: 'bad.py', line: 2, message: 'Identical operands' }],
        analysis: { explanations: [{ findingKey: 'finding-1', explanation: '条件恒成立', impact: '分支无效', remediation: '比较不同操作数' }] },
      },
    } }
    return undefined
  })
  await page.goto('/teacher/courses/course-1/reviews')
  await expect(page.getByText('评审服务暂不可用')).toBeVisible()
  listFails = false
  await page.getByRole('button', { name: '重试加载', exact: true }).click()
  await page.getByRole('tab', { name: '代码 Review' }).click()
  await page.getByRole('button', { name: /代码提交 #sub-1/ }).click()
  await page.getByRole('button', { name: '重试任务' }).click()
  await page.getByRole('button', { name: /代码提交 #sub-1/ }).click()
  await expect(page.getByText('Identical operands', { exact: true })).toBeVisible()
  await expect(page.getByText('SonarQube Quality Gate: ERROR')).toBeVisible()
  await expect(page.getByText('AI 解释：条件恒成立')).toBeVisible()
  expect(retried).toBe(true)
})

test('管理员查看平台审计日志', async ({ page }) => {
  await mockPlatform(page, 'admin', [], (path, method) => {
    if (path === '/admin/audit-logs' && method === 'GET') {
      return {
        data: pageOf([
          { id: '9', actorId: '1', actorUsername: 'admin', courseId: null, action: 'ADMIN_USER_CREATE', targetType: 'USER', targetId: '2', outcome: 'SUCCEEDED', traceId: 'trace-9', occurredAt: now },
          { id: '8', actorId: null, actorUsername: null, courseId: null, action: 'AUTH_LOGIN', targetType: 'USER', targetId: '3', outcome: 'REJECTED', traceId: 'trace-8', occurredAt: now },
        ]),
      }
    }
    return undefined
  })

  await page.goto('/admin/audit')
  await expect(page.getByText('ADMIN_USER_CREATE')).toBeVisible()
  await expect(page.getByText('SUCCEEDED').first()).toBeVisible()
  await expect(page.getByText('REJECTED').first()).toBeVisible()
  // entries without an actor fall back to the system label
  await expect(page.getByText('系统').first()).toBeVisible()
  await expect(page.getByText('trace-9')).toBeVisible()
})

test('教师归档课程后课程标记为已归档', async ({ page }) => {
  const course: Course = {
    id: 'course-1', code: 'SE-101', name: '软件工程导论', description: '需求与设计基础', semesterId: 'semester-1',
    semesterName: '2026 秋季', role: 'TEACHER', memberCount: 32, createdAt: now,
  }
  let patchBody: Record<string, unknown> | undefined
  await mockPlatform(page, 'teacher', [course], (path, method, request) => {
    if (path === '/courses/course-1' && method === 'PATCH') {
      patchBody = request.postDataJSON() as Record<string, unknown>
      return { data: { ...course, status: 'ARCHIVED' } }
    }
    return undefined
  })

  await page.goto('/teacher/courses/course-1')
  await page.getByRole('button', { name: '归档课程' }).click()
  await page.getByRole('dialog', { name: '归档课程' }).getByRole('button', { name: 'OK' }).click()
  await expect.poll(() => patchBody !== undefined).toBe(true)
  expect(patchBody).toEqual({ status: 'ARCHIVED' })
  await expect(page.getByRole('button', { name: '恢复课程' })).toBeVisible()
  await expect(page.getByText('已归档').first()).toBeVisible()
})
