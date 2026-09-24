<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { reviewApi } from '@/api/reviews'
import { assignmentApi } from '@/api/assignments'
import { courseApi } from '@/api/courses'
import { jobApi } from '@/api/jobs'
import { useCourseStore } from '@/stores/courses'
import type { AssignmentSummary, KnowledgeDocument, ReviewJob, ReviewReport, ReviewType, TeacherSubmission } from '@/types/domain'

const courseStore = useCourseStore()
const type = ref<ReviewType>('DOCUMENT')
const jobs = ref<ReviewJob[]>([])
const report = ref<ReviewReport | null>(null)
const selectedJobId = ref<string | null>(null)
const loading = ref(false)
const submitting = ref(false)
const jobPage = ref(0)
const jobSize = 20
const jobTotal = ref(0)
const documents = ref<KnowledgeDocument[]>([])
const selectedDocumentId = ref('')
const documentType = ref('SRS')
const submissionId = ref('')
const reviewAssignments = ref<AssignmentSummary[]>([])
const selectedAssignmentId = ref('')
const teacherSubmissions = ref<TeacherSubmission[]>([])
const selectedCodeAttachment = ref('')
const gradeVisible = ref(false)
const gradeForm = reactive({ score: 0, feedback: '', reason: '' })
const gradeMaximum = ref(100)
const gradeRubricItems = ref<Array<{ rubricItemId: string; title: string; score: number; suggestedScore: number; maxScore?: number; feedback: string }>>([])
let pollTimer: ReturnType<typeof setInterval> | null = null
let courseGeneration = 0
let jobsRequestGeneration = 0
let reportRequestGeneration = 0
let submissionRequestGeneration = 0

const courseId = computed(() => courseStore.selectedCourseId)
const selectedJob = computed(() => jobs.value.find((job) => job.id === selectedJobId.value))
const canConfirmGrade = computed(() => courseStore.selectedCourse?.role === 'TEACHER')
const submittedOptions = computed(() => teacherSubmissions.value.filter((item) => item.submission.status !== 'DRAFT'))
const codeAttachmentOptions = computed(() => submittedOptions.value.flatMap((item) =>
  item.submission.answers.flatMap((answer) => answer.attachmentObjectKey ? [{
    value: `${item.submission.id}:${answer.questionId}:${answer.attachmentObjectKey}`,
    submissionId: item.submission.id,
    objectKey: answer.attachmentObjectKey,
    label: `${item.studentName} · 第 ${item.submission.attemptNumber} 次 · ${answer.attachmentFileName || answer.attachmentObjectKey.split('/').pop() || '源码附件'}`,
  }] : []),
))

async function loadJobs(silent = false) {
  const requestedCourseId = courseId.value
  const requestedType = type.value
  const requestedPage = jobPage.value
  const courseVersion = courseGeneration
  const requestVersion = ++jobsRequestGeneration
  if (!requestedCourseId) {
    jobs.value = []
    jobTotal.value = 0
    return
  }
  if (!silent) loading.value = true
  try {
    const result = await reviewApi.list(requestedCourseId, requestedType, requestedPage, jobSize)
    if (courseVersion !== courseGeneration || requestVersion !== jobsRequestGeneration
      || requestedCourseId !== courseId.value || requestedType !== type.value) return
    jobs.value = result.items
    jobTotal.value = result.total
    if (selectedJobId.value) {
      const refreshed = jobs.value.find((job) => job.id === selectedJobId.value)
      if (refreshed?.status === 'COMPLETED' && !report.value) await loadReport(refreshed)
    }
  } catch (error) {
    if (!silent && courseVersion === courseGeneration && requestVersion === jobsRequestGeneration) {
      ElMessage.error(error instanceof Error ? error.message : '评审任务加载失败')
    }
  }
  finally {
    if (courseVersion === courseGeneration && requestVersion === jobsRequestGeneration) loading.value = false
  }
}

