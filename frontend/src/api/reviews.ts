import { apiRequest, apiUrl } from './client'
import type { GradeRecord, PageResult, ReviewJob, ReviewReport, ReviewType, RubricEvaluation } from '@/types/domain'

export interface GradeRubricConfirmation {
  rubricItemId: string
  score: number
  feedback?: string
}

interface ReviewJobResponse {
  id: string
  asyncJobId?: string
  courseId: string
  assignmentId?: string
  submissionId?: string
  documentId?: string
  resourceId?: string
  type: ReviewType
  status: ReviewJob['status']
  errorMessage?: string
  createdAt: string
  updatedAt?: string
}

interface ReviewReportResponse {
  id: string
  reviewJobId: string
  summary: string
  result: Record<string, unknown>
  generatedAt: string
}

function toJob(value: ReviewJobResponse): ReviewJob {
  const target = value.type === 'DOCUMENT'
    ? `文档 #${value.documentId || value.resourceId || value.id}`
    : value.type === 'CODE'
      ? `代码提交 #${value.submissionId || value.id}`
      : `作业提交 #${value.submissionId || value.id}`
  return { ...value, subjectName: target }
}

function stringList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

function record(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function dimensionFindings(result: Record<string, unknown>, keyword: string): string[] {
  const dimensions = Array.isArray(result.dimensions) ? result.dimensions : []
  const dimension = dimensions.find((item) => {
    if (!item || typeof item !== 'object') return false
    return String((item as Record<string, unknown>).dimension || '').toUpperCase().includes(keyword)
  }) as Record<string, unknown> | undefined
  return stringList(dimension?.findings)
}

function rubricItems(result: Record<string, unknown>): RubricEvaluation[] {
  if (!Array.isArray(result.rubricItems)) return []
  return result.rubricItems.flatMap((raw) => {
    if (!raw || typeof raw !== 'object') return []
    const item = raw as Record<string, unknown>
    return [{
      rubricItemId: String(item.rubricItemId ?? ''),
      title: `Rubric #${String(item.rubricItemId ?? '')}`,
      suggestedScore: Number(item.suggestedScore ?? 0),
      evidence: stringList(item.evidence).join('；'),
      feedback: typeof item.feedback === 'string' ? item.feedback : stringList(item.issues).join('；'),
    }]
  })
}

function toReport(value: ReviewReportResponse, type: ReviewType): ReviewReport {
  const dimensions = Array.isArray(value.result.dimensions) ? value.result.dimensions : []
  const dimensionSuggestions = dimensions.flatMap((raw) => {
    if (!raw || typeof raw !== 'object') return []
    return stringList((raw as Record<string, unknown>).suggestions)
  })
  const analysis = record(value.result.analysis)
  const codeSuggestions = Array.isArray(analysis.explanations)
    ? analysis.explanations.flatMap((raw) => {
        const item = record(raw)
        if (!item.findingKey) return []
        return [`${String(item.findingKey)}：${String(item.explanation || '')}；影响：${String(item.impact || '')}；修复：${String(item.remediation || '')}`]
      })
    : []
  return {
    id: value.id,
    jobId: value.reviewJobId,
    type,
    summary: value.summary,
    scoreSuggestion: typeof value.result.totalSuggestedScore === 'number' ? value.result.totalSuggestedScore : undefined,
    completeness: dimensionFindings(value.result, 'COMPLETE'),
    consistency: dimensionFindings(value.result, 'CONSIST'),
    testability: dimensionFindings(value.result, 'VERIFI'),
    clarity: dimensionFindings(value.result, 'CLAR'),
    suggestions: [...stringList(value.result.recommendations), ...dimensionSuggestions, ...codeSuggestions],
    rubricItems: rubricItems(value.result),
    createdAt: value.generatedAt,
  }
}

export const reviewApi = {
  async list(courseId: string, type?: ReviewType, page = 0, size = 20): Promise<PageResult<ReviewJob>> {
    const result = await apiRequest<PageResult<ReviewJobResponse>>({
      url: `/courses/${courseId}/reviews`,
      params: { type, page, size },
    })
    return { ...result, items: result.items.map(toJob) }
  },
  report: (courseId: string, job: ReviewJob) =>
    apiRequest<ReviewReportResponse>({ url: `/courses/${courseId}/reviews/${job.id}/report` })
      .then((value) => toReport(value, job.type)),
  createDocument: (courseId: string, documentId: string, documentKind: string) =>
    apiRequest<ReviewJobResponse>({
      url: `/courses/${courseId}/reviews/documents`,
      method: 'POST',
      data: { documentId, documentKind, idempotencyKey: crypto.randomUUID() },
    }).then(toJob),
  createCode: (courseId: string, submissionId: string, attachmentObjectKey: string) =>
    apiRequest<ReviewJobResponse>({
      url: `/courses/${courseId}/reviews/code`,
      method: 'POST',
      data: { submissionId, attachmentObjectKey, idempotencyKey: crypto.randomUUID() },
    }).then(toJob),
  createAssignment: (courseId: string, submissionId: string) =>
    apiRequest<ReviewJobResponse>({
      url: `/courses/${courseId}/reviews/assignments`,
      method: 'POST',
      data: { submissionId, idempotencyKey: crypto.randomUUID() },
    }).then(toJob),
  confirmGrade: (submissionId: string, score: number, feedback: string, reason?: string, rubricItems: GradeRubricConfirmation[] = []) =>
    apiRequest<GradeRecord>({
      url: `/submissions/${submissionId}/grade/confirm`,
      method: 'POST',
      data: { score, feedback, reason, rubricItems },
    }),
  grades: (courseId?: string) =>
    apiRequest<PageResult<GradeRecord>>({ url: '/grades', params: { courseId, page: 0, size: 100 } }),
  retry: (courseId: string, reviewJobId: string) =>
    apiRequest<ReviewJobResponse>({
      url: `/courses/${courseId}/reviews/${reviewJobId}/retry`,
      method: 'POST',
    }).then(toJob),
  async exportReport(courseId: string, reportId: string): Promise<void> {
    const response = await fetch(apiUrl(`/courses/${courseId}/reviews/reports/${reportId}/export`), {
      credentials: 'include',
    })
    if (response.status === 401) window.dispatchEvent(new CustomEvent('seforge:unauthorized'))
    if (!response.ok) {
      let message = `报告导出失败 (${response.status})`
      try {
        const error = await response.json() as { message?: string }
        message = error.message || message
      } catch {
        // Reverse-proxy failures may not return JSON.
      }
      throw new Error(message)
    }
    const objectUrl = URL.createObjectURL(await response.blob())
    const anchor = window.document.createElement('a')
    anchor.href = objectUrl
    anchor.download = `seforge-review-${reportId}.json`
    anchor.click()
    URL.revokeObjectURL(objectUrl)
  },
}
