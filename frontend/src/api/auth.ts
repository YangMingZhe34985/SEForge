import { apiRequest, clearCsrf, refreshCsrf } from './client'
import type { User } from '@/types/domain'

export interface RegisterInput {
  email: string
  username: string
  displayName: string
  password: string
  studentNo: string
}

export type LoginPortal = 'STUDENT' | 'TEACHER' | 'ADMIN'

export const authApi = {
  csrf: refreshCsrf,
  me: () => apiRequest<User>({ url: '/auth/me' }),
  register: (input: RegisterInput) => apiRequest<User>({ url: '/auth/register', method: 'POST', data: input }),
  login: (identifier: string, password: string, portal: LoginPortal) =>
    apiRequest<{ user: User }>({ url: '/auth/login', method: 'POST', data: { identifier, password, portal } }),
  claimStudentNo: (identifier: string, password: string, studentNo: string) =>
    apiRequest<User>({ url: '/auth/claim-student-no', method: 'POST', data: { identifier, password, studentNo } }),
  resetPassword: (token: string, password: string) =>
    apiRequest<void>({ url: '/auth/reset-password', method: 'POST', data: { token, password } }),
  async logout(): Promise<void> {
    await apiRequest<void>({ url: '/auth/logout', method: 'POST' })
    clearCsrf()
  },
}
