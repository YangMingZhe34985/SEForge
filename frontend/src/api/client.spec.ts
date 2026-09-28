import { describe, it, expect, vi } from 'vitest'
const http = vi.hoisted(() => ({ request: vi.fn(), get: vi.fn() }))
vi.mock('axios', () => ({ default: { create: () => http, isAxiosError: (e: { isAxiosError?: boolean }) => e?.isAxiosError === true } }))
import { apiRequest } from './client'
describe('API diagnostics', () => {
  it('distinguishes browser timeout from a missing network connection', async () => {
    http.request.mockRejectedValueOnce({ isAxiosError: true, code: 'ECONNABORTED' })
    await expect(apiRequest({ url: '/probe' })).rejects.toMatchObject({ code: 'REQUEST_TIMEOUT', message: expect.stringContaining('请求等待超时') })
    http.request.mockRejectedValueOnce({ isAxiosError: true, code: 'ERR_NETWORK' })
    await expect(apiRequest({ url: '/probe' })).rejects.toMatchObject({ message: expect.stringContaining('无法连接到服务器') })
  })
  it('preserves provider, Redis and structure errors with the server trace', async () => {
    for (const code of ['KNOWLEDGE_POINT_PROVIDER_FAILED','KNOWLEDGE_POINT_TIMEOUT','KNOWLEDGE_POINT_REDIS_UNAVAILABLE','KNOWLEDGE_POINT_INVALID_OUTPUT']) {
      http.request.mockRejectedValueOnce({ isAxiosError: true, response: { status: 503, data: { code, message: '可诊断错误', traceId: 'trace-safe', details: { stage: 'test' } } } })
      await expect(apiRequest({ url: '/probe' })).rejects.toMatchObject({ code, traceId: 'trace-safe', message: expect.stringContaining('trace-safe') })
    }
  })
  it('keeps Tutor HTTP code, stage and client trace even after a lost response', async () => {
    http.request.mockRejectedValueOnce({ isAxiosError: true, code: 'ECONNABORTED', config: { headers: { 'X-Trace-Id': 'tutor-client-trace' } } })
    await expect(apiRequest({ url: '/assignments/1/tutor' })).rejects.toMatchObject({ code: 'REQUEST_TIMEOUT', traceId: 'tutor-client-trace', message: expect.stringContaining('tutor-client-trace') })
    http.request.mockRejectedValueOnce({ isAxiosError: true, response: { status: 502, data: { code: 'TUTOR_TOOL_FAILED', message: '授权工具失败', traceId: 'server-trace', details: { stage: 'TOOL', tool: 'search_course_knowledge' } } } })
    await expect(apiRequest({ url: '/assignments/1/tutor' })).rejects.toMatchObject({ status: 502, code: 'TUTOR_TOOL_FAILED', traceId: 'server-trace', details: { stage: 'TOOL', tool: 'search_course_knowledge' } })
  })
})
