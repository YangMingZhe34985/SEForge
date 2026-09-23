import { apiRequest } from './client'
import type { AsyncJob } from '@/types/domain'

export const jobApi = {
  get: (jobId: string) => apiRequest<AsyncJob>({ url: `/jobs/${jobId}` }),
  cancel: (jobId: string) => apiRequest<AsyncJob>({ url: `/jobs/${jobId}`, method: 'DELETE' }),
}
