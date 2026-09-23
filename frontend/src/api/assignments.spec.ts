import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))
vi.mock('./client', () => ({ apiRequest: requestMock }))

import { assignmentApi } from './assignments'

describe('assignmentApi contracts', () => {
  beforeEach(() => requestMock.mockReset())

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
