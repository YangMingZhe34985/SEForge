<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import StatusBadge from '@/components/StatusBadge.vue'
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
const activeTab = ref('resources')
const resourceVisible = ref(false)
const chapterVisible = ref(false)
const pointVisible = ref(false)
const classVisible = ref(false)
const inviteVisible = ref(false)
const announcementVisible = ref(false)
const editVisible = ref(false)
const detailLoading = ref(false)
const documentUploading = ref(false)
const resourceUploading = ref(false)
const editingChapterId = ref<string | null>(null)
const editingPointId = ref<string | null>(null)
const editingClassId = ref<string | null>(null)
const editingAnnouncementId = ref<string | null>(null)
const editingMember = ref<CourseMember | null>(null)
const memberForm = reactive({ classId: '' as string, role: 'STUDENT' as 'STUDENT' | 'TA' })
const resources = ref<CourseResource[]>([])
const documents = ref<KnowledgeDocument[]>([])
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
      // Knowledge ingestion is a later-phase capability: its outage must not hide
      // the Phase 1 course resources, chapters, and announcements.
      courseApi.documents(targetCourseId).catch(() => [] as KnowledgeDocument[]),
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
  if (!selected.value || !resourceForm.name.trim() || !resourceForm.objectKey.trim()) return ElMessage.warning('请填写名称和对象键')
  const requiredPrefix = `courses/${selected.value.id}/`
  if (!resourceForm.objectKey.startsWith(requiredPrefix) || resourceForm.objectKey.includes('..') || resourceForm.objectKey.includes('\\')) {
    return ElMessage.warning(`外部对象标识必须以 ${requiredPrefix} 开头`)
  }
  try {
    resources.value.unshift(await courseApi.createResource(selected.value.id, {
      ...resourceForm,
      chapterId: resourceForm.chapterId || undefined,
      contentType: resourceForm.contentType || undefined,
    }))
    resourceVisible.value = false
    Object.assign(resourceForm, { name: '', description: '', resourceType: 'LINK', objectKey: '', contentType: '', chapterId: '' })
    ElMessage.success('外部对象元数据已登记；此操作未上传或校验文件')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '资源登记失败') }
}

async function uploadResource(files: FileList | null) {
  const file = files?.item(0)
  if (!selected.value || !file) return
  resourceUploading.value = true
  try {
    resources.value.unshift(await courseApi.uploadResource(selected.value.id, file))
    ElMessage.success('课程原始资料已上传')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '资源上传失败') }
  finally { resourceUploading.value = false }
}

async function downloadResource(resource: CourseResource) {
  if (!selected.value) return
  try { await courseApi.downloadResource(selected.value.id, resource) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '资源下载失败') }
}

async function deleteResource(resource: CourseResource) {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(`确定撤下“${resource.name}”吗？现有对象保留以便审计。`, '撤下资源', { type: 'warning' })
    await courseApi.deleteResource(selected.value.id, resource.id)
    resources.value = resources.value.filter((item) => item.id !== resource.id)
    ElMessage.success('资源已撤下')
  } catch (error) { if (!isDialogDismissal(error)) ElMessage.error(error instanceof Error ? error.message : '撤下失败') }
}

async function uploadDocument(files: FileList | null) {
  const file = files?.item(0)
  if (!selected.value || !file) return
  const targetCourseId = selected.value.id
  documentUploading.value = true
  try {
    const result = await courseApi.uploadDocument(targetCourseId, file)
    if (!componentMounted || courseId.value !== targetCourseId) return
    const index = documents.value.findIndex((item) => item.id === result.document.id)
    if (index >= 0) documents.value[index] = result.document
    else documents.value.unshift(result.document)
    if (result.job) {
      documentJobs[result.document.id] = result.job
      void monitorDocumentJob(result.document.id, result.job.id, targetCourseId, detailGeneration)
    }
    ElMessage.success(result.duplicate ? '相同文档已存在，已复用摄取任务' : '文档已上传并进入摄取队列')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '文档上传失败') }
  finally { documentUploading.value = false }
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
    await ElMessageBox.confirm(`确定删除“${document.name}”及其向量索引吗？`, '删除知识文档', { type: 'warning' })
    await courseApi.deleteDocument(selected.value.id, document.id)
    documents.value = documents.value.filter((item) => item.id !== document.id)
  } catch (error) { if (!isDialogDismissal(error)) ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}

async function downloadDocument(document: KnowledgeDocument) {
  if (!selected.value) return
  try { await courseApi.downloadDocument(selected.value.id, document) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '下载失败') }
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

