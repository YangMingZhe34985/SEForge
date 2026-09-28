import { Buffer } from 'node:buffer'
import { randomUUID } from 'node:crypto'
import { mkdir, writeFile } from 'node:fs/promises'
import { loadEnvFile } from 'node:process'
import { fileURLToPath } from 'node:url'
import { expect, test, type APIRequestContext } from '@playwright/test'

const origin = process.env.SEFORGE_REAL_RECOVERY_URL
const api = process.env.SEFORGE_REAL_RECOVERY_API
test.skip(!origin || !api, 'Opt in against the local launcher; creates synthetic course data and calls configured AI')
test.use({ trace: 'off' }) // No credentials, sessions or provider data in browser traces.

test('real PDF → worker → embedding → Milvus → streamed QA survives route and reload', async ({ page, playwright }, testInfo) => {
  test.setTimeout(240_000)
  loadEnvFile(fileURLToPath(new URL('../../.env', import.meta.url)))
  const admin = await playwright.request.newContext()
  async function call(client: APIRequestContext, path: string, data?: unknown, file?: Buffer) {
    const url = `${api}/api/v1${path}`
    const csrf = (await (await client.get(`${api}/api/v1/auth/csrf`)).json()).data
    const response = data !== undefined || file
      ? await client.post(url, { headers: { [csrf.headerName]: csrf.token },
        ...(file ? { multipart: { file: { name: 'recovery.pdf', mimeType: 'application/pdf', buffer: file } } } : { data }) })
      : await client.get(url)
    expect(response.ok(), `HTTP ${response.status()} ${path}`).toBeTruthy()
    return (await response.json()).data
  }
  try {
    await call(admin, '/auth/login', { identifier: process.env.SEFORGE_BOOTSTRAP_ADMIN_USERNAME,
      password: process.env.SEFORGE_BOOTSTRAP_ADMIN_PASSWORD, portal: 'ADMIN' })
    const identifier = `recovery-${Date.now()}`
    const password = `${randomUUID()}Aa1!`
    await call(admin, '/admin/users', { username: identifier, email: `${identifier}@example.invalid`,
      password, displayName: 'Recovery verification teacher', accountType: 'TEACHER', roles: ['USER'] })
    await call(page.request, '/auth/login', { identifier, password, portal: 'TEACHER' })
    const semesters = await call(admin, '/semesters')
    const course = await call(page.request, '/courses', { name: `Recovery verification ${Date.now()}`, semesterId: semesters[0].id })
    const bytes = pdf('Requirements must be clear, consistent, complete and verifiable. Every requirement needs a measurable acceptance test. Traceability links each requirement to its test evidence.')
    const upload = await call(page.request, `/courses/${course.id}/knowledge/documents`, undefined, bytes)
    await expect.poll(async () => (await call(page.request, `/courses/${course.id}/knowledge/documents`))[0]?.status,
      { timeout: 150_000, intervals: [1500] }).toBe('READY')
    let modelRequests = 0
    let cancellations = 0
    page.on('request', request => {
      if (request.method() === 'POST' && /\/conversations\/[^/]+\/messages$/.test(request.url())) modelRequests++
      if (request.method() === 'DELETE' && request.url().includes('/requests/')) cancellations++
    })
    await page.goto(`${origin}/teacher/courses/${course.id}/assistant`)
    await expect(page.getByText('从课程资料开始提问')).toBeVisible()
    await page.getByPlaceholder('输入与本课程相关的问题…').fill('According to this course, what properties must requirements have and how should they be verified? Give eight short bullet points with a Markdown heading and cite the course evidence.')
    await page.getByRole('button', { name: '发送', exact: true }).click()
    await expect(page.locator('.citation-card').first()).toBeVisible({ timeout: 30_000 })
    const conversation = new URL(page.url()).searchParams.get('conversation')
    const path = `/courses/${course.id}/conversations/${conversation}`
    const during = await call(page.request, `${path}/generation`)
    expect(during.status).toBe('PROCESSING')
    await page.getByRole('link', { name: '课程内容', exact: true }).click()
    await page.getByRole('link', { name: '课程助手', exact: true }).click()
    await expect(page.locator('.generation-status')).toBeVisible()
    await page.reload()
    await expect(page.locator('.generation-status')).toBeVisible()
    await expect.poll(async () => (await call(page.request, `${path}/generation`)).status,
      { timeout: 100_000, intervals: [1500] }).toBe('COMPLETED')
    await expect(page.locator('.message--assistant')).toHaveCount(1)
    await expect(page.locator('.citation-card').first()).toBeVisible()
    await page.reload()
    await expect(page.locator('.message--assistant')).toHaveCount(1)
    const history = await call(page.request, `${path}/messages`)
    const answers = history.items.filter((message: { role: string }) => message.role === 'ASSISTANT')
    expect(answers).toHaveLength(1)
    expect(answers[0].citations.length).toBeGreaterThan(0)
    expect(modelRequests).toBe(1)
    expect(cancellations).toBe(0)
    const evidence = JSON.stringify({
      courseId: course.id, documentId: upload.document.id, conversationId: conversation,
      status: 'COMPLETED', processingObserved: true, browserQaPosts: modelRequests, cancelRequests: cancellations,
      assistantMessages: answers.length, citations: answers[0].citations.length, traceId: during.traceId,
      provider: 'configured real provider; no route mocks',
    }, null, 2)
    const evidenceDirectory = fileURLToPath(new URL('../../logs/', import.meta.url))
    await mkdir(evidenceDirectory, { recursive: true })
    await writeFile(`${evidenceDirectory}/bug7-real-browser.json`, evidence)
    await testInfo.attach('recovery-runtime.json', { contentType: 'application/json', body: Buffer.from(evidence) })
  } finally { await admin.dispose() }
})

function pdf(text: string) {
  const stream = `BT /F1 12 Tf 40 750 Td (${text}) Tj ET`
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>', `<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`]
  let out = '%PDF-1.4\n'
  const offsets = [0]
  objects.forEach((object, index) => { offsets.push(Buffer.byteLength(out)); out += `${index + 1} 0 obj\n${object}\nendobj\n` })
  const xref = Buffer.byteLength(out)
  out += 'xref\n0 6\n0000000000 65535 f \n' + offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')
  return Buffer.from(out + `trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`)
}
