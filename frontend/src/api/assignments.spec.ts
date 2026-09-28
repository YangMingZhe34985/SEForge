import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))
vi.mock('./client', () => ({ apiRequest: requestMock }))

import { assignmentApi } from './assignments'

describe('assignmentApi contracts', () => {
  beforeEach(() => requestMock.mockReset())
  it('gives Tutor a dedicated deadline and reuses the correlation/idempotency key', async () => {
    requestMock.mockResolvedValue({ content: '提示' })
    const key = '00000000-0000-4000-8000-000000000001'
    await assignmentApi.tutor('11', '22', 'HINT', '', key)
    await assignmentApi.tutor('11', '22', 'HINT', '', key)
    expect(requestMock).toHaveBeenCalledTimes(2)
    expect(requestMock).toHaveBeenLastCalledWith({ url: '/assignments/11/tutor', method: 'POST', timeout: 630000,
      headers: { 'X-Trace-Id': key }, data: { questionId: '22', action: 'HINT', draftAnswer: '', requestKey: key } })
  })

  it('sets and clears an individual deadline extension', async () => {
    requestMock.mockResolvedValue({ dueAtOverrides: {} })

    await assignmentApi.setExtension('11', '22', '2026-10-01T12:00:00+08:00')
    await assignmentApi.setExtension('11', '22')

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/assignments/11/extensions/22',
      method: 'PUT',
      data: { dueAt: '2026-10-01T12:00:00+08:00' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/assignments/11/extensions/22',
      method: 'PUT',
      data: { dueAt: null },
    })
  })
})
