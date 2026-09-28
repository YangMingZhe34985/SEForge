<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import SafeMarkdown from '@/components/SafeMarkdown.vue'
import TeachingResourceList from '@/components/TeachingResourceList.vue'
import KnowledgePointDraftEditor from '@/components/KnowledgePointDraftEditor.vue'
import { teachingContentApi, type PointSource } from '@/api/teachingContent'
import { courseApi } from '@/api/courses'
import { jobApi } from '@/api/jobs'
import { useCourseStore } from '@/stores/courses'
import type {
  CourseAnnouncement,
  CourseChapter,
  CourseClass,
  CourseInvite,
  CourseMember,
  CourseResource,
  AsyncJob,
  KnowledgeDocument,
  KnowledgePoint,
} from '@/types/domain'

const route = useRoute()
const router = useRouter()
const store = useCourseStore()

const courseId = computed(() => String(route.params.courseId ?? ''))
// Route names are workspace-scoped (teacher-course-* / student-course-*); the shared
// component derives link targets from the workspace the route belongs to.
const workspace = computed<'teacher' | 'student'>(() => (route.meta.workspace === 'teacher' ? 'teacher' : 'student'))
const activeTab = ref('chapters')
const selectedChapterId = ref('')
const selectedChapter = computed(() => chapters.value.find(c => c.id === selectedChapterId.value))
const chapterFiles = ref<{ file: File; include: boolean }[]>([])
const chapterSaving = ref(false)
const includeChapterUploads = ref(true)
function supportsRag(name: string) { return /\.(pdf|pptx?|docx|md|txt)$/i.test(name) }
function chooseChapterFiles(files: FileList | null) { chapterFiles.value = Array.from(files || []).map(file => ({ file, include: supportsRag(file.name) })) }
function pointSources(point: KnowledgePoint): PointSource[] { try { return JSON.parse(point.sourceCitations || '[]') as PointSource[] } catch { return [] } }
const resourceVisible = ref(false)
const chapterVisible = ref(false)
const pointVisible = ref(false)
const classVisible = ref(false)
const inviteVisible = ref(false)
const announcementVisible = ref(false)
const editVisible = ref(false)
const detailLoading = ref(false)
const resourceUploading = ref(false)
const editingChapterId = ref<string | null>(null)
const editingPointId = ref<string | null>(null)
const editingClassId = ref<string | null>(null)
const editingAnnouncementId = ref<string | null>(null)
const editingMember = ref<CourseMember | null>(null)
const memberForm = reactive({ classId: '' as string, role: 'STUDENT' as 'STUDENT' | 'TA' })
const resources = ref<CourseResource[]>([])
const documents = ref<KnowledgeDocument[]>([])
const documentLoadError = ref('')
const documentJobs = reactive<Record<string, AsyncJob>>({})
const chapters = ref<CourseChapter[]>([])
const knowledgePoints = ref<KnowledgePoint[]>([])
const courseClasses = ref<CourseClass[]>([])
const invites = ref<CourseInvite[]>([])
const members = ref<CourseMember[]>([])
const announcements = ref<CourseAnnouncement[]>([])
const announcementPage = ref(1)
const announcementTotal = ref(0)
const announcementPageSize = 10
const memberPage = ref(1)
const memberPageSize = 10
const courseForm = reactive({ name: '', description: '', status: 'ACTIVE' as 'ACTIVE' | 'ARCHIVED' })
const resourceForm = reactive({ name: '', description: '', resourceType: 'LINK', objectKey: '', contentType: '', chapterId: '' })
const chapterForm = reactive({ title: '', description: '', sortOrder: 1, parentId: undefined as string | undefined })
const pointForm = reactive({ title: '', description: '', chapterId: '', sortOrder: 1 })
const classForm = reactive({ code: '', name: '', capacity: undefined as number | undefined, primaryClass: false })
const inviteForm = reactive({
  classId: '',
  memberRole: 'STUDENT' as 'STUDENT' | 'TA',
  maxUses: undefined as number | undefined,
  expiresAt: undefined as Date | undefined,
})
const announcementForm = reactive({ title: '', content: '' })
const terminalJobStatuses = new Set(['COMPLETED', 'FAILED', 'DEAD_LETTER', 'CANCELLED'])
let detailGeneration = 0
let componentMounted = true
const selected = computed(() => store.courses.find((course) => course.id === courseId.value) ?? null)
const canManageSelected = computed(() => ['TEACHER', 'TA'].includes(selected.value?.role || ''))
const canAdministerSelected = computed(() => selected.value?.role === 'TEACHER')
const pagedMembers = computed(() => {
  const start = (memberPage.value - 1) * memberPageSize
  return members.value.slice(start, start + memberPageSize)
})

