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

const users = {
  admin: { id: '1', email: 'admin@seforge.test', username: 'admin', displayName: '平台管理员', accountType: 'TEACHER', roles: ['ADMIN', 'USER'], enabled: true },
  teacher: { id: '2', email: 'teacher@seforge.test', username: 'teacher', displayName: '任课教师', accountType: 'TEACHER', roles: ['USER'], enabled: true },
  student: { id: '3', email: 'student@seforge.test', username: 'student', displayName: '学生甲', accountType: 'STUDENT', roles: ['USER'], enabled: true },
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

test('管理员创建教师账号', async ({ page }) => {
  let createdBody: Record<string, unknown> | undefined
  let updatedRoles: unknown
  await mockPlatform(page, 'admin', [], (path, method, request) => {
    if (path === '/admin/users' && method === 'GET') return { data: pageOf([]) }
    if (path === '/admin/users' && method === 'POST') {
      createdBody = request.postDataJSON() as Record<string, unknown>
      return {
        data: {
          id: 'teacher-new',
          ...createdBody,
          roles: ['USER'],
          enabled: true,
        },
      }
    }
    if (path === '/admin/users/teacher-new/roles' && method === 'PUT') {
      updatedRoles = (request.postDataJSON() as { roles: unknown }).roles
      return {
        data: {
          id: 'teacher-new',
          ...createdBody,
          roles: ['USER', 'ADMIN'],
          enabled: true,
        },
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

test('教师创建课程并上传课程资料', async ({ page }) => {
  const courses: Course[] = []
  const courseClasses: Array<Record<string, unknown>> = []
  const invites: Array<Record<string, unknown>> = []
  let uploaded = false
  let jobPolls = 0
  let inviteRequest: Record<string, unknown> | undefined
  await mockPlatform(page, 'teacher', courses, (path, method, request) => {
    if (path === '/courses' && method === 'POST') {
      const input = request.postDataJSON() as Record<string, string>
      const course: Course = {
        id: 'course-new',
        code: input.code,
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
    if (path === '/courses/course-new/knowledge/documents' && method === 'POST') {
      uploaded = true
      return {
        data: {
          document: {
            id: 'document-1', courseId: 'course-new', name: 'requirements.md', mediaType: 'text/markdown',
            sizeBytes: 35, checksum: 'sha256', status: 'QUEUED', createdAt: now,
          },
          job: { id: 'job-1', type: 'DOCUMENT_INGESTION', status: 'QUEUED', attempts: 0, maxAttempts: 3, cancelRequested: false, createdAt: now, updatedAt: now },
          duplicate: false,
        },
      }
    }
    if (path === '/courses/course-new/knowledge/documents' && method === 'GET') {
      return { data: uploaded ? [{
        id: 'document-1', courseId: 'course-new', name: 'requirements.md', mediaType: 'text/markdown',
        sizeBytes: 35, checksum: 'sha256', status: 'READY', createdAt: now,
        job: { id: 'job-1', type: 'INGEST_DOCUMENT', status: 'COMPLETED', attempts: 1, maxAttempts: 3, cancelRequested: false, createdAt: now, updatedAt: now },
      }] : [] }
    }
    if (path === '/jobs/job-1' && method === 'GET') {
      jobPolls += 1
      return { data: { id: 'job-1', type: 'INGEST_DOCUMENT', status: jobPolls > 1 ? 'COMPLETED' : 'RUNNING', attempts: 1, maxAttempts: 3, cancelRequested: false, createdAt: now, updatedAt: now } }
    }
    if (path === '/courses/course-new/classes' && method === 'GET') return { data: courseClasses }
    if (path === '/courses/course-new/classes' && method === 'POST') {
      const input = request.postDataJSON() as Record<string, unknown>
      const courseClass = { id: 'class-1', courseId: 'course-new', ...input, createdAt: now }
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
  await dialog.locator('.el-form-item').filter({ hasText: '课程编号' }).locator('input').fill('SE-2026')
  await dialog.locator('.el-form-item').filter({ hasText: '课程名称' }).locator('input').fill('软件工程实践')
  await dialog.locator('.el-form-item').filter({ hasText: '所属学期' }).locator('.el-select').click()
  await page.getByRole('option', { name: '2026 秋季' }).click()
  await dialog.getByRole('button', { name: '创建', exact: true }).click()
  await expect(page).toHaveURL(/\/teacher\/courses\/course-new$/)
  await expect(page.getByText('软件工程实践', { exact: true }).first()).toBeVisible()

  await page.locator('input[type="file"]').setInputFiles({
    name: 'requirements.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from('# Software requirements specification'),
  })
  await expect(page.getByText('requirements.md')).toBeVisible()
  await expect(page.getByText('READY', { exact: true })).toBeVisible({ timeout: 5000 })
  expect(uploaded).toBe(true)
  expect(jobPolls).toBeGreaterThan(1)

  await page.getByRole('tab', { name: '教学管理' }).click()
  await page.getByRole('button', { name: '创建教学班' }).click()
  const classDialog = page.getByRole('dialog', { name: '创建教学班' })
  await classDialog.locator('.el-form-item').filter({ hasText: '教学班代码' }).locator('input').fill('SE-01')
  await classDialog.locator('.el-form-item').filter({ hasText: '教学班名称' }).locator('input').fill('软件工程 1 班')
  await classDialog.getByRole('button', { name: '创建', exact: true }).click()
  await expect(page.getByText('软件工程 1 班', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '创建邀请码' }).click()
  const inviteDialog = page.getByRole('dialog', { name: '创建课程邀请码' })
  await inviteDialog.locator('.el-form-item').filter({ hasText: '加入教学班' }).locator('.el-select').click()
  await page.getByRole('option', { name: '软件工程 1 班 (SE-01)' }).click()
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
  await registerForm.locator('.el-form-item').filter({ hasText: /^密码/ }).locator('input').fill('SecurePass-2026')
  await registerForm.locator('.el-form-item').filter({ hasText: '确认密码' }).locator('input').fill('SecurePass-2026')
  await registerForm.getByRole('button', { name: '创建学生账号' }).click()
  await expect(page.getByText('注册成功，请使用新账号登录')).toBeVisible()
  expect(registeredBody).toMatchObject({ username: 'student.two', displayName: '学生乙', email: 'student.two@seforge.test' })

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
})

test('教师配置题目、Rubric 与 Tutor 策略后发布作业', async ({ page }) => {
  const course: Course = {
    id: 'course-1', code: 'SE-101', name: '软件工程导论', description: '需求与设计基础', semesterId: 'semester-1',
    semesterName: '2026 秋季', role: 'TEACHER', memberCount: 32, createdAt: now,
  }
  let questionCreated = false
  let rubricSaved = false
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
      return { data: { id: 'rubric-1', assignmentId: 'assignment-1', ...request.postDataJSON(), items: [] } }
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
  await page.getByRole('button', { name: '添加题目' }).click()
  const questionDialog = page.getByRole('dialog', { name: '添加题目' })
  await questionDialog.locator('.el-form-item').filter({ hasText: '题目' }).locator('textarea').fill('分析需求可追踪性的价值')
  await questionDialog.getByRole('button', { name: '保存题目' }).click()
  await expect(page.getByText('分析需求可追踪性的价值')).toBeVisible()
  await page.getByRole('button', { name: '保存 Rubric' }).click()
  await page.getByRole('button', { name: '保存策略' }).click()
  await page.getByRole('button', { name: '发布作业' }).click()

  expect(questionCreated).toBe(true)
  expect(rubricSaved).toBe(true)
  expect(policySaved).toBe(true)
  expect(published).toBe(true)
  await expect(page.getByText('PUBLISHED').first()).toBeVisible()
})

test('教师查看 AI 建议后确认最终成绩', async ({ page }) => {
  const course: Course = {
    id: 'course-1', code: 'SE-101', name: '软件工程导论', description: '需求与设计基础', semesterId: 'semester-1',
    semesterName: '2026 秋季', role: 'TEACHER', memberCount: 32, createdAt: now,
  }
  let confirmed = false
  await mockPlatform(page, 'teacher', [course], (path, method) => {
    if (path === '/courses/course-1/reviews' && method === 'GET') {
      return { data: pageOf([{ id: 'review-1', courseId: 'course-1', submissionId: 'submission-1', type: 'ASSIGNMENT', status: 'COMPLETED', createdAt: now }]) }
    }
    if (path === '/courses/course-1/reviews/review-1/report' && method === 'GET') {
      return { data: { id: 'report-1', reviewJobId: 'review-1', summary: '论证完整，证据仍可加强。', result: { totalSuggestedScore: 86, rubricItems: [{ rubricItemId: 'rubric-1', suggestedScore: 43, evidence: ['覆盖主要场景'], feedback: '补充异常流' }] }, generatedAt: now } }
    }
    if (path === '/submissions/submission-1/grade/confirm' && method === 'POST') {
      confirmed = true
      return { data: { id: 'grade-1', courseId: 'course-1', assignmentId: 'assignment-1', assignmentTitle: '需求分析作业', score: 86, maxScore: 100, status: 'FINAL', feedback: '论证完整，证据仍可加强。', gradedAt: now } }
    }
    return undefined
  })

  await page.goto('/teacher/courses/course-1/reviews')
  await page.getByRole('tab', { name: '作业 Review' }).click()
  await page.getByRole('button', { name: /作业提交 #submission-1/ }).click()
  await expect(page.getByText('AI 建议分')).toBeVisible()
  await page.getByRole('button', { name: '教师确认成绩' }).click()
  const dialog = page.getByRole('dialog', { name: '教师确认最终成绩' })
  await dialog.getByRole('button', { name: '确认并发布' }).click()
  await expect.poll(() => confirmed).toBe(true)
  await expect(page.getByText('最终成绩已由教师确认')).toBeVisible()
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
