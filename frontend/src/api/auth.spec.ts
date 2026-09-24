import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock, csrfMock, clearMock } = vi.hoisted(() => ({
  requestMock: vi.fn(), csrfMock: vi.fn(), clearMock: vi.fn(),
}))
vi.mock('./client', () => ({ apiRequest: requestMock, refreshCsrf: csrfMock, clearCsrf: clearMock }))

import { authApi } from './auth'

describe('Phase 1 login contracts', () => {
  beforeEach(() => requestMock.mockReset())

  it('sends student number with an explicit student portal intent', async () => {
    requestMock.mockResolvedValueOnce({ user: { studentNo: '2026-001' } })
    await authApi.login('2026-001', 'secret', 'STUDENT')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/auth/login', method: 'POST', data: { identifier: '2026-001', password: 'secret', portal: 'STUDENT' },
    })
  })

  it('keeps administrator and teacher portal intents explicit', async () => {
    requestMock.mockResolvedValue({ user: {} })
    await authApi.login('teacher', 'secret', 'TEACHER')
    await authApi.login('admin', 'secret', 'ADMIN')
    expect(requestMock.mock.calls.map(([request]) => request.data.portal)).toEqual(['TEACHER', 'ADMIN'])
  })

  it('requires student number when registering', async () => {
    requestMock.mockResolvedValueOnce({})
    await authApi.register({ email: 's@example.test', username: 'student', displayName: 'Student',
      studentNo: '2026-001', password: 'strong-password' })
    expect(requestMock.mock.calls[0]?.[0]?.data.studentNo).toBe('2026-001')
  })
})