async function loadDetails(targetCourseId: string | null) {
  const generation = ++detailGeneration
  documentLoadError.value = ''
  if (!targetCourseId) {
    resources.value = []
    documents.value = []
    chapters.value = []
    knowledgePoints.value = []
    courseClasses.value = []
    invites.value = []
    members.value = []
    announcements.value = []
    announcementTotal.value = 0
    Object.keys(documentJobs).forEach((documentId) => delete documentJobs[documentId])
    detailLoading.value = false
    return
  }
  detailLoading.value = true
  try {
    const [resourceList, documentList, chapterList, pointList, announcementPageResult] = await Promise.all([
      courseApi.resources(targetCourseId),
      // Keep course resources available, but never disguise an ingestion outage as an empty library.
      courseApi.documents(targetCourseId).catch((error: unknown) => {
        if (componentMounted && generation === detailGeneration) {
          documentLoadError.value = error instanceof Error ? error.message : '知识文档加载失败，请刷新重试'
        }
        return [] as KnowledgeDocument[]
      }),
      courseApi.chapters(targetCourseId),
      courseApi.knowledgePoints(targetCourseId),
      courseApi.announcements(targetCourseId, 0, announcementPageSize),
    ])
    const course = store.courses.find((item) => item.id === targetCourseId)
    const mayViewTeaching = ['TEACHER', 'TA'].includes(course?.role || '')
    const mayAdminister = course?.role === 'TEACHER'
    let classList: CourseClass[] = []
    let memberList: CourseMember[] = []
    let inviteList: CourseInvite[] = []
    if (mayViewTeaching) {
      const teachingData = await Promise.all([
        courseApi.classes(targetCourseId),
        courseApi.members(targetCourseId),
      ])
      classList = teachingData[0]
      memberList = teachingData[1]
      if (mayAdminister) inviteList = await courseApi.invites(targetCourseId)
    }
    if (!componentMounted || generation !== detailGeneration || courseId.value !== targetCourseId) return

    resources.value = resourceList
    documents.value = documentList
    chapters.value = chapterList.sort((a, b) => a.sortOrder - b.sortOrder)
    if (!chapters.value.some(c => c.id === selectedChapterId.value)) selectedChapterId.value = chapters.value[0]?.id || ''
    knowledgePoints.value = pointList
    announcements.value = announcementPageResult.items
    announcementPage.value = announcementPageResult.page + 1
    announcementTotal.value = announcementPageResult.total
    courseClasses.value = classList
    invites.value = inviteList
    members.value = memberList
    memberPage.value = 1
    Object.keys(documentJobs).forEach((documentId) => delete documentJobs[documentId])
    documentList.forEach((document) => {
      if (!document.job) return
      documentJobs[document.id] = document.job
      if (!terminalJobStatuses.has(document.job.status)) {
        void monitorDocumentJob(document.id, document.job.id, targetCourseId, generation)
      }
    })
  } catch (error) {
    if (componentMounted && generation === detailGeneration) {
      ElMessage.error(error instanceof Error ? error.message : '课程详情加载失败')
    }
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

function openEditDialog() {
  if (!selected.value) return
  Object.assign(courseForm, {
    name: selected.value.name,
    description: selected.value.description || '',
    status: selected.value.status,
  })
  editVisible.value = true
}

async function saveCourseEdit() {
  if (!selected.value || !courseForm.name.trim()) return ElMessage.warning('请填写课程名称')
  try {
    const updated = await courseApi.update(selected.value.id, {
      name: courseForm.name.trim(),
      description: courseForm.description,
      status: courseForm.status,
    })
    const index = store.courses.findIndex((course) => course.id === updated.id)
    if (index >= 0) store.courses[index] = { ...store.courses[index], ...updated }
    editVisible.value = false
    ElMessage.success(updated.status === 'ARCHIVED' ? '课程已更新并归档' : '课程已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '课程更新失败')
  }
}

async function toggleArchive() {
  if (!selected.value) return
  const archiving = selected.value.status !== 'ARCHIVED'
  try {
    await ElMessageBox.confirm(
      archiving
        ? `归档后“${selected.value.name}”将不再接受新成员加入，确定归档吗？`
        : `确定恢复“${selected.value.name}”为进行中吗？`,
      archiving ? '归档课程' : '恢复课程',
      { type: 'warning' },
    )
    const updated = await courseApi.update(selected.value.id, { status: archiving ? 'ARCHIVED' : 'ACTIVE' })
    const index = store.courses.findIndex((course) => course.id === updated.id)
    if (index >= 0) store.courses[index] = { ...store.courses[index], ...updated }
    ElMessage.success(archiving ? '课程已归档' : '课程已恢复')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '操作失败')
    }
  }
}

async function createResource() {
  if (!selected.value || !resourceForm.name.trim() || !/^https?:\/\//i.test(resourceForm.objectKey)) return ElMessage.warning('请填写名称和 HTTP(S) 链接')
  try {
    resources.value.unshift(await teachingContentApi.link(selected.value.id, { name: resourceForm.name, url: resourceForm.objectKey, description: resourceForm.description, chapterId: resourceForm.chapterId || undefined }))
    resourceVisible.value = false
    Object.assign(resourceForm, { name: '', description: '', resourceType: 'LINK', objectKey: '', contentType: '', chapterId: '' })
    ElMessage.success('参考链接已保存')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '链接保存失败') }
}

async function uploadResource(files: FileList | null, chapterId?: string) {
  if (!selected.value || !files?.length || resourceUploading.value) return
  const target = selected.value.id
  resourceUploading.value = true
  try {
    for (const file of Array.from(files)) await courseApi.uploadResource(target, file, chapterId, !!chapterId && includeChapterUploads.value && supportsRag(file.name))
    await loadDetails(target)
    ElMessage.success('资料已上传')
  } catch (error) { await loadDetails(target); ElMessage.error(error instanceof Error ? error.message : '资料上传失败，已成功的资料会保留，请只重试失败文件') }
  finally { resourceUploading.value = false }
}

async function includeResource(resource: CourseResource) {
  if (!selected.value) return
  try { await teachingContentApi.include(selected.value.id, resource.id); await loadDetails(selected.value.id) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '加入知识库失败') }
}

async function editResource(resource: CourseResource) {
  if (!selected.value) return
  try {
    const name = await ElMessageBox.prompt('修改显示名称（文件保留扩展名）', '编辑资料', { inputValue: resource.name })
    const description = await ElMessageBox.prompt('修改资料说明', '资料说明', { inputValue: resource.description || '' })
    await teachingContentApi.update(selected.value.id, resource.id, name.value, description.value)
    await loadDetails(selected.value.id)
  } catch (error) { if (!isDialogDismissal(error)) ElMessage.error(error instanceof Error ? error.message : '修改失败') }
}

async function downloadResource(resource: CourseResource) {
  if (!selected.value) return
  try { await courseApi.downloadResource(selected.value.id, resource) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '资源下载失败') }
}

