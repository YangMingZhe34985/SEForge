import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))

vi.mock('./client', () => ({
  apiRequest: requestMock,
  apiUrl: (path: string) => `/api/v1${path}`,
}))

import { reviewApi } from './reviews'
import type { ReviewJob } from '@/types/domain'

const codeJob = {
  id: '91',
  courseId: '42',
  submissionId: '77',
  type: 'CODE',
  status: 'QUEUED',
  createdAt: '2026-09-22T08:00:00Z',
}

describe('reviewApi contracts', () => {
  beforeEach(() => requestMock.mockReset())

  it('queues server-side Sonar analysis without accepting client findings', async () => {
    requestMock.mockResolvedValueOnce(codeJob)

    await reviewApi.createCode('42', '77', 'courses/42/submissions/77/source.zip')

    const config = requestMock.mock.calls[0][0]
    expect(config).toMatchObject({
      url: '/courses/42/reviews/code',
      method: 'POST',
      data: {
        submissionId: '77',
        attachmentObjectKey: 'courses/42/submissions/77/source.zip',
        idempotencyKey: expect.any(String),
      },
    })
    expect(config.data).not.toHaveProperty('sonarFindings')
  })

  it('maps nested code explanations into visible recommendations', async () => {
    requestMock.mockResolvedValueOnce({
      id: 'report-1',
      reviewJobId: '91',
      summary: 'Static analysis completed',
      result: {
        analysis: {
          explanations: [{
            findingKey: 'finding-1',
            explanation: 'Null may be dereferenced',
            impact: 'Runtime failure',
            remediation: 'Guard the value',
          }],
        },
      },
      generatedAt: '2026-09-22T08:01:00Z',
    })

    const report = await reviewApi.report('42', { ...codeJob, subjectName: 'code' } as ReviewJob)

    expect(report.suggestions).toContain('finding-1：Null may be dereferenced；影响：Runtime failure；修复：Guard the value')
  })

  it('uses the course-scoped retry endpoint', async () => {
    requestMock.mockResolvedValueOnce({ ...codeJob, status: 'QUEUED' })

    await reviewApi.retry('42', '91')

    expect(requestMock).toHaveBeenCalledWith({
      url: '/courses/42/reviews/91/retry',
      method: 'POST',
    })
  })

  it('keeps authoritative Sonar findings separate from AI explanation and audit metadata', async () => {
    requestMock.mockResolvedValueOnce({ id: 'r', reviewJobId: '91', summary: 'review', model: 'coder', promptVersion: 'code-review/v2', aiTraceId: '7', generatedAt: '', result: {
      sonar: { qualityGate: 'ERROR', projectKey: 'project', analysisId: 'analysis' },
      findings: [{ findingKey: 'finding-1', rule: 'python:S1764', type: 'BUG', severity: 'MAJOR', component: 'bad.py', line: 2, message: 'Identical operands' }],
      analysis: { explanations: [{ findingKey: 'finding-1', explanation: 'AI explanation', impact: 'impact', remediation: 'fix' }] },
    } })
    const report = await reviewApi.report('42', { ...codeJob, subjectName: 'code' } as ReviewJob)
    expect(report.findings).toEqual([expect.objectContaining({ message: 'Identical operands', explanation: 'AI explanation', rule: 'python:S1764' })])
    expect(report.sonar?.qualityGate).toBe('ERROR')
    expect(report.aiTraceId).toBe('7')
  })

  it('delegates type filtering and pagination to the server', async () => {
    requestMock.mockResolvedValueOnce({ items: [codeJob], page: 3, size: 20, total: 81 })

    const result = await reviewApi.list('42', 'CODE', 3, 20)

    expect(requestMock).toHaveBeenCalledWith({
      url: '/courses/42/reviews',
      params: { type: 'CODE', page: 3, size: 20 },
    })
    expect(result).toMatchObject({ page: 3, size: 20, total: 81 })
    expect(result.items).toHaveLength(1)
  })
})
