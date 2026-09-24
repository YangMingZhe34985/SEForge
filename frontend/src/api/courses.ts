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

export interface UpdateCourseInput {
  name?: string
  description?: string
  status?: 'ACTIVE' | 'ARCHIVED'
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
  list: (page = 0, size = 50, filters?: { search?: string; status?: 'ACTIVE' | 'ARCHIVED'; semesterId?: string }) =>
    apiRequest<PageResult<CourseSummary>>({ url: '/courses', params: { page, size, ...filters } }),
  get: (courseId: string) => apiRequest<CourseDetails>({ url: `/courses/${courseId}` }),
  create: (input: CreateCourseInput) => apiRequest<CourseDetails>({ url: '/courses', method: 'POST', data: input }),
  update: (courseId: string, input: UpdateCourseInput) =>
    apiRequest<CourseDetails>({ url: `/courses/${courseId}`, method: 'PATCH', data: input }),
  join: (inviteCode: string) => apiRequest<CourseDetails>({ url: '/courses/join', method: 'POST', data: { inviteCode } }),
  semesters: () => apiRequest<Semester[]>({ url: '/semesters' }),
  createSemester: (input: CreateSemesterInput) =>
    apiRequest<Semester>({ url: '/admin/semesters', method: 'POST', data: input }),
  updateSemester: (semesterId: string, input: Omit<CreateSemesterInput, 'code'>) =>
    apiRequest<Semester>({ url: `/admin/semesters/${semesterId}`, method: 'PUT', data: input }),
  classes: (courseId: string) => apiRequest<CourseClass[]>({ url: `/courses/${courseId}/classes` }),
  createClass: (courseId: string, input: CreateCourseClassInput) =>
    apiRequest<CourseClass>({ url: `/courses/${courseId}/classes`, method: 'POST', data: input }),
  updateClass: (courseId: string, classId: string, input: Omit<CourseClass, 'id' | 'code'>) =>
    apiRequest<CourseClass>({ url: `/courses/${courseId}/classes/${classId}`, method: 'PUT', data: input }),
  invites: (courseId: string) => apiRequest<CourseInvite[]>({ url: `/courses/${courseId}/invites` }),
  createInvite: (courseId: string, input: CreateInviteInput) =>
    apiRequest<CourseInvite>({ url: `/courses/${courseId}/invites`, method: 'POST', data: input }),
  revokeInvite: (courseId: string, inviteId: string) =>
    apiRequest<CourseInvite>({ url: `/courses/${courseId}/invites/${inviteId}`, method: 'DELETE' }),
  members: (courseId: string) => apiRequest<CourseMember[]>({ url: `/courses/${courseId}/members` }),
  removeMember: (courseId: string, userId: string) =>
    apiRequest<void>({ url: `/courses/${courseId}/members/${userId}`, method: 'DELETE' }),
  updateMember: (courseId: string, userId: string, input: { classId: string | null; role: 'STUDENT' | 'TA' }) =>
    apiRequest<CourseMember>({ url: `/courses/${courseId}/members/${userId}`, method: 'PUT', data: input }),
  announcements: (courseId: string, page = 0, size = 20) =>
    apiRequest<PageResult<CourseAnnouncement>>({
      url: `/courses/${courseId}/announcements`,
      params: { page, size },
    }),
  createAnnouncement: (courseId: string, input: { title: string; content: string }) =>
    apiRequest<CourseAnnouncement>({ url: `/courses/${courseId}/announcements`, method: 'POST', data: input }),
  updateAnnouncement: (courseId: string, announcementId: string, input: { title: string; content: string }) =>
    apiRequest<CourseAnnouncement>({ url: `/courses/${courseId}/announcements/${announcementId}`, method: 'PUT', data: input }),
  withdrawAnnouncement: (courseId: string, announcementId: string) =>
    apiRequest<void>({ url: `/courses/${courseId}/announcements/${announcementId}`, method: 'DELETE' }),
  resources: (courseId: string) => apiRequest<CourseResource[]>({ url: `/courses/${courseId}/resources` }),
  createResource: (courseId: string, input: CreateResourceInput) =>
    apiRequest<CourseResource>({ url: `/courses/${courseId}/resources`, method: 'POST', data: input }),
  uploadResource: (courseId: string, file: File, chapterId?: string) => {
    const data = new FormData()
    data.append('file', file)
    if (chapterId) data.append('chapterId', chapterId)
    return apiRequest<CourseResource>({ url: `/courses/${courseId}/resources/upload`, method: 'POST', data })
  },
  deleteResource: (courseId: string, resourceId: string) =>
    apiRequest<void>({ url: `/courses/${courseId}/resources/${resourceId}`, method: 'DELETE' }),
  async downloadResource(courseId: string, resource: CourseResource): Promise<void> {
    const response = await fetch(apiUrl(`/courses/${courseId}/resources/${resource.id}/download`), { credentials: 'include' })
    if (response.status === 401) window.dispatchEvent(new CustomEvent('seforge:unauthorized'))
    if (!response.ok) throw new Error(`下载失败 (${response.status})`)
    const objectUrl = URL.createObjectURL(await response.blob())
    const anchor = window.document.createElement('a')
    anchor.href = objectUrl
    anchor.download = resource.name
    anchor.click()
    URL.revokeObjectURL(objectUrl)
  },
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
  updateChapter: (courseId: string, chapterId: string, input: Pick<CourseChapter, 'title' | 'description' | 'sortOrder' | 'parentId'>) =>
    apiRequest<CourseChapter>({ url: `/courses/${courseId}/chapters/${chapterId}`, method: 'PUT', data: input }),
  deleteChapter: (courseId: string, chapterId: string) =>
    apiRequest<void>({ url: `/courses/${courseId}/chapters/${chapterId}`, method: 'DELETE' }),
  knowledgePoints: (courseId: string) => apiRequest<KnowledgePoint[]>({ url: `/courses/${courseId}/knowledge-points` }),
  createKnowledgePoint: (courseId: string, input: Pick<KnowledgePoint, 'title' | 'description' | 'chapterId' | 'sortOrder'>) =>
    apiRequest<KnowledgePoint>({ url: `/courses/${courseId}/knowledge-points`, method: 'POST', data: input }),
  updateKnowledgePoint: (courseId: string, pointId: string, input: Pick<KnowledgePoint, 'title' | 'description' | 'chapterId' | 'sortOrder'>) =>
    apiRequest<KnowledgePoint>({ url: `/courses/${courseId}/knowledge-points/${pointId}`, method: 'PUT', data: input }),
  deleteKnowledgePoint: (courseId: string, pointId: string) =>
    apiRequest<void>({ url: `/courses/${courseId}/knowledge-points/${pointId}`, method: 'DELETE' }),
}