async function deleteResource(resource: CourseResource) {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(`确定删除“${resource.name}”及其原件和知识索引吗？此操作不可撤销。`, '删除资料', { type: 'warning' })
    await courseApi.deleteResource(selected.value.id, resource.id)
    await loadDetails(selected.value.id)
    ElMessage.success('资料和关联索引已删除')
  } catch (error) { if (!isDialogDismissal(error)) ElMessage.error(error instanceof Error ? error.message : '撤下失败') }
}

async function reindexDocument(document: KnowledgeDocument) {
  if (!selected.value) return
  const targetCourseId = selected.value.id
  try {
    const job = await courseApi.reindexDocument(targetCourseId, document.id)
    if (!componentMounted || courseId.value !== targetCourseId) return
    documentJobs[document.id] = job
    document.status = 'QUEUED'
    void monitorDocumentJob(document.id, job.id, targetCourseId, detailGeneration)
    ElMessage.success('已重新进入摄取队列')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '重建索引失败') }
}

async function monitorDocumentJob(documentId: string, jobId: string, targetCourseId: string, generation: number) {
  for (let attempt = 0; attempt < 180; attempt += 1) {
    if (!componentMounted || generation !== detailGeneration
      || courseId.value !== targetCourseId || documentJobs[documentId]?.id !== jobId) return
    try {
      const job = await jobApi.get(jobId)
      if (!componentMounted || generation !== detailGeneration
        || courseId.value !== targetCourseId || documentJobs[documentId]?.id !== jobId) return
      documentJobs[documentId] = job
      if (terminalJobStatuses.has(job.status)) {
        if (job.status === 'COMPLETED') ElMessage.success('文档摄取已完成')
        else if (job.status !== 'CANCELLED') ElMessage.error(job.error || `文档摄取失败：${job.status}`)
        await loadDetails(targetCourseId)
        return
      }
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '摄取任务状态获取失败')
      return
    }
    await new Promise((resolve) => window.setTimeout(resolve, 1_000))
  }
  if (componentMounted && generation === detailGeneration && courseId.value === targetCourseId) {
    ElMessage.warning('摄取任务仍在运行，可稍后手动刷新')
  }
}

async function cancelDocumentJob(document: KnowledgeDocument) {
  const job = documentJobs[document.id]
  if (!job || job.cancelRequested || terminalJobStatuses.has(job.status)) return
  try {
    await ElMessageBox.confirm(`确定取消“${document.name}”的当前摄取任务吗？`, '取消摄取', { type: 'warning' })
    documentJobs[document.id] = await jobApi.cancel(job.id)
    ElMessage.success('已请求取消摄取任务，可使用“重建索引”重试')
  } catch (error) { if (!isDialogDismissal(error)) ElMessage.error(error instanceof Error ? error.message : '取消失败') }
}

function documentTaskStatus(document: KnowledgeDocument): string {
  const job = documentJobs[document.id]
  return job?.status === 'COMPLETED' ? document.status : (job?.status || document.status)
}

function documentTaskError(document: KnowledgeDocument): string | undefined {
  const job = documentJobs[document.id]
  return job?.status === 'COMPLETED' ? document.error : (job?.error || document.error)
}

function mayCancelDocumentJob(document: KnowledgeDocument): boolean {
  const job = documentJobs[document.id]
  return Boolean(job && !job.cancelRequested && !terminalJobStatuses.has(job.status))
}

function isDocumentJobCancelling(document: KnowledgeDocument): boolean {
  const job = documentJobs[document.id]
  return Boolean(job?.cancelRequested && !terminalJobStatuses.has(job.status))
}

function isDialogDismissal(error: unknown): boolean {
  return error === 'cancel' || error === 'close'
}

async function deleteDocument(document: KnowledgeDocument) {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(`将“${document.name}”移出 AI 知识库吗？资料原件仍可下载。`, '移出知识库', { type: 'warning' })
    await courseApi.deleteDocument(selected.value.id, document.id)
    documents.value = documents.value.filter((item) => item.id !== document.id)
  } catch (error) { if (!isDialogDismissal(error)) ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

async function createChapter() {
  if (!selected.value || !chapterForm.title.trim() || chapterSaving.value) return
  const targetCourseId = selected.value.id
  const pendingFiles = [...chapterFiles.value]
  const stillCurrent = () => componentMounted && courseId.value === targetCourseId
  chapterSaving.value = true
  try {
    const value = editingChapterId.value
      ? await courseApi.updateChapter(targetCourseId, editingChapterId.value, { ...chapterForm })
      : await courseApi.createChapter(targetCourseId, { ...chapterForm })
    if (!stillCurrent()) return
    const index = chapters.value.findIndex((item) => item.id === value.id)
    if (index >= 0) chapters.value[index] = value
    else chapters.value.push(value)
    chapters.value.sort((a, b) => a.sortOrder - b.sortOrder)
    editingChapterId.value = value.id
    selectedChapterId.value = value.id
    for (const item of pendingFiles) {
      await courseApi.uploadResource(targetCourseId, item.file, value.id, item.include)
      if (!stillCurrent()) return
      chapterFiles.value.shift()
    }
    await loadDetails(targetCourseId)
    if (!stillCurrent()) return
    chapterVisible.value = false
    editingChapterId.value = null
    Object.assign(chapterForm, { title: '', description: '', sortOrder: chapters.value.length + 1, parentId: undefined })
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '章节保存或资料上传失败；已保存内容保留，可重试剩余文件') }
  finally { chapterSaving.value = false }
}

function editChapter(chapter: CourseChapter) {
  chapterFiles.value = []
  editingChapterId.value = chapter.id
  Object.assign(chapterForm, { title: chapter.title, description: chapter.description || '', sortOrder: chapter.sortOrder, parentId: chapter.parentId || undefined })
  chapterVisible.value = true
}

