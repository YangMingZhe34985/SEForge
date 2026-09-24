export type PlatformRole = 'ADMIN' | 'USER'
export type AccountType = 'STUDENT' | 'TEACHER'
export type CourseRole = 'TEACHER' | 'TA' | 'STUDENT'
export type SemesterStatus = 'PLANNED' | 'ACTIVE' | 'CLOSED'

export interface ApiEnvelope<T> {
  code: string | number
  message: string
  data: T
  traceId: string
}

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface User {
  id: string
  email: string
  username: string
  displayName: string
  accountType: AccountType
  roles: string[]
  enabled: boolean
}

export type AuditOutcome = 'SUCCEEDED' | 'FAILED' | 'REJECTED'

export interface AuditLogEntry {
  id: string
  actorId: string | null
  actorUsername: string | null
  courseId: string | null
  action: string
  targetType: string | null
  targetId: string | null
  outcome: AuditOutcome
  traceId: string
  occurredAt: string
}

export interface Semester {
  id: string
  code: string
  name: string
  startsOn: string
  endsOn: string
  status: SemesterStatus
}

export interface CourseSummary {
  id: string
  code: string
  name: string
  description?: string
  semesterId?: string
  semesterName?: string
  status: 'ACTIVE' | 'ARCHIVED'
  role: CourseRole | null
  memberCount: number
  createdAt: string
}

export interface CourseDetails extends CourseSummary {
  ownerId: string
  status: 'ACTIVE' | 'ARCHIVED'
  classes: CourseClass[]
}

export interface CourseClass {
  id: string
  code: string
  name: string
  capacity: number | null
  primaryClass: boolean
}

export interface CourseInvite {
  id: string
  code: string
  courseId: string
  classId: string | null
  memberRole: CourseRole
  maxUses: number | null
  usedCount: number
  expiresAt: string | null
  active: boolean
}

export interface CourseMember {
  id: string
  userId: string
  displayName: string
  classId: string | null
  role: CourseRole
  joinedAt: string
}

export interface CourseAnnouncement {
  id: string
  courseId: string
  authorId: string
  title: string
  content: string
  publishedAt: string
}

export interface CourseResource {
  id: string
  courseId: string
  name: string
  description?: string
  resourceType: string
  objectKey: string
  contentType?: string
  sizeBytes?: number
  chapterId?: string
  createdAt: string
}

export interface KnowledgeDocument {
  id: string
  courseId: string
  chapterId?: string
  name: string
  mediaType: string
  sizeBytes: number
  checksum: string
  status: string
  parserVersion?: string
  embeddingVersion?: string
  chunkingVersion?: string
  error?: string
  ingestedAt?: string
  createdAt: string
  job?: AsyncJob | null
}

export interface AsyncJob {
  id: string
  type: string
  status: JobStatus
  courseId?: string
  attempts: number
  maxAttempts: number
  cancelRequested: boolean
  result?: string
  error?: string
  createdAt: string
  updatedAt: string
}

export interface DocumentUploadResult {
  document: KnowledgeDocument
  job?: AsyncJob | null
  duplicate: boolean
}

export interface CourseChapter {
  id: string
  parentId?: string
  title: string
  sortOrder: number
  description?: string
}

export interface KnowledgePoint {
  id: string
  chapterId?: string
  title: string
  description?: string
  sortOrder: number
}

export interface Conversation {
  id: string
  courseId: string
  title: string
  updatedAt: string
}

export interface Citation {
  id?: string
  documentId: string
  chunkId?: string
  label?: string
  source?: string
  documentName?: string
  chapter?: string
  page?: number
  section?: string
  quote?: string
  excerpt?: string
  score?: number
}

export interface ChatMessage {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  citations?: Citation[]
  createdAt: string
  pending?: boolean
}

export type AssignmentStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED'
export type QuestionType =
  | 'SINGLE_CHOICE'
  | 'MULTIPLE_CHOICE'
  | 'TRUE_FALSE'
  | 'SHORT_ANSWER'
  | 'ANALYSIS'
  | 'DESIGN'
  | 'CODE'

export interface AssignmentSummary {
  id: string
  courseId: string
  title: string
  description?: string
  status: AssignmentStatus
  classId?: string
  availableAt?: string
  dueAt?: string
  maxAttempts?: number
  submitted?: boolean
  score?: number
}

export interface AssignmentQuestion {
  id: string
  type: QuestionType
  prompt: string
  options?: string[]
  points: number
  orderIndex: number
  knowledgePointId?: string
}

