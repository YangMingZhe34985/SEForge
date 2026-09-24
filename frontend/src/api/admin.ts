import { apiRequest } from './client'
import type { AuditLogEntry, PageResult, User } from '@/types/domain'

export const adminApi = {
  users: () => apiRequest<PageResult<User>>({ url: '/admin/users', params: { page: 0, size: 100 } }),
  createUser: (input: { email: string; username: string; displayName: string; password: string; accountType: 'TEACHER' | 'STUDENT'; roles: Array<'ADMIN' | 'USER'> }) =>
    apiRequest<User>({ url: '/admin/users', method: 'POST', data: input }),
  setEnabled: (userId: string, enabled: boolean) =>
    apiRequest<User>({ url: `/admin/users/${userId}`, method: 'PATCH', data: { enabled } }),
  setRoles: (userId: string, roles: Array<'ADMIN' | 'USER'>) =>
    apiRequest<User>({ url: `/admin/users/${userId}/roles`, method: 'PUT', data: { roles } }),
  auditLogs: (page = 0, size = 20) =>
    apiRequest<PageResult<AuditLogEntry>>({ url: '/admin/audit-logs', params: { page, size } }),
}