async function deleteChapter(chapter: CourseChapter) {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(`确定删除章节“${chapter.title}”吗？关联内容未清空时服务端会拒绝。`, '删除章节', { type: 'warning' })
    await courseApi.deleteChapter(selected.value.id, chapter.id)
    chapters.value = chapters.value.filter((item) => item.id !== chapter.id)
  } catch (error) { if (!isDialogDismissal(error)) ElMessage.error(error instanceof Error ? error.message : '章节删除失败') }
}

async function createPoint() {
  if (!selected.value || !pointForm.title.trim()) return
  try {
    const input = { ...pointForm, chapterId: pointForm.chapterId || undefined }
    const value = editingPointId.value
      ? await courseApi.updateKnowledgePoint(selected.value.id, editingPointId.value, input)
      : await courseApi.createKnowledgePoint(selected.value.id, input)
    const index = knowledgePoints.value.findIndex((item) => item.id === value.id)
    if (index >= 0) knowledgePoints.value[index] = value
    else knowledgePoints.value.push(value)
    pointVisible.value = false
    editingPointId.value = null
    Object.assign(pointForm, { title: '', description: '', chapterId: '', sortOrder: knowledgePoints.value.length + 1 })
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '知识点创建失败') }
}

function editPoint(point: KnowledgePoint) {
  editingPointId.value = point.id
  Object.assign(pointForm, { title: point.title, description: point.description || '', chapterId: point.chapterId || '', sortOrder: point.sortOrder })
  pointVisible.value = true
}

async function deletePoint(point: KnowledgePoint) {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(`确定删除知识点“${point.title}”吗？`, '删除知识点', { type: 'warning' })
    await courseApi.deleteKnowledgePoint(selected.value.id, point.id)
    knowledgePoints.value = knowledgePoints.value.filter((item) => item.id !== point.id)
  } catch (error) { if (!isDialogDismissal(error)) ElMessage.error(error instanceof Error ? error.message : '知识点删除失败') }
}

async function loadAnnouncements(page: number) {
  if (!selected.value) return
  try {
    const result = await courseApi.announcements(selected.value.id, Math.max(0, page - 1), announcementPageSize)
    announcements.value = result.items
    announcementPage.value = result.page + 1
    announcementTotal.value = result.total
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '公告加载失败') }
}

async function createAnnouncement() {
  if (!selected.value || !announcementForm.title.trim() || !announcementForm.content.trim()) {
    return ElMessage.warning('请填写公告标题和内容')
  }
  try {
    const input = {
      title: announcementForm.title.trim(),
      content: announcementForm.content.trim(),
    }
    const announcement = editingAnnouncementId.value
      ? await courseApi.updateAnnouncement(selected.value.id, editingAnnouncementId.value, input)
      : await courseApi.createAnnouncement(selected.value.id, input)
    const wasEditing = Boolean(editingAnnouncementId.value)
    editingAnnouncementId.value = null
    announcementVisible.value = false
    Object.assign(announcementForm, { title: '', content: '' })
    if (wasEditing) {
      const index = announcements.value.findIndex((item) => item.id === announcement.id)
      if (index >= 0) announcements.value[index] = announcement
    } else if (announcementPage.value === 1) {
      announcements.value.unshift(announcement)
      announcements.value = announcements.value.slice(0, announcementPageSize)
      announcementTotal.value += 1
    } else {
      await loadAnnouncements(1)
    }
    ElMessage.success('公告已发布')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '公告发布失败') }
}

function editAnnouncement(announcement: CourseAnnouncement) {
  editingAnnouncementId.value = announcement.id
  Object.assign(announcementForm, { title: announcement.title, content: announcement.content })
  announcementVisible.value = true
}

async function withdrawAnnouncement(announcement: CourseAnnouncement) {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(`确定撤回公告“${announcement.title}”吗？`, '撤回公告', { type: 'warning' })
    await courseApi.withdrawAnnouncement(selected.value.id, announcement.id)
    await loadAnnouncements(announcementPage.value)
  } catch (error) { if (!isDialogDismissal(error)) ElMessage.error(error instanceof Error ? error.message : '撤回失败') }
}

async function createClass() {
  if (!selected.value || !classForm.name.trim()) return ElMessage.warning('请填写教学班名称')
  try {
    const input = {
      name: classForm.name.trim(),
      capacity: classForm.capacity,
      primaryClass: classForm.primaryClass,
    }
    const value = editingClassId.value
      ? await courseApi.updateClass(selected.value.id, editingClassId.value, { name: input.name, capacity: input.capacity ?? null, primaryClass: input.primaryClass, active: true })
      : await courseApi.createClass(selected.value.id, input)
    const index = courseClasses.value.findIndex((item) => item.id === value.id)
    if (index >= 0) courseClasses.value[index] = value
    else courseClasses.value.push(value)
    courseClasses.value.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
    classVisible.value = false
    editingClassId.value = null
    Object.assign(classForm, { code: '', name: '', capacity: undefined, primaryClass: false })
    ElMessage.success('教学班已创建')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '教学班创建失败') }
}

function editClass(courseClass: CourseClass) {
  editingClassId.value = courseClass.id
  Object.assign(classForm, { code: courseClass.code, name: courseClass.name, capacity: courseClass.capacity ?? undefined, primaryClass: courseClass.primaryClass })
  classVisible.value = true
}

async function toggleClass(courseClass: CourseClass) {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(`确定${courseClass.active ? '关闭' : '恢复'}教学班“${courseClass.name}”吗？`, '教学班状态', { type: 'warning' })
    const value = await courseApi.updateClass(selected.value.id, courseClass.id, {
      name: courseClass.name, capacity: courseClass.capacity, primaryClass: courseClass.primaryClass, active: !courseClass.active,
    })
    Object.assign(courseClass, value)
  } catch (error) { if (!isDialogDismissal(error)) ElMessage.error(error instanceof Error ? error.message : '教学班更新失败') }
}