async function loadReport(job: ReviewJob) {
  const requestedCourseId = courseId.value
  const courseVersion = courseGeneration
  const requestVersion = ++reportRequestGeneration
  if (!requestedCourseId) return
  loading.value = true
  try {
    const result = await reviewApi.report(requestedCourseId, job)
    if (courseVersion === courseGeneration && requestVersion === reportRequestGeneration
      && selectedJobId.value === job.id && requestedCourseId === courseId.value) report.value = result
  }
  catch (error) {
    if (courseVersion === courseGeneration && requestVersion === reportRequestGeneration) {
      ElMessage.error(error instanceof Error ? error.message : '报告加载失败')
    }
  }
  finally {
    if (courseVersion === courseGeneration && requestVersion === reportRequestGeneration) loading.value = false
  }
}

async function selectJob(job: ReviewJob) {
  reportRequestGeneration += 1
  selectedJobId.value = job.id
  report.value = null
  if (job.status === 'COMPLETED') await loadReport(job)
}

function changeJobPage(page: number) {
  jobPage.value = Math.max(page - 1, 0)
  selectedJobId.value = null
  report.value = null
  void loadJobs()
}

async function startDocumentReview() {
  if (!courseId.value || !selectedDocumentId.value) return ElMessage.warning('请选择课程知识文档')
  await createJob(() => reviewApi.createDocument(courseId.value!, selectedDocumentId.value, documentType.value))
}

async function startCodeReview() {
  if (!courseId.value || !selectedCodeAttachment.value) return ElMessage.warning('请选择已提交的 ZIP 源码附件')
  const attachment = codeAttachmentOptions.value.find((item) => item.value === selectedCodeAttachment.value)
  if (!attachment) return ElMessage.warning('源码附件已失效，请重新选择')
  await createJob(() => reviewApi.createCode(courseId.value!, attachment.submissionId, attachment.objectKey))
}

async function startAssignmentReview() {
  if (!courseId.value || !submissionId.value.trim()) return ElMessage.warning('请输入提交记录 ID')
  await createJob(() => reviewApi.createAssignment(courseId.value!, submissionId.value.trim()))
}

async function createJob(factory: () => Promise<ReviewJob>) {
  const courseVersion = courseGeneration
  submitting.value = true
  try {
    const job = await factory()
    if (courseVersion !== courseGeneration) return
    jobPage.value = 0
    jobs.value.unshift(job)
    jobTotal.value += 1
    selectedJobId.value = job.id
    report.value = null
    ElMessage.success('评审任务已进入队列')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '任务创建失败') }
  finally { if (courseVersion === courseGeneration) submitting.value = false }
}

async function retrySelected() {
  if (!courseId.value || !selectedJob.value) return
  const requestedCourseId = courseId.value
  const reviewJobId = selectedJob.value.id
  const courseVersion = courseGeneration
  submitting.value = true
  try {
    const retried = await reviewApi.retry(requestedCourseId, reviewJobId)
    if (courseVersion !== courseGeneration || requestedCourseId !== courseId.value) return
    const index = jobs.value.findIndex((item) => item.id === retried.id)
    if (index >= 0) jobs.value[index] = retried
    report.value = null
    ElMessage.success('评审任务已重新入队')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '重试失败') }
  finally { if (courseVersion === courseGeneration) submitting.value = false }
}

async function cancelSelected() {
  const job = selectedJob.value
  if (!job?.asyncJobId || !['QUEUED', 'PROCESSING', 'RUNNING'].includes(job.status)) return
  try {
    await ElMessageBox.confirm(`确定取消“${job.subjectName}”吗？`, '取消评审', { type: 'warning' })
    await jobApi.cancel(job.asyncJobId)
    await loadJobs(true)
    ElMessage.success('已请求取消评审任务')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '取消失败')
    }
  }
}

async function exportReport() {
  if (!courseId.value || !report.value) return
  try { await reviewApi.exportReport(courseId.value, report.value.id) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '报告导出失败') }
}

