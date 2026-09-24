import { apiRequest } from './client'
import type { AuditLogEntry, CourseDetails, PageResult, Semester, User } from '@/types/domain'

export interface AdminOverview {
  teachers: number
  students: number
  activeCourses: number
  archivedCourses: number
  currentSemester: Semester | null
}

export interface AdminUserMembership {
  courseId: string
  courseName: string
  classId: string | null
  role: 'TEACHER' | 'TA' | 'STUDENT'
}

export interface UserImportRow {
  line: number
  accountType: string
  studentNo: string
  username: string
  email: string
  displayName: string
  status: 'VALID' | 'REJECTED' | 'CREATED' | 'SKIPPED' | 'FAILED'
  message: string
  userId: string | null
  initialPassword: string | null
}

export interface UserImportPreview {
  digest: string
  total: number
  valid: number
  rejected: number
  rows: UserImportRow[]
}

export interface UserImportResult {
  total: number
  created: number
  skipped: number
  failed: number
  rows: UserImportRow[]
}

export const adminApi = {
  users: (page = 0, size = 20, accountType?: 'TEACHER' | 'STUDENT', search?: string) =>
    apiRequest<PageResult<User>>({ url: '/admin/users', params: { page, size, accountType, search } }),
  user: (userId: string) => apiRequest<User>({ url: `/admin/users/${userId}` }),
  memberships: (userId: string) => apiRequest<AdminUserMembership[]>({ url: `/admin/users/${userId}/memberships` }),
  createUser: (input: { email: string; username: string; displayName: string; password: string; accountType: 'TEACHER' | 'STUDENT'; roles: Array<'ADMIN' | 'USER'>; studentNo?: string }) =>
    apiRequest<User>({ url: '/admin/users', method: 'POST', data: input }),
  setEnabled: (userId: string, enabled: boolean) =>
    apiRequest<User>({ url: `/admin/users/${userId}`, method: 'PATCH', data: { enabled } }),
  setRoles: (userId: string, roles: Array<'ADMIN' | 'USER'>) =>
    apiRequest<User>({ url: `/admin/users/${userId}/roles`, method: 'PUT', data: { roles } }),
  updateProfile: (userId: string, input: { displayName: string; studentNo?: string }) =>
    apiRequest<User>({ url: `/admin/users/${userId}/profile`, method: 'PUT', data: input }),
  issuePasswordReset: (userId: string) =>
    apiRequest<{ token: string; expiresAt: string }>({ url: `/admin/users/${userId}/password-reset`, method: 'POST' }),
  previewImport: (file: File) => {
    const data = new FormData()
    data.append('file', file)
    return apiRequest<UserImportPreview>({ url: '/admin/users/import/preview', method: 'POST', data })
  },
  confirmImport: (file: File, digest: string) => {
    const data = new FormData()
    data.append('file', file)
    return apiRequest<UserImportResult>({ url: '/admin/users/import/confirm', method: 'POST', params: { digest }, data })
  },
  overview: () => apiRequest<AdminOverview>({ url: '/admin/overview' }),
  updateCourse: (courseId: string, input: { name?: string; description?: string; status?: 'ACTIVE' | 'ARCHIVED' }) =>
    apiRequest<CourseDetails>({ url: `/admin/courses/${courseId}`, method: 'PATCH', data: input }),
  transferOwner: (courseId: string, userId: string) =>
    apiRequest<CourseDetails>({ url: `/admin/courses/${courseId}/owner`, method: 'PUT', data: { userId } }),
  auditLogs: (page = 0, size = 20, filters?: { action?: string; outcome?: string; actorId?: string; from?: string; to?: string }) =>
    apiRequest<PageResult<AuditLogEntry>>({ url: '/admin/audit-logs', params: { page, size, ...filters } }),
}