async function createInvite() {
  if (!selected.value) return
  try {
    const invite = await courseApi.createInvite(selected.value.id, {
      classId: inviteForm.classId || undefined,
      memberRole: inviteForm.memberRole,
      maxUses: inviteForm.maxUses,
      expiresAt: inviteForm.expiresAt?.toISOString(),
    })
    invites.value.unshift(invite)
    inviteVisible.value = false
    Object.assign(inviteForm, { classId: '', memberRole: 'STUDENT', maxUses: undefined, expiresAt: undefined })
    await copyInvite(invite.code, false)
    ElMessage.success(`邀请码 ${invite.code} 已创建并复制`)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '邀请码创建失败') }
}

async function revokeInvite(invite: CourseInvite) {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(`确定吊销邀请码 ${invite.code} 吗？`, '吊销邀请码', { type: 'warning' })
    Object.assign(invite, await courseApi.revokeInvite(selected.value.id, invite.id))
  } catch (error) { if (!isDialogDismissal(error)) ElMessage.error(error instanceof Error ? error.message : '邀请码吊销失败') }
}

async function copyInvite(code: string, notify = true) {
  try {
    await navigator.clipboard.writeText(code)
    if (notify) ElMessage.success('邀请码已复制')
  } catch {
    ElMessage.warning(`无法自动复制，请手动复制：${code}`)
  }
}

async function removeMember(member: CourseMember) {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(`确定将“${member.displayName}”移出课程吗？`, '移除课程成员', { type: 'warning' })
    await courseApi.removeMember(selected.value.id, member.userId)
    members.value = members.value.filter((item) => item.userId !== member.userId)
    selected.value.memberCount = Math.max(0, selected.value.memberCount - 1)
    const maxPage = Math.max(1, Math.ceil(members.value.length / memberPageSize))
    memberPage.value = Math.min(memberPage.value, maxPage)
    ElMessage.success('成员已移除')
  } catch (error) { if (!isDialogDismissal(error)) ElMessage.error(error instanceof Error ? error.message : '移除成员失败') }
}

function editMember(member: CourseMember) {
  editingMember.value = member
  Object.assign(memberForm, { classId: member.classId || '', role: member.role === 'TA' ? 'TA' : 'STUDENT' })
}

async function saveMember() {
  if (!selected.value || !editingMember.value) return
  try {
    const updated = await courseApi.updateMember(selected.value.id, editingMember.value.userId, {
      classId: memberForm.classId || null, role: memberForm.role,
    })
    Object.assign(editingMember.value, updated)
    editingMember.value = null
    ElMessage.success('成员已更新')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '成员更新失败') }
}

function className(classId: string | null): string {
  return courseClasses.value.find((item) => item.id === classId)?.name || '未分班'
}

function formatDateTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '不限'
}

watch(() => store.selectedCourseId, (id) => { void loadDetails(id) }, { immediate: true })
watch(courseId, (id) => {
  // The router guard keeps the store selection in sync; this covers direct param changes.
  if (id && store.selectedCourseId !== id) store.select(id)
})
onMounted(async () => {
  if (!store.courses.length) {
    try { await store.load() } catch { /* the router guard already surfaces load failures */ }
  }
  if (courseId.value && store.selectedCourseId !== courseId.value) store.select(courseId.value)
})
onBeforeUnmount(() => {
  componentMounted = false
  detailGeneration += 1
})
</script>