async function createChapter() {
  if (!selected.value || !chapterForm.title.trim()) return
  try {
    const value = editingChapterId.value
      ? await courseApi.updateChapter(selected.value.id, editingChapterId.value, { ...chapterForm })
      : await courseApi.createChapter(selected.value.id, { ...chapterForm })
    const index = chapters.value.findIndex((item) => item.id === value.id)
    if (index >= 0) chapters.value[index] = value
    else chapters.value.push(value)
    chapters.value.sort((a, b) => a.sortOrder - b.sortOrder)
    chapterVisible.value = false
    editingChapterId.value = null
    Object.assign(chapterForm, { title: '', description: '', sortOrder: chapters.value.length + 1, parentId: undefined })
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '章节创建失败') }
}

function editChapter(chapter: CourseChapter) {
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
  if (!selected.value || (!editingClassId.value && !classForm.code.trim()) || !classForm.name.trim()) return ElMessage.warning('请填写教学班代码和名称')
  try {
    const input = {
      code: classForm.code.trim(),
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
          <el-tab-pane label="课程资源" name="resources">
            <div class="tab-toolbar"><p>原始课程资料可直接上传和下载，不依赖 AI 摄取。</p><div class="button-row"><el-button @click="loadDetails(selected.id)">刷新</el-button><template v-if="canManageSelected"><label class="upload-button" :class="{ disabled: resourceUploading }"><input type="file" :disabled="resourceUploading" @change="uploadResource(($event.target as HTMLInputElement).files)" />{{ resourceUploading ? '上传中…' : '上传课程资料' }}</label><el-button @click="resourceVisible = true">登记外部对象</el-button><label class="upload-button" :class="{ disabled: documentUploading }"><input type="file" accept=".pdf,.ppt,.pptx,.docx,.md,.txt" :disabled="documentUploading" @change="uploadDocument(($event.target as HTMLInputElement).files)" />{{ documentUploading ? '上传中…' : '上传知识文档' }}</label></template></div></div>
            <el-table v-if="documents.length" :data="documents" class="document-table">
              <el-table-column prop="name" label="知识文档" min-width="200" />
              <el-table-column label="大小" width="100"><template #default="scope">{{ formatBytes(scope.row.sizeBytes) }}</template></el-table-column>
              <el-table-column label="摄取状态" width="130"><template #default="scope"><StatusBadge :status="documentTaskStatus(scope.row)" /></template></el-table-column>
              <el-table-column label="状态说明" min-width="180" show-overflow-tooltip><template #default="scope">{{ documentTaskError(scope.row) }}</template></el-table-column>
              <el-table-column label="操作" width="270"><template #default="scope"><el-button link @click="downloadDocument(scope.row)">下载</el-button><template v-if="canManageSelected"><el-button v-if="isDocumentJobCancelling(scope.row)" link disabled>取消中…</el-button><el-button v-else-if="mayCancelDocumentJob(scope.row)" link type="warning" @click="cancelDocumentJob(scope.row)">取消任务</el-button><el-button link @click="reindexDocument(scope.row)">重建索引</el-button><el-button link type="danger" @click="deleteDocument(scope.row)">删除</el-button></template></template></el-table-column>
            </el-table>
            <EmptyState v-else title="暂无知识文档" description="上传 PDF、PPT/PPTX、Word、Markdown 或 TXT，完成后即可用于课程问答。" />
            <h3 class="resource-heading">课程原始资料与外部对象</h3>
            <el-table v-if="resources.length" :data="resources">
              <el-table-column prop="name" label="名称" min-width="180" />
              <el-table-column prop="resourceType" label="类型" width="120" />
              <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
              <el-table-column prop="objectKey" label="外部对象标识" min-width="220" show-overflow-tooltip />
              <el-table-column label="操作" width="130"><template #default="scope"><el-button link @click="downloadResource(scope.row)">下载</el-button><el-button v-if="canManageSelected" link type="danger" @click="deleteResource(scope.row)">撤下</el-button></template></el-table-column>
            </el-table>
            <p v-else class="muted">暂无课程原始资料。</p>
          </el-tab-pane>
          <el-tab-pane label="章节" name="chapters">
            <div class="tab-toolbar"><p>章节用于组织资源、知识点和问答引用。</p><el-button v-if="canManageSelected" @click="chapterVisible = true">添加章节</el-button></div>
            <el-timeline v-if="chapters.length"><el-timeline-item v-for="chapter in chapters" :key="chapter.id" :timestamp="`排序 ${chapter.sortOrder}`"><strong>{{ chapter.title }}</strong><p class="muted">{{ chapter.description }}</p><div v-if="canManageSelected"><el-button link @click="editChapter(chapter)">编辑</el-button><el-button link type="danger" @click="deleteChapter(chapter)">删除</el-button></div></el-timeline-item></el-timeline>
            <EmptyState v-else title="暂无章节" />
          </el-tab-pane>
          <el-tab-pane label="知识点" name="knowledge">
            <div class="tab-toolbar"><p>知识点将用于作业关联和教学分析。</p><el-button v-if="canManageSelected" @click="pointVisible = true">添加知识点</el-button></div>
            <div v-if="knowledgePoints.length" class="tag-cloud"><span v-for="point in knowledgePoints" :key="point.id"><el-tag effect="plain" size="large">{{ point.title }}</el-tag><template v-if="canManageSelected"><el-button link @click="editPoint(point)">编辑</el-button><el-button link type="danger" @click="deletePoint(point)">删除</el-button></template></span></div>
            <EmptyState v-else title="暂无知识点" />
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
    <el-dialog v-model="resourceVisible" title="登记外部对象元数据" width="min(560px, 94vw)">
      <el-alert title="此操作只登记一个已存在的外部对象标识，不会上传文件，也不会验证对象是否存在。普通课程资料请使用“上传知识文档”。" type="warning" :closable="false" show-icon />
      <el-form label-position="top" class="dialog-form"><div class="form-grid"><el-form-item label="显示名称"><el-input v-model="resourceForm.name" /></el-form-item><el-form-item label="外部引用类型"><el-select v-model="resourceForm.resourceType"><el-option label="链接引用" value="LINK" /><el-option label="视频引用" value="VIDEO" /><el-option label="其他外部对象" value="OTHER" /></el-select></el-form-item></div><el-form-item label="外部对象标识（非上传地址）"><el-input v-model="resourceForm.objectKey" :placeholder="selected ? `courses/${selected.id}/external/...` : 'courses/{courseId}/external/...'" /></el-form-item><el-form-item label="说明"><el-input v-model="resourceForm.description" type="textarea" /></el-form-item><el-form-item label="章节"><el-select v-model="resourceForm.chapterId" clearable><el-option v-for="chapter in chapters" :key="chapter.id" :label="chapter.title" :value="chapter.id" /></el-select></el-form-item></el-form>
      <template #footer><el-button @click="resourceVisible = false">取消</el-button><el-button type="primary" @click="createResource">仅登记元数据</el-button></template>
    </el-dialog>
    <el-dialog v-model="chapterVisible" :title="editingChapterId ? '编辑章节' : '添加章节'" width="min(480px, 94vw)"><el-form label-position="top"><el-form-item label="章节名称"><el-input v-model="chapterForm.title" /></el-form-item><el-form-item label="顺序"><el-input-number v-model="chapterForm.sortOrder" :min="1" /></el-form-item><el-form-item label="说明"><el-input v-model="chapterForm.description" type="textarea" /></el-form-item></el-form><template #footer><el-button type="primary" @click="createChapter">保存</el-button></template></el-dialog>
    <el-dialog v-model="pointVisible" :title="editingPointId ? '编辑知识点' : '添加知识点'" width="min(480px, 94vw)"><el-form label-position="top"><el-form-item label="知识点名称"><el-input v-model="pointForm.title" /></el-form-item><el-form-item label="关联章节"><el-select v-model="pointForm.chapterId" clearable><el-option v-for="chapter in chapters" :key="chapter.id" :label="chapter.title" :value="chapter.id" /></el-select></el-form-item><el-form-item label="顺序"><el-input-number v-model="pointForm.sortOrder" :min="1" /></el-form-item><el-form-item label="说明"><el-input v-model="pointForm.description" type="textarea" /></el-form-item></el-form><template #footer><el-button type="primary" @click="createPoint">保存</el-button></template></el-dialog>
    <el-dialog v-model="classVisible" :title="editingClassId ? '修改教学班' : '创建教学班'" width="min(520px, 94vw)">
      <el-form label-position="top"><div class="form-grid"><el-form-item label="教学班代码"><el-input v-model="classForm.code" :disabled="Boolean(editingClassId)" placeholder="SE-01" /></el-form-item><el-form-item label="教学班名称"><el-input v-model="classForm.name" placeholder="软件工程 1 班" /></el-form-item></div><div class="form-grid"><el-form-item label="容量（可选）"><el-input-number v-model="classForm.capacity" :min="1" :max="10000" controls-position="right" style="width:100%" /></el-form-item><el-form-item label="主教学班"><el-switch v-model="classForm.primaryClass" active-text="是" inactive-text="否" /></el-form-item></div></el-form>
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
