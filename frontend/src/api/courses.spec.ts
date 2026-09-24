import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))

vi.mock('./client', () => ({
  apiRequest: requestMock,
  apiUrl: (path: string) => `/api/v1${path}`,
}))

import { courseApi } from './courses'

describe('courseApi management contracts', () => {
  beforeEach(() => requestMock.mockReset())

  it('creates a semester through the admin contract', async () => {
    requestMock.mockResolvedValueOnce({ id: '9' })
    const input = {
      code: '2026-FALL',
      name: '2026 秋季学期',
      startsOn: '2026-09-01',
      endsOn: '2027-01-15',
      status: 'PLANNED' as const,
    }

    await courseApi.createSemester(input)

    expect(requestMock).toHaveBeenCalledWith({
      url: '/admin/semesters',
      method: 'POST',
      data: input,
    })
  })

  it('keeps teaching management scoped to the selected course', async () => {
    requestMock.mockResolvedValue([])

    await courseApi.classes('42')
    await courseApi.invites('42')
    await courseApi.members('42')
    await courseApi.announcements('42', 2, 10)

    expect(requestMock).toHaveBeenNthCalledWith(1, { url: '/courses/42/classes' })
    expect(requestMock).toHaveBeenNthCalledWith(2, { url: '/courses/42/invites' })
    expect(requestMock).toHaveBeenNthCalledWith(3, { url: '/courses/42/members' })
    expect(requestMock).toHaveBeenNthCalledWith(4, {
      url: '/courses/42/announcements',
      params: { page: 2, size: 10 },
    })
  })

  it('uses the server DTO for an expiring student invite', async () => {
    requestMock.mockResolvedValueOnce({ id: '7' })
    const input = {
      classId: '5',
      memberRole: 'STUDENT' as const,
      maxUses: 60,
      expiresAt: '2026-10-01T00:00:00.000Z',
    }

    await courseApi.createInvite('42', input)

    expect(requestMock).toHaveBeenCalledWith({
      url: '/courses/42/invites',
      method: 'POST',
      data: input,
    })
  })

  it('updates and archives a course through the PATCH contract', async () => {
    requestMock.mockResolvedValueOnce({ id: '42', status: 'ARCHIVED' })

    await courseApi.update('42', { name: '软件工程实践', status: 'ARCHIVED' })

    expect(requestMock).toHaveBeenCalledWith({
      url: '/courses/42',
      method: 'PATCH',
      data: { name: '软件工程实践', status: 'ARCHIVED' },
    })
  })
})