<template>
  <div>
    <template v-if="selected">
      <PageHeader :title="selected.name" :description="`${selected.code} · ${selected.semesterName || '未设置学期'}`">
        <div class="button-row">
          <el-tag v-if="selected.status === 'ARCHIVED'" type="warning" effect="plain">已归档</el-tag>
          <el-button @click="router.push({ name: `${workspace}-course-assistant`, params: { courseId: selected.id } })">进入课程助手</el-button>
          <el-button @click="router.push({ name: `${workspace}-course-assignments`, params: { courseId: selected.id } })">查看作业</el-button>
          <template v-if="canManageSelected">
            <el-button @click="router.push({ name: `${workspace}-course-reviews`, params: { courseId: selected.id } })">智能评审</el-button>
            <el-button @click="router.push({ name: `${workspace}-course-dashboard`, params: { courseId: selected.id } })">教学 Dashboard</el-button>
          </template>
          <template v-if="canAdministerSelected">
            <el-button @click="openEditDialog">编辑课程</el-button>
            <el-button :type="selected.status === 'ARCHIVED' ? 'success' : 'warning'" @click="toggleArchive">
              {{ selected.status === 'ARCHIVED' ? '恢复课程' : '归档课程' }}
            </el-button>
          </template>
        </div>
      </PageHeader>

      <section class="course-workspace panel">
        <el-tabs v-model="activeTab" class="course-tabs" v-loading="detailLoading">
          <el-tab-pane label="课程公告" name="announcements">
            <div class="tab-toolbar"><p>课程成员可查看公告，公告按发布时间倒序排列。</p><el-button v-if="canManageSelected" type="primary" @click="announcementVisible = true">发布公告</el-button></div>
            <div v-if="announcements.length" class="announcement-list">
              <article v-for="announcement in announcements" :key="announcement.id" class="announcement-card">
                <div class="announcement-card__header"><h3>{{ announcement.title }}</h3><time>{{ formatDateTime(announcement.publishedAt) }}</time></div>
                <p>{{ announcement.content }}</p>
                <div v-if="canManageSelected" class="button-row"><el-button link @click="editAnnouncement(announcement)">修改</el-button><el-button link type="danger" @click="withdrawAnnouncement(announcement)">撤回</el-button></div>
              </article>
            </div>
            <EmptyState v-else title="暂无课程公告" description="教师或助教发布公告后，所有课程成员都能在这里看到。" />
            <el-pagination v-if="announcementTotal > announcementPageSize" v-model:current-page="announcementPage" class="member-pagination" background layout="prev, pager, next" :page-size="announcementPageSize" :total="announcementTotal" @current-change="loadAnnouncements" />
          </el-tab-pane>
          <el-tab-pane label="章节" name="chapters">
            <div class="tab-toolbar"><p>按章节组织教学资料、AI 知识库与知识点。</p><el-button v-if="canManageSelected" @click="editingChapterId = null; chapterFiles = []; chapterForm.title = ''; chapterForm.description = ''; chapterVisible = true">添加章节</el-button></div>
            <el-select v-if="chapters.length" v-model="selectedChapterId" aria-label="当前章节"><el-option v-for="chapter in chapters" :key="chapter.id" :label="chapter.title" :value="chapter.id" /></el-select>
            <section v-if="selectedChapter">
              <h3>{{ selectedChapter.title }}</h3><SafeMarkdown :content="selectedChapter.description || ''" />
              <div v-if="canManageSelected"><el-button link @click="editChapter(selectedChapter)">编辑章节</el-button><el-button link type="danger" @click="deleteChapter(selectedChapter)">删除章节</el-button></div>
              <h3>章节资料</h3><div v-if="canManageSelected" class="tab-toolbar"><el-checkbox v-model="includeChapterUploads">支持的文档默认加入 AI 知识库</el-checkbox><label class="upload-button"><input type="file" multiple :disabled="resourceUploading" @change="uploadResource(($event.target as HTMLInputElement).files, selectedChapterId)" />上传章节资料</label></div>
              <el-alert v-if="documentLoadError" :title="documentLoadError" type="error" :closable="false" />
              <TeachingResourceList :resources="resources.filter(r => r.chapterId === selectedChapterId)" :documents="documents" :manage="canManageSelected" :status="documentTaskStatus" :error="documentTaskError" :cancelable="mayCancelDocumentJob" :cancelling="isDocumentJobCancelling" :format-bytes="formatBytes" @download="downloadResource" @remove="deleteResource" @edit="editResource" @include="includeResource" @exclude="deleteDocument" @reindex="reindexDocument" @cancel="cancelDocumentJob" />
              <h3>知识点</h3><template v-if="canManageSelected"><el-button @click="editingPointId = null; pointForm.title = ''; pointForm.description = ''; pointForm.chapterId = selectedChapterId; pointVisible = true">添加知识点</el-button><KnowledgePointDraftEditor :course-id="selected.id" :chapter-id="selectedChapterId" @confirmed="loadDetails(selected.id)" /></template>
              <article v-for="point in knowledgePoints.filter(p => p.chapterId === selectedChapterId)" :key="point.id"><h4>{{ point.title }} <small>{{ point.importance }}</small></h4><p>{{ point.description }}</p><details v-if="pointSources(point).length"><summary>来源引用</summary><blockquote v-for="source in pointSources(point)" :key="source.id">{{ source.name }} · {{ source.page ? '第 ' + source.page + ' 页' : source.section }}<p>{{ source.quote }}</p></blockquote></details><template v-if="canManageSelected"><el-button link @click="editPoint(point)">编辑知识点</el-button><el-button link type="danger" @click="deletePoint(point)">删除知识点</el-button></template></article>
            </section><EmptyState v-else title="暂无章节" />
          </el-tab-pane>
          <el-tab-pane label="参考资料" name="resources">
            <div class="tab-toolbar"><p>课程级文件、模板、数据集与外部链接；默认不加入 AI 知识库。</p><div class="button-row"><el-button @click="loadDetails(selected.id)">刷新</el-button><template v-if="canManageSelected"><label class="upload-button"><input type="file" multiple :disabled="resourceUploading" @change="uploadResource(($event.target as HTMLInputElement).files)" />上传参考资料</label><el-button @click="resourceVisible = true">添加外部链接</el-button></template></div></div>
            <el-alert v-if="documentLoadError" :title="documentLoadError" type="error" :closable="false" />
            <TeachingResourceList :resources="resources.filter(r => !r.chapterId)" :documents="documents" :manage="canManageSelected" :status="documentTaskStatus" :error="documentTaskError" :cancelable="mayCancelDocumentJob" :cancelling="isDocumentJobCancelling" :format-bytes="formatBytes" @download="downloadResource" @remove="deleteResource" @edit="editResource" @include="includeResource" @exclude="deleteDocument" @reindex="reindexDocument" @cancel="cancelDocumentJob" />
            <h3 v-if="knowledgePoints.some(p => !p.chapterId)">课程通用知识点</h3><article v-for="point in knowledgePoints.filter(p => !p.chapterId)" :key="point.id">{{ point.title }}<template v-if="canManageSelected"><el-button link @click="editPoint(point)">编辑知识点</el-button><el-button link type="danger" @click="deletePoint(point)">删除知识点</el-button></template></article>
          </el-tab-pane>
          <el-tab-pane v-if="canManageSelected" label="教学管理" name="teaching">
            <section class="management-section">
              <div class="tab-toolbar"><div><h3>教学班</h3><p>教学班可用于邀请码归属和后续班级维度统计。</p></div><el-button v-if="canAdministerSelected" @click="classVisible = true">创建教学班</el-button></div>
              <el-table v-if="courseClasses.length" :data="courseClasses" stripe>
                <el-table-column prop="code" label="班级代码" min-width="130" />
                <el-table-column prop="name" label="班级名称" min-width="180" />
                <el-table-column label="容量" width="100"><template #default="scope">{{ scope.row.capacity ?? '不限' }}</template></el-table-column>
                <el-table-column label="类型" width="110"><template #default="scope"><el-tag v-if="scope.row.primaryClass" type="success" effect="plain">主教学班</el-tag><span v-else class="muted">普通班</span></template></el-table-column>
                <el-table-column label="状态" width="90"><template #default="scope">{{ scope.row.active ? '开放' : '关闭' }}</template></el-table-column>
                <el-table-column v-if="canAdministerSelected" label="操作" width="140"><template #default="scope"><el-button link @click="editClass(scope.row)">修改</el-button><el-button link :type="scope.row.active ? 'warning' : 'success'" @click="toggleClass(scope.row)">{{ scope.row.active ? '关闭' : '恢复' }}</el-button></template></el-table-column>
              </el-table>
              <EmptyState v-else title="暂无教学班" description="课程教师可以创建第一个教学班。" />
            </section>

            <section class="management-section">
              <div class="tab-toolbar"><div><h3>课程邀请码</h3><p>邀请码只授予学生加入资格，可限定教学班、使用次数和有效期。</p></div><el-button v-if="canAdministerSelected" type="primary" @click="inviteVisible = true">创建邀请码</el-button></div>
              <template v-if="canAdministerSelected">
                <el-table v-if="invites.length" :data="invites" stripe>
                  <el-table-column label="邀请码" min-width="175"><template #default="scope"><div class="invite-code"><code>{{ scope.row.code }}</code><el-button link type="primary" @click="copyInvite(scope.row.code)">复制</el-button></div></template></el-table-column>
                  <el-table-column label="教学班" min-width="150"><template #default="scope">{{ className(scope.row.classId) }}</template></el-table-column>
                  <el-table-column prop="memberRole" label="加入角色" width="105" />
                  <el-table-column label="使用次数" width="120"><template #default="scope">{{ scope.row.maxUses == null ? `${scope.row.usedCount} / 不限` : `${scope.row.usedCount} / ${scope.row.maxUses}` }}</template></el-table-column>
                  <el-table-column label="有效期" min-width="175"><template #default="scope">{{ formatDateTime(scope.row.expiresAt) }}</template></el-table-column>
                  <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.active ? 'success' : 'info'">{{ scope.row.active ? '有效' : '失效' }}</el-tag></template></el-table-column>
                  <el-table-column label="操作" width="90"><template #default="scope"><el-button v-if="scope.row.active" link type="danger" @click="revokeInvite(scope.row)">吊销</el-button></template></el-table-column>
                </el-table>
                <EmptyState v-else title="暂无邀请码" description="创建邀请码后可复制发给学生或助教。" />
              </template>
              <el-alert v-else title="助教可以查看成员；邀请码与教学班创建由课程教师管理。" type="info" :closable="false" show-icon />
            </section>

            <section class="management-section">
              <div class="tab-toolbar"><div><h3>课程成员</h3><p>共 {{ members.length }} 名有效成员，成员列表由服务端课程权限控制。</p></div></div>
              <el-table v-if="members.length" :data="pagedMembers" stripe>
                <el-table-column prop="displayName" label="姓名" min-width="180" />
                <el-table-column label="教学班" min-width="160"><template #default="scope">{{ className(scope.row.classId) }}</template></el-table-column>
                <el-table-column prop="role" label="课程角色" width="115" />
                <el-table-column label="加入时间" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.joinedAt) }}</template></el-table-column>
                <el-table-column v-if="canAdministerSelected" label="操作" width="130"><template #default="scope"><template v-if="scope.row.role !== 'TEACHER'"><el-button link @click="editMember(scope.row)">管理</el-button><el-button link type="danger" @click="removeMember(scope.row)">移除</el-button></template><span v-else class="muted">课程教师</span></template></el-table-column>
              </el-table>
              <EmptyState v-else title="暂无课程成员" />
              <el-pagination v-if="members.length > memberPageSize" v-model:current-page="memberPage" class="member-pagination" background layout="prev, pager, next" :page-size="memberPageSize" :total="members.length" />
            </section>
          </el-tab-pane>
        </el-tabs>
      </section>
    </template>
    <div v-else class="panel"><EmptyState title="课程不可用" description="该课程不存在，或当前账号无权访问。" /></div>

    <el-dialog v-model="editVisible" title="编辑课程" width="min(520px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="课程名称"><el-input v-model="courseForm.name" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="courseForm.description" type="textarea" /></el-form-item>
        <el-form-item label="课程状态">
          <el-select v-model="courseForm.status" style="width:100%">
            <el-option label="进行中（ACTIVE）" value="ACTIVE" />
            <el-option label="已归档（ARCHIVED）" value="ARCHIVED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" @click="saveCourseEdit">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="resourceVisible" title="添加参考链接" width="min(560px, 94vw)">
      <el-form label-position="top"><el-form-item label="显示名称"><el-input v-model="resourceForm.name" /></el-form-item><el-form-item label="HTTP(S) 链接"><el-input v-model="resourceForm.objectKey" placeholder="https://..." /></el-form-item><el-form-item label="说明"><el-input v-model="resourceForm.description" type="textarea" /></el-form-item></el-form>
      <template #footer><el-button @click="resourceVisible = false">取消</el-button><el-button type="primary" @click="createResource">保存链接</el-button></template>
    </el-dialog>
    <el-dialog v-model="chapterVisible" :title="editingChapterId ? '编辑章节' : '添加章节'" width="min(480px, 94vw)"><el-form label-position="top"><el-form-item label="章节名称"><el-input v-model="chapterForm.title" /></el-form-item><el-form-item label="顺序"><el-input-number v-model="chapterForm.sortOrder" :min="1" /></el-form-item><el-form-item label="教学说明（Markdown）"><el-input v-model="chapterForm.description" type="textarea" /></el-form-item><label>教学资料（可多选）<input type="file" multiple :disabled="chapterSaving" @change="chooseChapterFiles(($event.target as HTMLInputElement).files)" /></label><div v-for="item in chapterFiles" :key="item.file.name">{{ item.file.name }} <el-checkbox v-model="item.include" :disabled="!supportsRag(item.file.name) || chapterSaving">加入 AI 知识库</el-checkbox></div></el-form><template #footer><el-button type="primary" :loading="chapterSaving" @click="createChapter">保存</el-button></template></el-dialog>
    <el-dialog v-model="pointVisible" :title="editingPointId ? '编辑知识点' : '添加知识点'" width="min(480px, 94vw)"><el-form label-position="top"><el-form-item label="知识点名称"><el-input v-model="pointForm.title" /></el-form-item><el-form-item label="关联章节"><el-select v-model="pointForm.chapterId" clearable><el-option v-for="chapter in chapters" :key="chapter.id" :label="chapter.title" :value="chapter.id" /></el-select></el-form-item><el-form-item label="顺序"><el-input-number v-model="pointForm.sortOrder" :min="1" /></el-form-item><el-form-item label="说明"><el-input v-model="pointForm.description" type="textarea" /></el-form-item></el-form><template #footer><el-button type="primary" @click="createPoint">保存</el-button></template></el-dialog>
    <el-dialog v-model="classVisible" :title="editingClassId ? '修改教学班' : '创建教学班'" width="min(520px, 94vw)">
      <el-form label-position="top"><div class="form-grid"><el-form-item label="教学班代码"><el-input :model-value="editingClassId ? classForm.code : '创建后由系统生成'" disabled /></el-form-item><el-form-item label="教学班名称"><el-input v-model="classForm.name" placeholder="软件工程 1 班" /></el-form-item></div><div class="form-grid"><el-form-item label="容量（可选）"><el-input-number v-model="classForm.capacity" :min="1" :max="10000" controls-position="right" style="width:100%" /></el-form-item><el-form-item label="主教学班"><el-switch v-model="classForm.primaryClass" active-text="是" inactive-text="否" /></el-form-item></div></el-form>
      <template #footer><el-button @click="classVisible = false">取消</el-button><el-button type="primary" @click="createClass">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="inviteVisible" title="创建课程邀请码" width="min(540px, 94vw)">
      <el-form label-position="top"><div class="form-grid"><el-form-item label="加入教学班"><el-select v-model="inviteForm.classId" clearable placeholder="不指定教学班" style="width:100%"><el-option v-for="item in courseClasses.filter((item) => item.active)" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" /></el-select></el-form-item><el-form-item label="加入角色"><el-input value="学生" disabled /></el-form-item></div><div class="form-grid"><el-form-item label="最大使用次数"><el-input-number v-model="inviteForm.maxUses" :min="1" :max="10000" placeholder="不限" controls-position="right" style="width:100%" /></el-form-item><el-form-item label="失效时间"><el-date-picker v-model="inviteForm.expiresAt" type="datetime" placeholder="永不自动失效" style="width:100%" /></el-form-item></div></el-form>
      <template #footer><el-button @click="inviteVisible = false">取消</el-button><el-button type="primary" @click="createInvite">生成并复制</el-button></template>
    </el-dialog>
    <el-dialog v-model="announcementVisible" :title="editingAnnouncementId ? '修改课程公告' : '发布课程公告'" width="min(600px, 94vw)">
      <el-form label-position="top"><el-form-item label="标题"><el-input v-model="announcementForm.title" maxlength="200" show-word-limit /></el-form-item><el-form-item label="内容"><el-input v-model="announcementForm.content" type="textarea" :rows="8" maxlength="20000" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="announcementVisible = false">取消</el-button><el-button type="primary" @click="createAnnouncement">保存</el-button></template>
    </el-dialog>
    <el-dialog :model-value="Boolean(editingMember)" title="管理课程成员" width="min(480px, 94vw)" @close="editingMember = null">
      <el-form label-position="top"><el-form-item label="课程角色"><el-select v-model="memberForm.role"><el-option label="学生" value="STUDENT" /><el-option label="助教" value="TA" /></el-select></el-form-item><el-form-item label="教学班"><el-select v-model="memberForm.classId" clearable placeholder="未分班"><el-option v-for="item in courseClasses.filter((item) => item.active)" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item></el-form>
      <template #footer><el-button @click="editingMember = null">取消</el-button><el-button type="primary" @click="saveMember">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.course-workspace { margin-top: 4px; overflow: hidden; }
