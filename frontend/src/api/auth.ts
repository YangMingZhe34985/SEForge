import { apiRequest, clearCsrf, refreshCsrf } from './client'
import type { User } from '@/types/domain'

export interface RegisterInput {
  email: string
  username: string
  displayName: string
  password: string
}

export const authApi = {
  csrf: refreshCsrf,
  me: () => apiRequest<User>({ url: '/auth/me' }),
  register: (input: RegisterInput) => apiRequest<User>({ url: '/auth/register', method: 'POST', data: input }),
  login: (identifier: string, password: string) =>
    apiRequest<{ user: User }>({ url: '/auth/login', method: 'POST', data: { identifier, password } }),
  async logout(): Promise<void> {
    await apiRequest<void>({ url: '/auth/logout', method: 'POST' })
    clearCsrf()
  },
}
