import { apiRequest, apiUrl } from './client'
import type {
  CourseAnnouncement,
  CourseChapter,
  CourseClass,
  CourseDetails,
  CourseInvite,
  CourseMember,
  CourseRole,
  CourseResource,
  CourseSummary,
  KnowledgePoint,
  KnowledgeDocument,
  DocumentUploadResult,
  AsyncJob,
  PageResult,
  Semester,
  SemesterStatus,
} from '@/types/domain'

export interface CreateCourseInput {
  code: string
  name: string
  description?: string
  semesterId: string
}

export interface CreateSemesterInput {
  code: string
  name: string
  startsOn: string
  endsOn: string
  status: SemesterStatus
}

export interface CreateCourseClassInput {
  code: string
  name: string
  capacity?: number
  primaryClass: boolean
}

export interface CreateInviteInput {
  classId?: string
  memberRole?: Exclude<CourseRole, 'TEACHER'>
  maxUses?: number
  expiresAt?: string
}

export interface CreateResourceInput {
  name: string
  description?: string
  resourceType: string
  objectKey: string
  contentType?: string
  sizeBytes?: number
  chapterId?: string
}

export const courseApi = {
  list: (page = 0, size = 50) => apiRequest<PageResult<CourseSummary>>({ url: '/courses', params: { page, size } }),
  get: (courseId: string) => apiRequest<CourseDetails>({ url: `/courses/${courseId}` }),
  create: (input: CreateCourseInput) => apiRequest<CourseDetails>({ url: '/courses', method: 'POST', data: input }),
  join: (inviteCode: string) => apiRequest<CourseDetails>({ url: '/courses/join', method: 'POST', data: { inviteCode } }),
  semesters: () => apiRequest<Semester[]>({ url: '/semesters' }),
  createSemester: (input: CreateSemesterInput) =>
    apiRequest<Semester>({ url: '/admin/semesters', method: 'POST', data: input }),
  classes: (courseId: string) => apiRequest<CourseClass[]>({ url: `/courses/${courseId}/classes` }),
  createClass: (courseId: string, input: CreateCourseClassInput) =>
    apiRequest<CourseClass>({ url: `/courses/${courseId}/classes`, method: 'POST', data: input }),
  invites: (courseId: string) => apiRequest<CourseInvite[]>({ url: `/courses/${courseId}/invites` }),
  createInvite: (courseId: string, input: CreateInviteInput) =>
    apiRequest<CourseInvite>({ url: `/courses/${courseId}/invites`, method: 'POST', data: input }),
  members: (courseId: string) => apiRequest<CourseMember[]>({ url: `/courses/${courseId}/members` }),
  removeMember: (courseId: string, userId: string) =>
    apiRequest<void>({ url: `/courses/${courseId}/members/${userId}`, method: 'DELETE' }),
  announcements: (courseId: string, page = 0, size = 20) =>
    apiRequest<PageResult<CourseAnnouncement>>({
      url: `/courses/${courseId}/announcements`,
      params: { page, size },
    }),
  createAnnouncement: (courseId: string, input: { title: string; content: string }) =>
    apiRequest<CourseAnnouncement>({ url: `/courses/${courseId}/announcements`, method: 'POST', data: input }),
  resources: (courseId: string) => apiRequest<CourseResource[]>({ url: `/courses/${courseId}/resources` }),
  createResource: (courseId: string, input: CreateResourceInput) =>
    apiRequest<CourseResource>({ url: `/courses/${courseId}/resources`, method: 'POST', data: input }),
  documents: (courseId: string) =>
    apiRequest<KnowledgeDocument[]>({ url: `/courses/${courseId}/knowledge/documents` }),
  uploadDocument: (courseId: string, file: File, chapterId?: string) => {
    const data = new FormData()
    data.append('file', file)
    if (chapterId) data.append('chapterId', chapterId)
    return apiRequest<DocumentUploadResult>({ url: `/courses/${courseId}/knowledge/documents`, method: 'POST', data })
  },
  reindexDocument: (courseId: string, documentId: string) =>
    apiRequest<AsyncJob>({ url: `/courses/${courseId}/knowledge/documents/${documentId}/reindex`, method: 'POST' }),
  deleteDocument: (courseId: string, documentId: string) =>
    apiRequest<void>({ url: `/courses/${courseId}/knowledge/documents/${documentId}`, method: 'DELETE' }),
  async downloadDocument(courseId: string, document: KnowledgeDocument): Promise<void> {
    const response = await fetch(apiUrl(`/courses/${courseId}/knowledge/documents/${document.id}/download`), { credentials: 'include' })
    if (response.status === 401) window.dispatchEvent(new CustomEvent('seforge:unauthorized'))
    if (!response.ok) {
      let message = `下载失败 (${response.status})`
      try {
        const error = await response.json() as { message?: string }
        message = error.message || message
      } catch {
        // Proxy responses may not be JSON.
      }
      throw new Error(message)
    }
    const objectUrl = URL.createObjectURL(await response.blob())
    const anchor = window.document.createElement('a')
    anchor.href = objectUrl
    anchor.download = document.name
    anchor.click()
    URL.revokeObjectURL(objectUrl)
  },
  chapters: (courseId: string) => apiRequest<CourseChapter[]>({ url: `/courses/${courseId}/chapters` }),
  createChapter: (courseId: string, input: Pick<CourseChapter, 'title' | 'description' | 'sortOrder' | 'parentId'>) =>
    apiRequest<CourseChapter>({ url: `/courses/${courseId}/chapters`, method: 'POST', data: input }),
  knowledgePoints: (courseId: string) => apiRequest<KnowledgePoint[]>({ url: `/courses/${courseId}/knowledge-points` }),
  createKnowledgePoint: (courseId: string, input: Pick<KnowledgePoint, 'title' | 'description' | 'chapterId' | 'sortOrder'>) =>
    apiRequest<KnowledgePoint>({ url: `/courses/${courseId}/knowledge-points`, method: 'POST', data: input }),
}
