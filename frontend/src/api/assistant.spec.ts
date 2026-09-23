import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))
vi.mock('./client', () => ({ apiRequest: requestMock }))

import { assistantApi } from './assistant'

describe('assistantApi contracts', () => {
  beforeEach(() => requestMock.mockReset())

  it('lets the server auto-title a new conversation', async () => {
    requestMock.mockResolvedValueOnce({ id: '1' })

    await assistantApi.createConversation('42')

    expect(requestMock).toHaveBeenCalledWith({
      url: '/courses/42/conversations',
      method: 'POST',
      data: {},
    })
  })

  it('returns the latest one hundred messages across a page boundary', async () => {
    const page = (prefix: string, count: number) => Array.from({ length: count }, (_, index) => ({
      id: `${prefix}-${index}`,
      role: 'USER',
      content: `${prefix}-${index}`,
      createdAt: '2026-09-22T00:00:00Z',
    }))
    requestMock
      .mockResolvedValueOnce({ items: page('old', 100), page: 0, size: 100, total: 201 })
      .mockResolvedValueOnce({ items: page('tail', 1), page: 2, size: 100, total: 201 })
      .mockResolvedValueOnce({ items: page('middle', 100), page: 1, size: 100, total: 201 })

    const result = await assistantApi.messages('42', '9')

    expect(result.items).toHaveLength(100)
    expect(result.items[0]?.id).toBe('middle-1')
    expect(result.items.at(-1)?.id).toBe('tail-0')
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/courses/42/conversations/9/messages',
      params: { page: 2, size: 100 },
    })
  })
})
