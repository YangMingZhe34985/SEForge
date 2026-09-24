import { Buffer } from 'node:buffer'
import { expect, test, type APIRequestContext } from '@playwright/test'

const baseURL = process.env.SEFORGE_REAL_PHASE1E_BASE_URL
const adminIdentifier = process.env.SEFORGE_REAL_ADMIN_USERNAME
const adminPassword = process.env.SEFORGE_REAL_ADMIN_PASSWORD

test.skip(!baseURL || !adminIdentifier || !adminPassword, 'Run only against an isolated Phase 1-E Compose project')

test('isolated real API: three identities, teaching loop, storage, and denial matrix', async ({ playwright }) => {
  const suffix = Date.now().toString(36)
  const teacherUsername = `teacher.${suffix}`
  const teacherBUsername = `teacherb.${suffix}`
  const studentUsername = `student.${suffix}`
  const studentNo = `E${suffix}`.toUpperCase()
  const password = `Safe-Phase1e-${suffix}`
  const contexts: APIRequestContext[] = []

  async function session() {
    const context = await playwright.request.newContext({ baseURL })
    contexts.push(context)
    const csrfResponse = await context.get('/api/v1/auth/csrf')
    expect(csrfResponse.status()).toBe(200)
    const csrf = (await csrfResponse.json()).data.token as string
    return { context, csrf }
  }

  async function call(actor: { context: APIRequestContext; csrf: string }, method: 'get' | 'post' | 'patch',
    path: string, data?: unknown) {
    return actor.context[method](path, {
      headers: { 'X-XSRF-TOKEN': actor.csrf },
      ...(data === undefined ? {} : { data }),
    })
  }

  try {
    const admin = await session()
    const adminLogin = await call(admin, 'post', '/api/v1/auth/login', {
      identifier: adminIdentifier, password: adminPassword, portal: 'ADMIN',
    })
    expect(adminLogin.status(), await adminLogin.text()).toBe(200)
    expect((await adminLogin.json()).data.user.roles).toContain('ADMIN')

    const csv = Buffer.from('accountType,studentNo,username,email,displayName\n'
      + `STUDENT,I${suffix.toUpperCase()},import.${suffix},import.${suffix}@example.test,Imported Student\n`
      + `STUDENT,I${suffix.toUpperCase()},duplicate.${suffix},duplicate.${suffix}@example.test,Duplicate Student\n`)
    const previewResponse = await admin.context.post('/api/v1/admin/users/import/preview', {
      headers: { 'X-XSRF-TOKEN': admin.csrf },
      multipart: { file: { name: 'accounts.csv', mimeType: 'text/csv', buffer: csv } },
    })
    expect(previewResponse.status(), await previewResponse.text()).toBe(200)
    const preview = (await previewResponse.json()).data
    expect(preview.valid).toBe(1)
    expect(preview.rejected).toBe(1)
    const importPath = `/api/v1/admin/users/import/confirm?digest=${preview.digest}`
    const confirmImport = () => admin.context.post(importPath, {
      headers: { 'X-XSRF-TOKEN': admin.csrf },
      multipart: { file: { name: 'accounts.csv', mimeType: 'text/csv', buffer: csv } },
    })
    const importedResponse = await confirmImport()
    expect(importedResponse.status(), await importedResponse.text()).toBe(200)
    const imported = (await importedResponse.json()).data
    expect(imported.created).toBe(1)
    expect(imported.skipped).toBe(1)
    expect(imported.rows[0].initialPassword).toBeTruthy()
    const repeatedResponse = await confirmImport()
    expect(repeatedResponse.status(), await repeatedResponse.text()).toBe(200)
    expect((await repeatedResponse.json()).data.created).toBe(0)

    const semestersResponse = await call(admin, 'get', '/api/v1/semesters')
    expect(semestersResponse.status()).toBe(200)
    const existing = ((await semestersResponse.json()).data as Array<{ id: number; status: string }>)
      .find((semester) => semester.status === 'ACTIVE')
    let semesterId = existing?.id
    if (!semesterId) {
      const semesterResponse = await call(admin, 'post', '/api/v1/admin/semesters', {
        code: `P1E-${suffix}`, name: 'Phase 1-E isolated verification',
        startsOn: '2026-09-01', endsOn: '2027-01-31', status: 'ACTIVE',
      })
      expect(semesterResponse.status(), await semesterResponse.text()).toBe(201)
      semesterId = (await semesterResponse.json()).data.id as number
    }

    async function createTeacher(username: string) {
      const response = await call(admin, 'post', '/api/v1/admin/users', {
        username, email: `${username}@example.test`, displayName: username,
        password, accountType: 'TEACHER', roles: ['USER'],
      })
      expect(response.status(), await response.text()).toBe(201)
      return (await response.json()).data.id as number
    }
    await createTeacher(teacherUsername)
    await createTeacher(teacherBUsername)

    const teacher = await session()
    expect((await call(teacher, 'post', '/api/v1/auth/login', {
      identifier: teacherUsername, password, portal: 'TEACHER',
    })).status()).toBe(200)
    const teacherB = await session()
    expect((await call(teacherB, 'post', '/api/v1/auth/login', {
      identifier: teacherBUsername, password, portal: 'TEACHER',
    })).status()).toBe(200)

    const courseResponse = await call(teacher, 'post', '/api/v1/courses', {
      code: `P1E-${suffix}`, name: 'Phase 1-E verification course', semesterId,
    })
    expect(courseResponse.status(), await courseResponse.text()).toBe(201)
    const courseId = (await courseResponse.json()).data.id as number
    const otherResponse = await call(teacher, 'post', '/api/v1/courses', {
      code: `P1E-OTHER-${suffix}`, name: 'Private comparison course', semesterId,
    })
    expect(otherResponse.status()).toBe(201)
    const otherCourseId = (await otherResponse.json()).data.id as number

    const classResponse = await call(teacher, 'post', `/api/v1/courses/${courseId}/classes`, {
      code: 'C1', name: 'Phase 1-E class', capacity: 30, primaryClass: true,
    })
    expect(classResponse.status(), await classResponse.text()).toBe(201)
    const classId = (await classResponse.json()).data.id as number

    const inviteResponse = await call(teacher, 'post', `/api/v1/courses/${courseId}/invites`, {
      classId, memberRole: 'STUDENT', maxUses: 2,
    })
    expect(inviteResponse.status(), await inviteResponse.text()).toBe(201)
    const inviteCode = (await inviteResponse.json()).data.code as string

    const resourceResponse = await teacher.context.post(`/api/v1/courses/${courseId}/resources/upload`, {
      headers: { 'X-XSRF-TOKEN': teacher.csrf },
      multipart: { file: { name: 'phase1e.txt', mimeType: 'text/plain', buffer: Buffer.from('Phase 1-E resource') } },
    })
    expect(resourceResponse.status(), await resourceResponse.text()).toBe(201)
    const resourceId = (await resourceResponse.json()).data.id as number

    const announcementResponse = await call(teacher, 'post', `/api/v1/courses/${courseId}/announcements`, {
      title: 'Phase 1-E notice', content: 'Welcome to the course',
    })
    expect(announcementResponse.status(), await announcementResponse.text()).toBe(201)

    const student = await session()
    const registerResponse = await call(student, 'post', '/api/v1/auth/register', {
      username: studentUsername, email: `${studentUsername}@example.test`,
      displayName: 'Phase 1-E Student', studentNo, password,
    })
    expect(registerResponse.status(), await registerResponse.text()).toBe(201)
    const studentId = (await registerResponse.json()).data.id as number
    expect((await call(student, 'post', '/api/v1/auth/login', {
      identifier: studentNo, password, portal: 'STUDENT',
    })).status()).toBe(200)

    expect((await call(student, 'post', '/api/v1/courses/join', { inviteCode })).status()).toBe(200)
    expect((await call(student, 'get', `/api/v1/courses/${courseId}`)).status()).toBe(200)
    const resourcesResponse = await call(student, 'get', `/api/v1/courses/${courseId}/resources`)
    expect(resourcesResponse.status()).toBe(200)
    expect((await resourcesResponse.json()).data.some((item: { id: number }) => item.id === resourceId)).toBe(true)
    const downloadResponse = await call(student, 'get', `/api/v1/courses/${courseId}/resources/${resourceId}/download`)
    expect(downloadResponse.status()).toBe(200)
    expect(await downloadResponse.text()).toBe('Phase 1-E resource')
    const announcementsResponse = await call(student, 'get', `/api/v1/courses/${courseId}/announcements`)
    expect(announcementsResponse.status()).toBe(200)
    expect((await announcementsResponse.json()).data.items[0].title).toBe('Phase 1-E notice')

    expect((await call(student, 'get', '/api/v1/admin/users')).status()).toBe(403)
    expect((await call(student, 'post', '/api/v1/courses', {
      code: `FORGED-${suffix}`, name: 'Forbidden', semesterId,
    })).status()).toBe(403)
    expect((await call(student, 'patch', `/api/v1/courses/${courseId}`, { name: 'Forbidden' })).status()).toBe(403)
    expect((await call(student, 'get', `/api/v1/courses/${otherCourseId}`)).status()).toBe(403)
    expect((await call(student, 'get', `/api/v1/courses/${otherCourseId}/resources`)).status()).toBe(403)
    expect((await call(teacherB, 'post', `/api/v1/courses/${courseId}/classes`, {
      code: 'FORGED', name: 'Forbidden class',
    })).status()).toBe(403)
    expect((await call(admin, 'post', `/api/v1/courses/${courseId}/classes`, {
      code: 'ADMIN', name: 'Improper admin class',
    })).status()).toBe(403)
    expect((await call(admin, 'patch', `/api/v1/admin/courses/${courseId}`, {
      name: 'Administratively governed course',
    })).status()).toBe(200)

    const promoteToTa = await teacher.context.put(`/api/v1/courses/${courseId}/members/${studentId}`, {
      headers: { 'X-XSRF-TOKEN': teacher.csrf }, data: { classId, role: 'TA' },
    })
    expect(promoteToTa.status(), await promoteToTa.text()).toBe(200)
    expect((await call(student, 'post', `/api/v1/courses/${courseId}/chapters`, {
      title: 'TA-managed chapter', sortOrder: 1,
    })).status()).toBe(201)
    expect((await call(student, 'post', `/api/v1/courses/${courseId}/classes`, {
      code: 'TA-FORGED', name: 'TA cannot govern classes',
    })).status()).toBe(403)

    const disabled = await call(admin, 'patch', `/api/v1/admin/users/${studentId}`, { enabled: false })
    expect(disabled.status(), await disabled.text()).toBe(200)
    expect((await call(student, 'get', '/api/v1/auth/me')).status()).toBe(401)
    const disabledSession = await session()
    const deniedLogin = await call(disabledSession, 'post', '/api/v1/auth/login', {
      identifier: studentNo, password, portal: 'STUDENT',
    })
    expect(deniedLogin.status()).toBe(401)
  } finally {
    for (const context of contexts) await context.dispose()
  }
})