async function openGradeDialog() {
  const courseVersion = courseGeneration
  const reviewJobId = selectedJob.value?.id
  gradeMaximum.value = 100
  gradeForm.score = report.value?.scoreSuggestion || 0
  gradeForm.feedback = report.value?.summary || ''
  gradeForm.reason = ''
  let definitions = new Map<string, { title: string; maxScore: number }>()
  if (selectedJob.value?.assignmentId) {
    try {
      const rubric = await assignmentApi.rubric(selectedJob.value.assignmentId)
      gradeMaximum.value = rubric?.totalScore || 100
      definitions = new Map((rubric?.items || []).map((item) => [item.id, { title: item.title, maxScore: item.maxScore }]))
    } catch (error) {
      ElMessage.warning(error instanceof Error ? error.message : '无法加载 Rubric 上限')
    }
  }
  if (courseVersion !== courseGeneration || selectedJob.value?.id !== reviewJobId) return
  gradeRubricItems.value = (report.value?.rubricItems || []).map((item) => ({
    rubricItemId: item.rubricItemId,
    title: definitions.get(item.rubricItemId)?.title || item.title,
    score: item.suggestedScore,
    suggestedScore: item.suggestedScore,
    maxScore: definitions.get(item.rubricItemId)?.maxScore || item.maxScore,
    feedback: item.feedback || '',
  }))
  gradeVisible.value = true
}

function syncGradeTotal() {
  if (gradeRubricItems.value.length) {
    gradeForm.score = gradeRubricItems.value.reduce((sum, item) => sum + item.score, 0)
  }
}

async function confirmGrade() {
  if (!selectedJob.value?.submissionId) return ElMessage.warning('评审任务未关联提交记录')
  const courseVersion = courseGeneration
  const reviewJobId = selectedJob.value.id
  const submissionId = selectedJob.value.submissionId
  const totalChanged = report.value?.scoreSuggestion !== undefined && gradeForm.score !== report.value.scoreSuggestion
  const itemChanged = gradeRubricItems.value.some((item) => item.score !== item.suggestedScore)
  if ((totalChanged || itemChanged) && !gradeForm.reason.trim()) {
    return ElMessage.warning('覆盖 AI 建议总分或分项分数时必须填写修改理由')
  }
  try {
    await reviewApi.confirmGrade(
      submissionId,
      gradeForm.score,
      gradeForm.feedback,
      gradeForm.reason || undefined,
      gradeRubricItems.value.map((item) => ({ rubricItemId: item.rubricItemId, score: item.score, feedback: item.feedback })),
    )
    if (courseVersion !== courseGeneration || selectedJob.value?.id !== reviewJobId) return
    gradeVisible.value = false
    ElMessage.success('最终成绩已由教师确认')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '成绩确认失败') }
}

async function loadDocuments() {
  const requestedCourseId = courseId.value
  const courseVersion = courseGeneration
  if (!requestedCourseId) {
    documents.value = []
    selectedDocumentId.value = ''
    return
  }
  try {
    const result = await courseApi.documents(requestedCourseId)
    if (courseVersion !== courseGeneration || requestedCourseId !== courseId.value) return
    documents.value = result
    if (!documents.value.some((item) => item.id === selectedDocumentId.value)) {
      selectedDocumentId.value = documents.value[0]?.id || ''
    }
  } catch (error) {
    if (courseVersion === courseGeneration) ElMessage.error(error instanceof Error ? error.message : '课程文档加载失败')
  }
}

async function loadAssignments() {
  reviewAssignments.value = []
  selectedAssignmentId.value = ''
  teacherSubmissions.value = []
  submissionId.value = ''
  selectedCodeAttachment.value = ''
  const requestedCourseId = courseId.value
  const courseVersion = courseGeneration
  if (!requestedCourseId) return
  try {
    const result = await assignmentApi.list(requestedCourseId)
    if (courseVersion !== courseGeneration || requestedCourseId !== courseId.value) return
    reviewAssignments.value = result.items
    selectedAssignmentId.value = reviewAssignments.value[0]?.id || ''
  } catch (error) {
    if (courseVersion === courseGeneration) ElMessage.error(error instanceof Error ? error.message : '作业列表加载失败')
  }
}

