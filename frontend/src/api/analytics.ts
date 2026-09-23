import { apiRequest } from './client'
import type { AnalyticsSnapshot, AsyncJob, CourseDashboard, PageResult, User } from '@/types/domain'

interface DashboardResponse {
  courseId: string
  classId?: string
  generatedAt: string
  overview: {
    students: number
    assignments: number
    expectedSubmissions: number
    completedSubmissions: number
    completionRate: number
    averageFinalScore: number
  }
  gradeDistribution: Array<{ bucket: string; count: number }>
  knowledgePoints: Array<{ title: string; scoreRate: number; weak: boolean }>
  frequentQuestions: Array<{ excerpt: string; count: number }>
  tutor: { total: number; failed: number; byOperation: Record<string, number> }
  qaFeedback: { helpful: number; notHelpful: number; helpfulRate: number }
  errors: { tutorFailures: number; reviewFailures: number }
}

function numeric(value: number | string | null | undefined): number {
  const result = Number(value ?? 0)
  return Number.isFinite(result) ? result : 0
}

export function toDashboard(value: DashboardResponse): CourseDashboard {
  return {
    courseId: value.courseId,
    classId: value.classId,
    generatedAt: value.generatedAt,
    metrics: [
      { label: '学生数', value: numeric(value.overview.students), unit: '人' },
      { label: '作业数', value: numeric(value.overview.assignments), unit: '份' },
      { label: '完成率', value: numeric(value.overview.completionRate), unit: '%' },
      { label: '平均分', value: numeric(value.overview.averageFinalScore), unit: '分' },
      { label: 'Tutor 使用', value: numeric(value.tutor.total), unit: '次' },
      { label: '问答有用率', value: numeric(value.qaFeedback.helpfulRate), unit: '%' },
    ],
    gradeDistribution: value.gradeDistribution.map((item) => ({ name: item.bucket, value: numeric(item.count) })),
    knowledgePointAccuracy: value.knowledgePoints.map((item) => ({ name: item.title, value: numeric(item.scoreRate) })),
    frequentQuestions: value.frequentQuestions.map((item) => ({ name: item.excerpt, value: numeric(item.count) })),
    weakKnowledgePoints: value.knowledgePoints.filter((item) => item.weak).map((item) => ({ name: item.title, value: numeric(item.scoreRate) })),
    tutorUsage: Object.entries(value.tutor.byOperation || {}).map(([name, count]) => ({ name, value: numeric(count) })),
    qaFeedback: {
      helpful: numeric(value.qaFeedback.helpful),
      notHelpful: numeric(value.qaFeedback.notHelpful),
      helpfulRate: numeric(value.qaFeedback.helpfulRate),
    },
    errorTrends: [{
      date: value.generatedAt,
      value: numeric(value.errors.tutorFailures) + numeric(value.errors.reviewFailures),
    }],
  }
}

function snapshotTrends(items: AnalyticsSnapshot[]): Array<{ date: string; value: number }> {
  return [...items]
    .filter((item) => item.metricType === 'DASHBOARD_V1')
    .sort((left, right) => left.generatedAt.localeCompare(right.generatedAt))
    .map((item) => {
      const payload = item.payload as Partial<DashboardResponse>
      return {
        date: item.generatedAt,
        value: numeric(payload.errors?.tutorFailures) + numeric(payload.errors?.reviewFailures),
      }
    })
}

export const analyticsApi = {
  dashboard: (courseId: string, classId?: string) =>
    apiRequest<DashboardResponse>({ url: `/courses/${courseId}/analytics/dashboard`, params: { classId } }).then(toDashboard),
  requestSnapshot: (courseId: string, classId?: string, idempotencyKey?: string) =>
    apiRequest<AsyncJob>({
      url: `/courses/${courseId}/analytics/snapshots`,
      method: 'POST',
      params: { classId, idempotencyKey },
    }),
  snapshots: (courseId: string, classId?: string, page = 0, size = 30) =>
    apiRequest<PageResult<AnalyticsSnapshot>>({
      url: `/courses/${courseId}/analytics/snapshots`,
      params: { classId, page, size },
    }),
  snapshotTrends,
  job: (jobId: string) => apiRequest<AsyncJob>({ url: `/jobs/${jobId}` }),
}

export const adminApi = {
  users: () => apiRequest<PageResult<User>>({ url: '/admin/users', params: { page: 0, size: 100 } }),
  createUser: (input: { email: string; username: string; displayName: string; password: string; accountType: 'TEACHER' | 'STUDENT'; roles: Array<'ADMIN' | 'USER'> }) =>
    apiRequest<User>({ url: '/admin/users', method: 'POST', data: input }),
  setEnabled: (userId: string, enabled: boolean) =>
    apiRequest<User>({ url: `/admin/users/${userId}`, method: 'PATCH', data: { enabled } }),
  setRoles: (userId: string, roles: Array<'ADMIN' | 'USER'>) =>
    apiRequest<User>({ url: `/admin/users/${userId}/roles`, method: 'PUT', data: { roles } }),
}
