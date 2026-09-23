import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))
vi.mock('./client', () => ({ apiRequest: requestMock }))

import { jobApi } from './jobs'

describe('jobApi contracts', () => {
  beforeEach(() => requestMock.mockReset())

  it('gets and cancels an owned durable job', async () => {
    requestMock.mockResolvedValue({ id: '9' })

    await jobApi.get('9')
    await jobApi.cancel('9')

    expect(requestMock).toHaveBeenNthCalledWith(1, { url: '/jobs/9' })
    expect(requestMock).toHaveBeenNthCalledWith(2, { url: '/jobs/9', method: 'DELETE' })
  })
})