.course-tabs { padding: 0 21px 21px; }
.tab-toolbar { min-height: 58px; display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.tab-toolbar p { color: var(--muted); }
.tag-cloud { display: flex; flex-wrap: wrap; gap: 10px; padding: 18px 0; }
.announcement-list { display: grid; gap: 12px; }.announcement-card { border: 1px solid var(--line); border-radius: 10px; padding: 17px 19px; background: #fff; }.announcement-card__header { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; }.announcement-card h3, .announcement-card p { margin: 0; }.announcement-card time { color: var(--muted); font-size: 12px; white-space: nowrap; }.announcement-card p { margin-top: 10px; color: #4c566a; line-height: 1.7; white-space: pre-wrap; }
.management-section + .management-section { margin-top: 30px; border-top: 1px solid var(--line); padding-top: 18px; }
.management-section h3, .management-section p { margin: 0; }
.management-section p { margin-top: 5px; }
.invite-code { display: flex; align-items: center; gap: 8px; }.invite-code code { color: var(--brand); font-size: 14px; font-weight: 800; letter-spacing: .08em; }
.member-pagination { justify-content: flex-end; margin-top: 16px; }
.upload-button { display: inline-flex; align-items: center; border-radius: 4px; padding: 8px 15px; background: var(--brand); color: #fff; font-size: 14px; cursor: pointer; }.upload-button.disabled { opacity: .6; cursor: wait; }.upload-button input { position: absolute; width: 1px; height: 1px; opacity: 0; }.document-table { margin-bottom: 24px; }.resource-heading { margin: 26px 0 10px; border-top: 1px solid var(--line); padding-top: 22px; font-size: 14px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.dialog-form { margin-top: 18px; }
@media (max-width: 600px) { .form-grid { grid-template-columns: 1fr; gap: 0; } .tab-toolbar { align-items: flex-start; flex-direction: column; padding: 10px 0; } }
</style>
