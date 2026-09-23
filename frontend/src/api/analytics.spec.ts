import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))

vi.mock('./client', () => ({ apiRequest: requestMock }))

import { analyticsApi, toDashboard } from './analytics'

const response = {
  courseId: '42',
  classId: '7',
  generatedAt: '2026-09-22T08:00:00Z',
  overview: {
    students: '25',
    assignments: '4',
    expectedSubmissions: '100',
    completedSubmissions: '80',
    completionRate: 80,
    averageFinalScore: 86.5,
  },
  gradeDistribution: [{ bucket: '80-89', count: '12' }],
  knowledgePoints: [{ title: '需求工程', scoreRate: 72.5, weak: true }],
  frequentQuestions: [{ excerpt: '如何写用例？', count: '6' }],
  tutor: { total: '14', failed: '1', byOperation: { HINT: '9', EXPLAIN: '5' } },
  qaFeedback: { helpful: '8', notHelpful: '2', helpfulRate: 80 },
  errors: { tutorFailures: '1', reviewFailures: '2' },
} as never

describe('analyticsApi contracts', () => {
  beforeEach(() => requestMock.mockReset())

  it('normalizes backend long values and exposes QA/Tutor metrics', () => {
    const dashboard = toDashboard(response)

    expect(dashboard.metrics).toEqual(expect.arrayContaining([
      { label: 'Tutor 使用', value: 14, unit: '次' },
      { label: '问答有用率', value: 80, unit: '%' },
    ]))
    expect(dashboard.tutorUsage).toEqual([
      { name: 'HINT', value: 9 },
      { name: 'EXPLAIN', value: 5 },
    ])
    expect(dashboard.qaFeedback).toEqual({ helpful: 8, notHelpful: 2, helpfulRate: 80 })
  })

  it('uses course and class scoped snapshot contracts', async () => {
    requestMock.mockResolvedValueOnce({ id: 'job-1' }).mockResolvedValueOnce({ items: [] })

    await analyticsApi.requestSnapshot('42', '7', 'request-1')
    await analyticsApi.snapshots('42', '7', 1, 10)

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/courses/42/analytics/snapshots',
      method: 'POST',
      params: { classId: '7', idempotencyKey: 'request-1' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/courses/42/analytics/snapshots',
      params: { classId: '7', page: 1, size: 10 },
    })
  })

  it('builds a chronological error trend from durable snapshots', () => {
    const trends = analyticsApi.snapshotTrends([
      { id: '2', courseId: '42', metricType: 'DASHBOARD_V1', periodEnd: '', generatedAt: '2026-09-22T08:00:00Z', payload: { errors: { tutorFailures: '2', reviewFailures: '1' } } },
      { id: '1', courseId: '42', metricType: 'DASHBOARD_V1', periodEnd: '', generatedAt: '2026-09-21T08:00:00Z', payload: { errors: { tutorFailures: '1', reviewFailures: '0' } } },
    ])

    expect(trends).toEqual([
      { date: '2026-09-21T08:00:00Z', value: 1 },
      { date: '2026-09-22T08:00:00Z', value: 3 },
    ])
  })

  it('updates platform roles through the administrator contract', async () => {
    requestMock.mockResolvedValueOnce({ id: 'user-1' })

    await (await import('./analytics')).adminApi.setRoles('user-1', ['USER', 'ADMIN'])

    expect(requestMock).toHaveBeenCalledWith({
      url: '/admin/users/user-1/roles',
      method: 'PUT',
      data: { roles: ['USER', 'ADMIN'] },
    })
  })
})