async function loadSubmissions() {
  teacherSubmissions.value = []
  submissionId.value = ''
  selectedCodeAttachment.value = ''
  const assignmentId = selectedAssignmentId.value
  const courseVersion = courseGeneration
  const requestVersion = ++submissionRequestGeneration
  if (!assignmentId) return
  try {
    const result = await assignmentApi.submissions(assignmentId)
    if (courseVersion !== courseGeneration || requestVersion !== submissionRequestGeneration
      || assignmentId !== selectedAssignmentId.value) return
    teacherSubmissions.value = result
    submissionId.value = submittedOptions.value[0]?.submission.id || ''
    selectedCodeAttachment.value = codeAttachmentOptions.value[0]?.value || ''
  } catch (error) {
    if (courseVersion === courseGeneration && requestVersion === submissionRequestGeneration) {
      ElMessage.error(error instanceof Error ? error.message : '提交记录加载失败')
    }
  }
}

function activateCourse() {
  courseGeneration += 1
  jobsRequestGeneration += 1
  reportRequestGeneration += 1
  submissionRequestGeneration += 1
  selectedJobId.value = null
  report.value = null
  jobs.value = []
  jobPage.value = 0
  jobTotal.value = 0
  documents.value = []
  reviewAssignments.value = []
  selectedAssignmentId.value = ''
  teacherSubmissions.value = []
  loading.value = false
  submitting.value = false
  void loadDocuments()
  void loadAssignments()
  void loadJobs()
}