test('isolated real browser: Docker admin/student and Vite teacher login', async ({ browser, playwright }) => {
  const suffix = Date.now().toString(36)
  const teacherUsername = `browser.teacher.${suffix}`
  const studentNo = `B${suffix}`.toUpperCase()
  const password = `Safe-Browser-${suffix}`
  const adminApi = await playwright.request.newContext({ baseURL })
  try {
    const csrf = (await (await adminApi.get('/api/v1/auth/csrf')).json()).data.token as string
    const adminLogin = await adminApi.post('/api/v1/auth/login', {
      headers: { 'X-XSRF-TOKEN': csrf },
      data: { identifier: adminIdentifier, password: adminPassword, portal: 'ADMIN' },
    })
    expect(adminLogin.status()).toBe(200)
    const created = await adminApi.post('/api/v1/admin/users', {
      headers: { 'X-XSRF-TOKEN': csrf },
      data: { username: teacherUsername, email: `${teacherUsername}@example.test`,
        displayName: 'Browser Teacher', password, accountType: 'TEACHER', roles: ['USER'] },
    })
    expect(created.status(), await created.text()).toBe(201)

    const studentApi = await playwright.request.newContext({ baseURL })
    try {
      const studentCsrf = (await (await studentApi.get('/api/v1/auth/csrf')).json()).data.token as string
      const registered = await studentApi.post('/api/v1/auth/register', {
        headers: { 'X-XSRF-TOKEN': studentCsrf },
        data: { username: `browser.student.${suffix}`, email: `browser.student.${suffix}@example.test`,
          displayName: 'Browser Student', studentNo, password },
      })
      expect(registered.status(), await registered.text()).toBe(201)
    } finally {
      await studentApi.dispose()
    }

    const adminContext = await browser.newContext()
    const teacherContext = await browser.newContext()
    const studentContext = await browser.newContext()
    try {
      const adminPage = await adminContext.newPage()
      await adminPage.goto(`${baseURL}/login/admin`)
      const adminForm = adminPage.locator('form').filter({ has: adminPage.getByRole('button', { name: '进入工作台' }) })
      await adminForm.locator('input').first().fill(adminIdentifier!)
      await adminForm.locator('input[type="password"]').fill(adminPassword!)
      await adminForm.getByRole('button', { name: '进入工作台' }).click()
      await expect(adminPage).toHaveURL(/\/admin$/)
      await expect(adminPage.getByRole('heading', { name: '管理概览' })).toBeVisible()

      const teacherPage = await teacherContext.newPage()
      await teacherPage.goto('http://127.0.0.1:4173/login')
      await teacherPage.getByText('教师', { exact: true }).click()
      const teacherForm = teacherPage.locator('form').filter({ has: teacherPage.getByRole('button', { name: '进入工作台' }) })
      await teacherForm.locator('input[autocomplete="username"]').fill(teacherUsername)
      await teacherForm.locator('input[type="password"]').fill(password)
      await teacherForm.getByRole('button', { name: '进入工作台' }).click()
      await expect(teacherPage).toHaveURL(/\/teacher$/)
      await expect(teacherPage.getByRole('button', { name: '创建课程' })).toBeVisible()

      const studentPage = await studentContext.newPage()
      await studentPage.goto(`${baseURL}/login`)
      const studentForm = studentPage.locator('form').filter({ has: studentPage.getByRole('button', { name: '进入工作台' }) })
      await studentForm.locator('input[autocomplete="username"]').fill(studentNo)
      await studentForm.locator('input[type="password"]').fill(password)
      await studentForm.getByRole('button', { name: '进入工作台' }).click()
      await expect(studentPage).toHaveURL(/\/student$/)
      await expect(studentPage.getByRole('button', { name: '使用邀请码加入' })).toBeVisible()
    } finally {
      await adminContext.close()
      await teacherContext.close()
      await studentContext.close()
    }
  } finally {
    await adminApi.dispose()
  }
})