export interface AssignmentDetails extends AssignmentSummary {
  tutorPolicy?: TutorPolicyConfig
  questions: AssignmentQuestion[]
}

export interface TutorPolicyConfig {
  allowFullSolutionBeforeSubmit: boolean
  fullSolutionAfterSubmit: boolean
  fullSolutionAfterDue: boolean
  allowLateSubmission: boolean
  enabledOperations: TutorAction[]
  dueAtOverrides: Record<string, string>
}

export interface AssignmentQuestionInput {
  type: QuestionType
  prompt: string
  options?: string[]
  referenceAnswer?: string
  points: number
  orderIndex: number
  knowledgePointId?: string
  config?: Record<string, unknown>
}

export interface RubricItem {
  id: string
  questionId?: string
  title: string
  description?: string
  maxScore: number
  criteria?: Record<string, unknown>
  orderIndex: number
}

export interface AssignmentRubric {
  id: string
  assignmentId: string
  title: string
  totalScore: number
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  items: RubricItem[]
}

export interface SubmissionAnswerInput {
  questionId: string
  answer: string | string[] | boolean
  attachmentObjectKey?: string
  attachmentFileName?: string
}

export interface SubmissionAttachment {
  submissionId: string
  questionId: string
  objectKey: string
  fileName: string
  sizeBytes: number
  contentType: string
}

export interface Submission {
  id: string
  assignmentId: string
  status: 'DRAFT' | 'SUBMITTED' | 'GRADED'
  answers: SubmissionAnswerInput[]
  attemptNumber: number
  updatedAt: string
  submittedAt?: string
}

export interface TeacherSubmission {
  studentId: string
  studentName: string
  submission: Submission
}

export type TutorAction =
  | 'HINT'
  | 'EXPLAIN'
  | 'CHECK_REASONING'
  | 'ANALYZE_ERROR'
  | 'EVALUATE_DRAFT'
  | 'FULL_SOLUTION'

export interface TutorResponse {
  interactionId: string
  action: TutorAction
  content: string
  allowed: boolean
  policyMessage?: string
  citations?: Citation[]
}

export type ReviewType = 'DOCUMENT' | 'ASSIGNMENT' | 'CODE'
export type JobStatus =
  | 'PENDING'
  | 'QUEUED'
  | 'RUNNING'
  | 'PROCESSING'
  | 'RETRY_WAIT'
  | 'COMPLETED'
  | 'FAILED'
  | 'DEAD_LETTER'
  | 'CANCELLED'

export interface ReviewJob {
  id: string
  asyncJobId?: string
  courseId: string
  type: ReviewType
  subjectName: string
  status: JobStatus
  assignmentId?: string
  submissionId?: string
  documentId?: string
  resourceId?: string
  createdAt: string
  updatedAt?: string
  errorMessage?: string
  reportId?: string
}

export interface RubricEvaluation {
  rubricItemId: string
  title: string
  suggestedScore: number
  maxScore?: number
  evidence?: string
  feedback?: string
}

export interface ReviewReport {
  id: string
  jobId: string
  type: ReviewType
  summary: string
  scoreSuggestion?: number
  completeness?: string[]
  consistency?: string[]
  testability?: string[]
  clarity?: string[]
  suggestions: string[]
  rubricItems?: RubricEvaluation[]
  createdAt: string
}

export interface GradeRecord {
  id: string
  courseId: string
  assignmentId: string
  assignmentTitle: string
  studentId?: string
  studentName?: string
  score: number
  maxScore: number
  status: 'PENDING_CONFIRMATION' | 'FINAL'
  feedback?: string
  gradedAt?: string
}

export interface DashboardMetric {
  label: string
  value: number
  unit?: string
  delta?: number
}

export interface NamedValue {
  name: string
  value: number
}

export interface CourseDashboard {
  courseId: string
  classId?: string
  generatedAt: string
  metrics: DashboardMetric[]
  gradeDistribution: NamedValue[]
  knowledgePointAccuracy: NamedValue[]
  frequentQuestions: NamedValue[]
  weakKnowledgePoints: NamedValue[]
  tutorUsage: NamedValue[]
  qaFeedback: {
    helpful: number
    notHelpful: number
    helpfulRate: number
  }
  errorTrends: Array<{ date: string; value: number }>
}

export interface AnalyticsSnapshot {
  id: string
  courseId: string
  classId?: string
  metricType: string
  periodStart?: string
  periodEnd: string
  generatedAt: string
  payload: unknown
}

export interface CsrfToken {
  headerName: string
  parameterName: string
  token: string
}