watch(courseId, activateCourse)
watch(type, () => {
  jobsRequestGeneration += 1
  reportRequestGeneration += 1
  selectedJobId.value = null
  report.value = null
  jobPage.value = 0
  void loadJobs()
})
watch(selectedAssignmentId, loadSubmissions)
onMounted(() => {
  activateCourse()
  pollTimer = setInterval(() => {
    if (jobs.value.some((job) => ['QUEUED', 'RUNNING', 'PROCESSING'].includes(job.status))) void loadJobs(true)
  }, 5000)
})
onBeforeUnmount(() => {
  courseGeneration += 1
  jobsRequestGeneration += 1
  reportRequestGeneration += 1
  submissionRequestGeneration += 1
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <div>
    <PageHeader title="智能评审" description="文档、作业与代码评审均为异步任务；AI 分数只是建议，最终成绩必须由教师确认。">
      <el-button @click="loadJobs()">刷新任务</el-button>
    </PageHeader>

    <el-tabs v-model="type" class="review-type-tabs">
      <el-tab-pane label="文档 Review" name="DOCUMENT" />
      <el-tab-pane label="作业 Review" name="ASSIGNMENT" />
      <el-tab-pane label="代码 Review" name="CODE" />
    </el-tabs>

    <section class="review-layout">
      <div class="stack">
        <section class="panel">
          <div class="panel__header"><h2>发起 {{ type }} 评审</h2></div>
          <div class="panel__body stack">
            <template v-if="type === 'DOCUMENT'">
              <el-select v-model="documentType"><el-option label="需求规格说明 SRS" value="SRS" /><el-option label="设计说明" value="DESIGN" /><el-option label="测试报告" value="TEST_REPORT" /><el-option label="README" value="README" /><el-option label="API 文档" value="API" /></el-select>
              <el-select v-model="selectedDocumentId" filterable placeholder="选择已上传的课程知识文档"><el-option v-for="document in documents" :key="document.id" :label="`${document.name} · ${document.status}`" :value="document.id" /></el-select>
              <el-alert v-if="!documents.length" title="请先在“我的课程”上传待评审文档" type="info" :closable="false" show-icon />
              <el-button type="primary" :loading="submitting" @click="startDocumentReview">进入评审队列</el-button>
            </template>
            <template v-else-if="type === 'CODE'">
              <el-alert title="源码必须是该提交已上传的 ZIP 附件；服务端安全校验后由 SonarQube 异步扫描，不会执行学生源码。" type="warning" :closable="false" show-icon />
              <el-select v-model="selectedAssignmentId" filterable placeholder="选择作业"><el-option v-for="item in reviewAssignments" :key="item.id" :label="item.title" :value="item.id" /></el-select>
              <el-select v-model="selectedCodeAttachment" filterable placeholder="选择已提交的 ZIP 附件"><el-option v-for="item in codeAttachmentOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
              <el-alert v-if="selectedAssignmentId && !codeAttachmentOptions.length" title="该作业暂无可评审的源码 ZIP 附件" type="info" :closable="false" />
              <el-button type="primary" :loading="submitting" @click="startCodeReview">进入 SonarQube 评审队列</el-button>
            </template>
            <template v-else>
              <el-alert title="AI 将按 Rubric 逐项给出证据和建议分，不会直接发布成绩。" type="info" :closable="false" show-icon />
              <el-select v-model="selectedAssignmentId" filterable placeholder="选择作业"><el-option v-for="item in reviewAssignments" :key="item.id" :label="item.title" :value="item.id" /></el-select>
              <el-select v-model="submissionId" filterable placeholder="选择学生提交"><el-option v-for="item in submittedOptions" :key="item.submission.id" :label="`${item.studentName} · 第 ${item.submission.attemptNumber} 次`" :value="item.submission.id" /></el-select>
              <el-button type="primary" :loading="submitting" @click="startAssignmentReview">生成 AI 初评</el-button>
            </template>
          </div>
        </section>

        <section class="panel">
          <div class="panel__header"><h2>任务记录</h2><span class="muted">自动刷新运行中任务</span></div>
          <div v-if="jobs.length" class="job-list" v-loading="loading">
            <button v-for="job in jobs" :key="job.id" type="button" :class="{ active: job.id === selectedJobId }" @click="selectJob(job)"><div><strong>{{ job.subjectName }}</strong><StatusBadge :status="job.status" /></div><small>{{ new Date(job.createdAt).toLocaleString() }}</small><p v-if="job.errorMessage" class="danger-text">{{ job.errorMessage }}</p></button>
          </div>
          <EmptyState v-else title="暂无评审任务" />
          <el-pagination
            v-if="jobTotal > jobSize"
            class="job-pagination"
            layout="prev, pager, next, total"
            :current-page="jobPage + 1"
            :page-size="jobSize"
            :total="jobTotal"
            @current-change="changeJobPage"
          />
        </section>
      </div>

      <section class="panel report-panel">
        <div class="panel__header"><div><h2>结构化报告</h2><span v-if="selectedJob" class="muted">{{ selectedJob.subjectName }}</span></div><div class="button-row"><el-button v-if="selectedJob && ['QUEUED', 'PROCESSING', 'RUNNING'].includes(selectedJob.status)" type="warning" plain @click="cancelSelected">取消任务</el-button><el-button v-if="selectedJob && ['FAILED', 'CANCELLED'].includes(selectedJob.status)" :loading="submitting" @click="retrySelected">重试任务</el-button><el-button v-if="report" @click="exportReport">导出 JSON</el-button><el-button v-if="canConfirmGrade && report?.scoreSuggestion !== undefined && selectedJob?.type === 'ASSIGNMENT'" type="primary" @click="openGradeDialog">教师确认成绩</el-button></div></div>
        <div v-if="report" class="panel__body report-content">
          <div v-if="report.scoreSuggestion !== undefined" class="score-suggestion"><span>AI 建议分</span><strong>{{ report.scoreSuggestion }}</strong><small>非最终成绩</small></div>
          <h3>评审摘要</h3><p>{{ report.summary }}</p>
          <template v-for="section in ([['完整性', report.completeness], ['一致性', report.consistency], ['可验证性', report.testability], ['清晰度', report.clarity], ['改进建议', report.suggestions]] as const)" :key="section[0]"><div v-if="section[1]?.length" class="report-section"><h3>{{ section[0] }}</h3><ul><li v-for="item in section[1]" :key="item">{{ item }}</li></ul></div></template>
          <div v-if="report.rubricItems?.length" class="rubric-list"><h3>Rubric 分项</h3><article v-for="item in report.rubricItems" :key="item.rubricItemId"><div><strong>{{ item.title }}</strong><span>{{ item.suggestedScore }}<template v-if="item.maxScore !== undefined"> / {{ item.maxScore }}</template></span></div><p>{{ item.evidence }}</p><small>{{ item.feedback }}</small></article></div>
        </div>
        <EmptyState v-else :title="selectedJob?.status === 'COMPLETED' ? '选择或加载报告' : '报告尚未生成'" :description="selectedJob ? '任务完成后将在这里展示结构化结果。' : '从左侧选择一个任务。'" />
      </section>
    </section>

    <el-dialog v-model="gradeVisible" title="教师确认最终成绩" width="min(640px, 94vw)"><el-alert title="确认后才会生成 Final Grade；覆盖 AI 建议时请填写理由。" type="warning" :closable="false" /><el-form label-position="top" class="grade-form"><el-form-item :label="`最终分数（满分 ${gradeMaximum}）`"><el-input-number v-model="gradeForm.score" :min="0" :max="gradeMaximum" :disabled="gradeRubricItems.length > 0" /></el-form-item><div v-if="gradeRubricItems.length" class="grade-rubric-list"><strong>Rubric 分项确认（总分自动汇总）</strong><article v-for="item in gradeRubricItems" :key="item.rubricItemId"><span>{{ item.title }}</span><el-input-number v-model="item.score" :min="0" :max="item.maxScore ?? 999" @change="syncGradeTotal" /><el-input v-model="item.feedback" placeholder="分项反馈" /></article></div><el-form-item label="给学生的反馈"><el-input v-model="gradeForm.feedback" type="textarea" :rows="5" /></el-form-item><el-form-item label="修改理由"><el-input v-model="gradeForm.reason" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="gradeVisible = false">取消</el-button><el-button type="primary" @click="confirmGrade">确认并发布</el-button></template></el-dialog>
  </div>
</template>

<style scoped>
.review-type-tabs { margin-bottom: 14px; }.review-layout { display: grid; grid-template-columns: minmax(320px, .85fr) minmax(0, 1.4fr); gap: 20px; align-items: start; }
.file-picker { min-height: 120px; display: grid; place-items: center; align-content: center; gap: 5px; border: 1px dashed #aab5c9; border-radius: 10px; background: #f8f9fc; color: #5d687b; cursor: pointer; }.file-picker input { position: absolute; width: 1px; height: 1px; opacity: 0; }.file-picker span { color: #8a94a7; font-size: 11px; }
.job-list { display: grid; max-height: 430px; overflow-y: auto; padding: 8px; }.job-list button { border: 0; border-radius: 9px; padding: 12px; background: transparent; text-align: left; }.job-list button:hover,.job-list button.active { background: #eef1fb; }.job-list button > div { display: flex; justify-content: space-between; gap: 8px; }.job-list small { color: var(--muted); }.job-list p { margin: 6px 0 0; font-size: 11px; }.job-pagination { justify-content: center; border-top: 1px solid var(--line); padding: 12px; }
.report-panel { min-height: 620px; }.report-content { line-height: 1.7; }.report-content > h3,.report-section h3,.rubric-list h3 { margin: 23px 0 8px; font-size: 14px; }.report-content > p { white-space: pre-wrap; }.score-suggestion { display: flex; align-items: baseline; gap: 10px; border-radius: 10px; padding: 16px; background: #f0f3ff; }.score-suggestion strong { color: var(--brand); font-size: 34px; }.score-suggestion small { color: var(--muted); }
.report-section { border-top: 1px solid var(--line); }.report-section li { margin-bottom: 7px; }.rubric-list article { border: 1px solid var(--line); border-radius: 9px; margin-top: 9px; padding: 13px; }.rubric-list article > div { display: flex; justify-content: space-between; }.rubric-list article p { margin: 7px 0; color: #4e596b; }.rubric-list article small { color: var(--muted); }.grade-form { margin-top: 18px; }
.grade-rubric-list { display: grid; gap: 9px; margin-bottom: 18px; }.grade-rubric-list article { display: grid; grid-template-columns: minmax(130px, 1fr) auto minmax(180px, 1.4fr); align-items: center; gap: 9px; border: 1px solid var(--line); border-radius: 8px; padding: 9px; }
@media (max-width: 950px) { .review-layout { grid-template-columns: 1fr; } }
</style>
