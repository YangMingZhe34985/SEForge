import { apiRequest } from './client'
import type { CourseResource, DocumentUploadResult } from '@/types/domain'
export interface PointSource { id: string; documentId: string; resourceId?: string; chapterId: string; name: string; page?: number; section?: string; quote: string }
export interface PointProposal { name: string; description: string; importance: 'CORE' | 'IMPORTANT' | 'OPTIONAL'; sourceIds: string[]; manual: boolean }
export interface PointDraft { id: string; sources: PointSource[]; points: PointProposal[]; coverage?: { documents: number; totalChunks: number; selectedChunks: number; sampled: boolean } }
export const teachingContentApi = {
  include: (course: string, resource: string) => apiRequest<DocumentUploadResult>({ url: `/courses/${course}/resources/${resource}/knowledge`, method: 'POST' }),
  update: (course: string, resource: string, name: string, description?: string) => apiRequest<CourseResource>({ url: `/courses/${course}/resources/${resource}`, method: 'PUT', data: { name, description } }),
  link: (course: string, input: { name: string; url: string; description?: string; chapterId?: string }) => apiRequest<CourseResource>({ url: `/courses/${course}/resources/links`, method: 'POST', data: input }),
  generate: (course: string, chapter: string) => apiRequest<PointDraft>({ url: `/courses/${course}/chapters/${chapter}/knowledge-point-drafts`, method: 'POST', timeout: 630000 }),
  confirm: (course: string, chapter: string, draft: string, points: PointProposal[]) => apiRequest<string[]>({ url: `/courses/${course}/chapters/${chapter}/knowledge-point-drafts/${draft}/confirm`, method: 'POST', data: { points } }),
}
