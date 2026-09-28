import { apiRequest, apiBlob } from './client'
import type {
  AssignmentDetails,
  AssignmentQuestion,
  AssignmentQuestionInput,
  AssignmentRubric,
  AssignmentSummary,
  PageResult,
  Submission,
  SubmissionAttachment,
  SubmissionAnswerInput,
  TeacherSubmission,
  TutorPolicyConfig,
  TutorAction,
  TutorResponse,
} from '@/types/domain'

export const assignmentApi = {
  extractQuestions: (assignmentId: string, sourceFile: string | string[], reparse = false, reparseFile?: string) => apiRequest<QuestionImport>({ url: `/assignments/${assignmentId}/question-imports`, method: 'POST', params: { sourceFile: Array.isArray(sourceFile) ? sourceFile.join(',') : sourceFile, reparse, reparseFile }, timeout: 630000 }),
  confirmImport: (assignmentId: string, id: string, questions: { draftKey: string; question: AssignmentQuestionInput }[]) => apiRequest<AssignmentQuestion[]>({ url: `/assignments/${assignmentId}/question-imports/${id}/confirm`, method: 'POST', data: { questions } }),
  referenceMarkdown: (assignmentId: string, sourceFile: string) => apiRequest<{ markdown: string; warning: string }>({ url: `/assignments/${assignmentId}/reference-markdown-drafts`, method: 'POST', params: { sourceFile }, timeout: 630000 }),
  media: (assignmentId: string, id: string) => apiRequest<import('@/types/domain').AssignmentMedia>({ url: `/assignments/${assignmentId}/media/${id}` }),
  mediaBlob: (assignmentId: string, id: string) => apiBlob(`/assignments/${assignmentId}/media/${id}/download`),
  uploadMedia: (assignmentId: string, purpose: 'QUESTION_CONTENT' | 'REFERENCE_ANSWER' | 'CONTENT' | 'REFERENCE' | 'ANSWER' | 'IMPORT', file: File, questionId?: string, expectedAttempt?: number, startNextAttempt = false) => {
    const data = new FormData()
    data.append('purpose', purpose); data.append('file', file)
    if (questionId) data.append('questionId', questionId)
    if (expectedAttempt !== undefined) data.append('expectedAttempt', String(expectedAttempt))
    data.append('startNextAttempt', String(startNextAttempt))
    return apiRequest<import('@/types/domain').AssignmentMedia>({ url: `/assignments/${assignmentId}/media`, method: 'POST', data })
  },
  list: (courseId: string) =>
    apiRequest<PageResult<AssignmentSummary>>({ url: `/courses/${courseId}/assignments`, params: { page: 0, size: 100 } }),
  get: (assignmentId: string) => apiRequest<AssignmentDetails>({ url: `/assignments/${assignmentId}` }),
  create: (courseId: string, input: Partial<AssignmentDetails>) =>
    apiRequest<AssignmentSummary>({ url: `/courses/${courseId}/assignments`, method: 'POST', data: input }),
  update: (assignmentId: string, input: Partial<AssignmentDetails>) =>
    apiRequest<AssignmentSummary>({ url: `/assignments/${assignmentId}`, method: 'PATCH', data: input }),
  transition: (assignmentId: string, status: AssignmentSummary['status']) =>
    apiRequest<AssignmentDetails>({ url: `/assignments/${assignmentId}/transition`, method: 'POST', data: { status } }),
  addQuestion: (assignmentId: string, input: AssignmentQuestionInput) =>
    apiRequest<AssignmentQuestion>({ url: `/assignments/${assignmentId}/questions`, method: 'POST', data: input }),
  updateQuestion: (assignmentId: string, questionId: string, input: AssignmentQuestionInput) =>
    apiRequest<AssignmentQuestion>({ url: `/assignments/${assignmentId}/questions/${questionId}`, method: 'PUT', data: input }),
  deleteQuestion: (assignmentId: string, questionId: string) =>
    apiRequest<void>({ url: `/assignments/${assignmentId}/questions/${questionId}`, method: 'DELETE' }),
  updateTutorPolicy: (assignmentId: string, policy: TutorPolicyConfig) =>
    apiRequest<TutorPolicyConfig>({ url: `/assignments/${assignmentId}/tutor-policy`, method: 'PUT', data: policy }),
  setExtension: (assignmentId: string, studentId: string, dueAt?: string) =>
    apiRequest<TutorPolicyConfig>({
      url: `/assignments/${assignmentId}/extensions/${studentId}`,
      method: 'PUT',
      data: { dueAt: dueAt || null },
    }),
  rubric: (assignmentId: string) => apiRequest<AssignmentRubric | null>({ url: `/assignments/${assignmentId}/rubric` }),
  saveRubric: (assignmentId: string, input: Pick<AssignmentRubric, 'title' | 'totalScore' | 'status'>) =>
    apiRequest<AssignmentRubric>({ url: `/assignments/${assignmentId}/rubric`, method: 'PUT', data: input }),
  addRubricItem: (assignmentId: string, input: Omit<AssignmentRubric['items'][number], 'id'>) =>
    apiRequest<AssignmentRubric['items'][number]>({ url: `/assignments/${assignmentId}/rubric/items`, method: 'POST', data: input }),
  deleteRubricItem: (assignmentId: string, itemId: string) =>
    apiRequest<void>({ url: `/assignments/${assignmentId}/rubric/items/${itemId}`, method: 'DELETE' }),
  submissions: (assignmentId: string) =>
    apiRequest<TeacherSubmission[]>({ url: `/assignments/${assignmentId}/submissions` }),
  mySubmission: (assignmentId: string) =>
    apiRequest<Submission | null>({ url: `/assignments/${assignmentId}/submissions/me` }),
  saveDraft: (assignmentId: string, answers: SubmissionAnswerInput[], expectedAttempt?: number, startNextAttempt = false) =>
    apiRequest<Submission>({ url: `/assignments/${assignmentId}/submissions/draft`, method: 'PUT',
      data: { answers, expectedAttempt, startNextAttempt } }),
  submit: (assignmentId: string, answers: SubmissionAnswerInput[], submissionKey?: string, expectedAttempt?: number) =>
    apiRequest<Submission>({ url: `/assignments/${assignmentId}/submissions`, method: 'POST', data: { answers, submissionKey, expectedAttempt } }),
  uploadAttachment: (assignmentId: string, questionId: string, file: File,
    expectedAttempt?: number, startNextAttempt = false) => {
    const data = new FormData()
    data.append('questionId', questionId)
    data.append('file', file)
    if (expectedAttempt !== undefined) data.append('expectedAttempt', String(expectedAttempt))
    data.append('startNextAttempt', String(startNextAttempt))
    return apiRequest<SubmissionAttachment>({
      url: `/assignments/${assignmentId}/submissions/attachments`,
      method: 'POST',
      data,
    })
  },
  tutor: (assignmentId: string, questionId: string, action: TutorAction, draftAnswer: SubmissionAnswerInput['answer'], requestKey: string = crypto.randomUUID()) =>
    apiRequest<TutorResponse>({
      url: `/assignments/${assignmentId}/tutor`,
      method: 'POST',
      // Tutor may require several model/tool round trips. Never inherit the 30s CRUD deadline.
      timeout: 630_000,
      headers: { 'X-Trace-Id': requestKey },
      data: { questionId, action, draftAnswer, requestKey },
    }),
}

export interface QuestionImport {
  id: string; sourceFile: string; sourceName: string; confirmed: boolean
  sources?: { id: string; name: string }[]
  questions: { draftKey: string; type: import('@/types/domain').QuestionType | null; contentMarkdown: string
    choices: { id: string; label: string }[]; correctAnswer: string | string[] | boolean | null
    referenceAnswer: string; score: number | null; order: number; sourceFile?: string; sourcePage: number; sourceRegion: number[]; warnings: string[] }[]
}
