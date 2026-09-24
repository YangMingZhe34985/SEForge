<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { assignmentApi } from '@/api/assignments'
import { ApiError } from '@/api/client'
import { courseApi } from '@/api/courses'
import { useCourseStore } from '@/stores/courses'
import type {
  AssignmentDetails,
  AssignmentQuestion,
  AssignmentQuestionInput,
  AssignmentRubric,
  AssignmentSummary,
  AssignmentStatus,
  CourseClass,
  KnowledgePoint,
  SubmissionAnswerInput,
  TutorAction,
  TutorPolicyConfig,
  TutorResponse,
  TeacherSubmission,
} from '@/types/domain'

type AnswerValue = SubmissionAnswerInput['answer']

const route = useRoute()
const courseStore = useCourseStore()
const courseId = computed(() => String(route.params.courseId))
const assignments = ref<AssignmentSummary[]>([])
const selectedId = ref<string | null>(null)
const detail = ref<AssignmentDetails | null>(null)
const loading = ref(false)
const saveState = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
const answers = reactive<Record<string, AnswerValue>>({})
const attachmentNames = reactive<Record<string, string>>({})
const uploadingQuestionId = ref('')
const tutorAction = ref<TutorAction>('HINT')
const tutorQuestionId = ref('')
const tutorResult = ref<TutorResponse | null>(null)
const tutorLoading = ref(false)
const createVisible = ref(false)
const questionVisible = ref(false)
const rubricItemVisible = ref(false)
const editingQuestionId = ref<string | null>(null)
const rubric = ref<AssignmentRubric | null>(null)
const teacherSubmissions = ref<TeacherSubmission[]>([])
const knowledgePoints = ref<KnowledgePoint[]>([])
const courseClasses = ref<CourseClass[]>([])
const createForm = reactive({ title: '', description: '', classId: '', availableAt: '', dueAt: '', maxAttempts: 1 })
const editForm = reactive({ title: '', description: '', availableAt: '', dueAt: '', maxAttempts: 1 })
const extensionVisible = ref(false)
const extensionForm = reactive({ studentId: '', studentName: '', dueAt: '' })
const questionForm = reactive({
  type: 'SHORT_ANSWER' as AssignmentQuestion['type'],
  prompt: '',
  optionsText: '',
  referenceAnswer: '',
  points: 10,
  orderIndex: 0,
  knowledgePointId: '',
})
const rubricForm = reactive({ title: '课程作业评分量表', totalScore: 100, status: 'DRAFT' as AssignmentRubric['status'] })
const rubricItemForm = reactive({ questionId: '', title: '', description: '', maxScore: 10, orderIndex: 0 })
const policyForm = reactive<TutorPolicyConfig>({
  allowFullSolutionBeforeSubmit: false,
  fullSolutionAfterSubmit: true,
  fullSolutionAfterDue: true,
  allowLateSubmission: false,
  enabledOperations: ['HINT', 'EXPLAIN', 'CHECK_REASONING', 'ANALYZE_ERROR', 'EVALUATE_DRAFT', 'FULL_SOLUTION'],
  dueAtOverrides: {},
})
let saveTimer: ReturnType<typeof setTimeout> | null = null
let courseGeneration = 0
let assignmentGeneration = 0

const canTeachCourse = computed(() => ['TEACHER', 'TA'].includes(courseStore.selectedCourse?.role || ''))
const canPublish = computed(() => courseStore.selectedCourse?.role === 'TEACHER')
const canEdit = computed(() => !canTeachCourse.value && detail.value?.status === 'PUBLISHED')
const isDraft = computed(() => detail.value?.status === 'DRAFT')

function toAnswerList(): SubmissionAnswerInput[] {
  return Object.entries(answers).map(([questionId, answer]) => ({ questionId, answer }))
}

function defaultAnswer(question: AssignmentQuestion): AnswerValue {
  if (question.type === 'MULTIPLE_CHOICE') return []
  if (question.type === 'TRUE_FALSE') return false
  return ''
}

async function loadList() {
  const requestedCourseId = courseId.value
  const courseVersion = ++courseGeneration
  assignmentGeneration += 1
  if (saveTimer) clearTimeout(saveTimer)
  assignments.value = []
  selectedId.value = null
  detail.value = null
  rubric.value = null
  teacherSubmissions.value = []
  knowledgePoints.value = []
  courseClasses.value = []
  tutorResult.value = null
  tutorLoading.value = false
  uploadingQuestionId.value = ''
  for (const key of Object.keys(answers)) delete answers[key]
  for (const key of Object.keys(attachmentNames)) delete attachmentNames[key]
  saveState.value = 'idle'
  loading.value = true
  try {
    const teaching = canTeachCourse.value
    const [page, classes] = await Promise.all([
      assignmentApi.list(requestedCourseId),
      teaching ? courseApi.classes(requestedCourseId) : Promise.resolve([]),
    ])
    if (courseVersion !== courseGeneration || requestedCourseId !== courseId.value) return
    assignments.value = page.items
    courseClasses.value = classes
    const first = assignments.value[0]
    if (first) await selectAssignment(first.id, requestedCourseId, courseVersion, teaching)
    else { selectedId.value = null; detail.value = null }
  } catch (error) {
    if (courseVersion === courseGeneration) ElMessage.error(error instanceof Error ? error.message : '作业加载失败')
  } finally {
    if (courseVersion === courseGeneration) loading.value = false
  }
}

async function selectAssignment(
  id: string,
  requestedCourseId = courseId.value,
  courseVersion = courseGeneration,
  teaching = canTeachCourse.value,
) {
  if (saveTimer) clearTimeout(saveTimer)
  const requestVersion = ++assignmentGeneration
  selectedId.value = id
  detail.value = null
  rubric.value = null
  teacherSubmissions.value = []
  knowledgePoints.value = []
  tutorResult.value = null
  loading.value = true
  try {
    const [assignment, submission] = await Promise.all([
      assignmentApi.get(id),
      teaching ? Promise.resolve(null) : assignmentApi.mySubmission(id),
    ])
    if (courseVersion !== courseGeneration || requestVersion !== assignmentGeneration
      || requestedCourseId !== courseId.value || selectedId.value !== id) return
    detail.value = assignment
    Object.assign(editForm, {
      title: assignment.title,
      description: assignment.description || '',
      availableAt: assignment.availableAt || '',
      dueAt: assignment.dueAt || '',
      maxAttempts: assignment.maxAttempts || 1,
    })
    Object.assign(policyForm, assignment.tutorPolicy || {
      allowFullSolutionBeforeSubmit: false,
      fullSolutionAfterSubmit: true,
      fullSolutionAfterDue: true,
      allowLateSubmission: false,
      enabledOperations: ['HINT', 'EXPLAIN', 'CHECK_REASONING', 'ANALYZE_ERROR', 'EVALUATE_DRAFT', 'FULL_SOLUTION'],
      dueAtOverrides: {},
    })
    for (const key of Object.keys(answers)) delete answers[key]
    for (const key of Object.keys(attachmentNames)) delete attachmentNames[key]
    for (const question of assignment.questions) answers[question.id] = defaultAnswer(question)
    for (const answer of submission?.answers || []) {
      answers[answer.questionId] = answer.answer
      if (answer.attachmentObjectKey) {
        attachmentNames[answer.questionId] = answer.attachmentFileName || answer.attachmentObjectKey.split('/').pop() || '已上传源码附件'
      }
    }
    tutorQuestionId.value = assignment.questions[0]?.id || ''
    saveState.value = submission ? 'saved' : 'idle'
    if (teaching) await loadTeacherData(id, requestedCourseId, courseVersion, requestVersion)
  } catch (error) {
    if (courseVersion === courseGeneration && requestVersion === assignmentGeneration) {
      ElMessage.error(error instanceof Error ? error.message : '作业详情加载失败')
    }
  } finally {
    if (courseVersion === courseGeneration && requestVersion === assignmentGeneration) loading.value = false
  }
}

async function loadTeacherData(
  assignmentId: string,
  requestedCourseId: string,
  courseVersion: number,
  requestVersion: number,
) {
  const [points, submissions] = await Promise.all([
    courseApi.knowledgePoints(requestedCourseId),
    assignmentApi.submissions(assignmentId),
  ])
  let loadedRubric: AssignmentRubric | null = null
  try {
    loadedRubric = await assignmentApi.rubric(assignmentId)
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) loadedRubric = null
    else throw error
  }
  if (courseVersion !== courseGeneration || requestVersion !== assignmentGeneration
    || requestedCourseId !== courseId.value || selectedId.value !== assignmentId) return
  knowledgePoints.value = points
  teacherSubmissions.value = submissions
  rubric.value = loadedRubric
  if (loadedRubric) Object.assign(rubricForm, {
    title: loadedRubric.title,
    totalScore: loadedRubric.totalScore,
    status: loadedRubric.status,
  })
}

function className(classId?: string): string {
  if (!classId) return '全部教学班'
  return courseClasses.value.find((item) => item.id === classId)?.name || `教学班 ${classId}`
}

function openExtension(row: TeacherSubmission) {
  extensionForm.studentId = row.studentId
  extensionForm.studentName = row.studentName
  extensionForm.dueAt = policyForm.dueAtOverrides?.[row.studentId] || detail.value?.dueAt || ''
  extensionVisible.value = true
}

async function saveExtension() {
  if (!selectedId.value || !extensionForm.studentId) return
  const assignmentId = selectedId.value
  const requestVersion = assignmentGeneration
  try {
    const policy = await assignmentApi.setExtension(
      assignmentId,
      extensionForm.studentId,
      extensionForm.dueAt || undefined,
    )
    if (requestVersion !== assignmentGeneration || selectedId.value !== assignmentId) return
    Object.assign(policyForm, policy)
    extensionVisible.value = false
    ElMessage.success(extensionForm.dueAt ? '个别延期已保存' : '个别延期已取消')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '个别延期保存失败')
  }
}

async function saveAssignment() {
  if (!selectedId.value || !detail.value) return
  const assignmentId = selectedId.value
  const requestVersion = assignmentGeneration
  try {
    const updated = await assignmentApi.update(assignmentId, {
      title: editForm.title,
      description: editForm.description,
      availableAt: editForm.availableAt || undefined,
      dueAt: editForm.dueAt || undefined,
      maxAttempts: editForm.maxAttempts,
    })
    if (requestVersion !== assignmentGeneration || selectedId.value !== assignmentId || !detail.value) return
    Object.assign(detail.value, updated)
    const summary = assignments.value.find((item) => item.id === assignmentId)
    if (summary) Object.assign(summary, updated)
    ElMessage.success('作业设置已保存')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '作业保存失败') }
}

async function transition(target: AssignmentStatus) {
  if (!selectedId.value) return
  const assignmentId = selectedId.value
  const requestVersion = assignmentGeneration
  try {
    const updated = await assignmentApi.transition(assignmentId, target)
    if (requestVersion !== assignmentGeneration || selectedId.value !== assignmentId) return
    detail.value = updated
    const summary = assignments.value.find((item) => item.id === assignmentId)
    if (summary) Object.assign(summary, updated)
    ElMessage.success(`作业状态已更新为 ${target}`)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '状态更新失败') }
}

function openQuestion(question?: AssignmentQuestion) {
  editingQuestionId.value = question?.id || null
  Object.assign(questionForm, {
    type: question?.type || 'SHORT_ANSWER',
    prompt: question?.prompt || '',
    optionsText: question?.options?.join('\n') || '',
    referenceAnswer: '',
    points: question?.points || 10,
    orderIndex: question?.orderIndex ?? detail.value?.questions.length ?? 0,
    knowledgePointId: question?.knowledgePointId || '',
  })
  questionVisible.value = true
}

async function saveQuestion() {
  if (!selectedId.value || !detail.value || !questionForm.prompt.trim()) return
  const assignmentId = selectedId.value
  const requestVersion = assignmentGeneration
  const choice = ['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(questionForm.type)
  const input: AssignmentQuestionInput = {
    type: questionForm.type,
    prompt: questionForm.prompt.trim(),
    options: choice ? questionForm.optionsText.split('\n').map((item) => item.trim()).filter(Boolean) : undefined,
    referenceAnswer: questionForm.referenceAnswer || undefined,
    points: questionForm.points,
    orderIndex: questionForm.orderIndex,
    knowledgePointId: questionForm.knowledgePointId || undefined,
    config: {},
  }
  try {
    const saved = editingQuestionId.value
      ? await assignmentApi.updateQuestion(assignmentId, editingQuestionId.value, input)
      : await assignmentApi.addQuestion(assignmentId, input)
    if (requestVersion !== assignmentGeneration || selectedId.value !== assignmentId || !detail.value) return
    const index = detail.value.questions.findIndex((item) => item.id === saved.id)
    if (index >= 0) detail.value.questions[index] = saved
    else detail.value.questions.push(saved)
    detail.value.questions.sort((a, b) => a.orderIndex - b.orderIndex)
    questionVisible.value = false
    ElMessage.success('题目已保存')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '题目保存失败') }
}

async function deleteQuestion(question: AssignmentQuestion) {
  if (!selectedId.value || !detail.value) return
  const assignmentId = selectedId.value
  const requestVersion = assignmentGeneration
  try {
    await ElMessageBox.confirm(`确定删除“${question.prompt.slice(0, 30)}”吗？`, '删除题目', { type: 'warning' })
    if (requestVersion !== assignmentGeneration || selectedId.value !== assignmentId) return
    await assignmentApi.deleteQuestion(assignmentId, question.id)
    if (requestVersion !== assignmentGeneration || selectedId.value !== assignmentId || !detail.value) return
    detail.value.questions = detail.value.questions.filter((item) => item.id !== question.id)
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}

async function saveTutorPolicy() {
  if (!selectedId.value) return
  const assignmentId = selectedId.value
  const requestVersion = assignmentGeneration
  try {
    const policy = await assignmentApi.updateTutorPolicy(assignmentId, { ...policyForm })
    if (requestVersion !== assignmentGeneration || selectedId.value !== assignmentId) return
    Object.assign(policyForm, policy)
    ElMessage.success('Tutor 策略已保存')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '策略保存失败') }
}

async function saveRubric() {
  if (!selectedId.value || !rubricForm.title.trim()) return
  const assignmentId = selectedId.value
  const requestVersion = assignmentGeneration
  try {
    const saved = await assignmentApi.saveRubric(assignmentId, { ...rubricForm })
    if (requestVersion !== assignmentGeneration || selectedId.value !== assignmentId) return
    rubric.value = saved
    ElMessage.success('Rubric 已保存')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : 'Rubric 保存失败') }
}

async function addRubricItem() {
  if (!selectedId.value || !rubricItemForm.title.trim()) return
  const assignmentId = selectedId.value
  const requestVersion = assignmentGeneration
  try {
    const item = await assignmentApi.addRubricItem(assignmentId, {
      ...rubricItemForm,
      questionId: rubricItemForm.questionId || undefined,
      criteria: {},
    })
    if (requestVersion !== assignmentGeneration || selectedId.value !== assignmentId) return
    if (rubric.value) rubric.value.items.push(item)
    rubricItemVisible.value = false
    Object.assign(rubricItemForm, { questionId: '', title: '', description: '', maxScore: 10, orderIndex: rubric.value?.items.length || 0 })
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : 'Rubric 分项保存失败') }
}

async function deleteRubricItem(itemId: string) {
  if (!selectedId.value || !rubric.value) return
  const assignmentId = selectedId.value
  const requestVersion = assignmentGeneration
  try {
    await assignmentApi.deleteRubricItem(assignmentId, itemId)
    if (requestVersion !== assignmentGeneration || selectedId.value !== assignmentId || !rubric.value) return
    rubric.value.items = rubric.value.items.filter((item) => item.id !== itemId)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}

function updateAnswer(questionId: string, value: AnswerValue) {
  answers[questionId] = value
  if (!canEdit.value || !selectedId.value) return
  saveState.value = 'idle'
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => void saveDraft(), 900)
}

async function saveDraft() {
  if (!selectedId.value || !canEdit.value) return
  const assignmentId = selectedId.value
  const requestVersion = assignmentGeneration
  saveState.value = 'saving'
  try {
    await assignmentApi.saveDraft(assignmentId, toAnswerList())
    if (requestVersion === assignmentGeneration && selectedId.value === assignmentId) saveState.value = 'saved'
  } catch (error) {
    if (requestVersion === assignmentGeneration && selectedId.value === assignmentId) {
      saveState.value = 'error'
      ElMessage.error(error instanceof Error ? error.message : '草稿保存失败')
    }
  }
}

async function submit() {
  if (!selectedId.value) return
  const assignmentId = selectedId.value
  const requestVersion = assignmentGeneration
  try {
    await ElMessageBox.confirm('提交后将计入一次作答次数，确定继续吗？', '提交作业', { type: 'warning' })
    if (requestVersion !== assignmentGeneration || selectedId.value !== assignmentId) return
    if (saveTimer) clearTimeout(saveTimer)
    await assignmentApi.submit(assignmentId, toAnswerList())
    if (requestVersion !== assignmentGeneration || selectedId.value !== assignmentId) return
    const summary = assignments.value.find((item) => item.id === assignmentId)
    if (summary) summary.submitted = true
    ElMessage.success('作业已提交')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '提交失败')
  }
}

async function uploadAttachment(questionId: string, files: FileList | null) {
  const file = files?.item(0)
  if (!file || !selectedId.value) return
  const assignmentId = selectedId.value
  const requestVersion = assignmentGeneration
  uploadingQuestionId.value = questionId
  try {
    const attachment = await assignmentApi.uploadAttachment(assignmentId, questionId, file)
    if (requestVersion === assignmentGeneration && selectedId.value === assignmentId) {
      attachmentNames[questionId] = attachment.fileName
      ElMessage.success('源码附件已安全上传')
    }
  } catch (error) {
    if (requestVersion === assignmentGeneration) ElMessage.error(error instanceof Error ? error.message : '源码附件上传失败')
  } finally {
    if (requestVersion === assignmentGeneration) uploadingQuestionId.value = ''
  }
}

async function askTutor() {
  if (!selectedId.value || !tutorQuestionId.value) return
  const assignmentId = selectedId.value
  const questionId = tutorQuestionId.value
  const requestVersion = assignmentGeneration
  tutorLoading.value = true
  tutorResult.value = null
  try {
    const result = await assignmentApi.tutor(assignmentId, questionId, tutorAction.value, answers[questionId] ?? '')
    if (requestVersion === assignmentGeneration && selectedId.value === assignmentId) tutorResult.value = result
  } catch (error) {
    if (requestVersion === assignmentGeneration) ElMessage.error(error instanceof Error ? error.message : 'Tutor 暂时不可用')
  } finally {
    if (requestVersion === assignmentGeneration) tutorLoading.value = false
  }
}

async function createAssignment() {
  if (!createForm.title.trim()) return ElMessage.warning('请输入作业标题')
  const requestedCourseId = courseId.value
  const courseVersion = courseGeneration
  try {
    const created = await assignmentApi.create(requestedCourseId, {
      ...createForm,
      classId: createForm.classId || undefined,
      availableAt: createForm.availableAt || undefined,
      dueAt: createForm.dueAt || undefined,
    })
    if (courseVersion !== courseGeneration || requestedCourseId !== courseId.value) return
    assignments.value.unshift(created)
    createVisible.value = false
    Object.assign(createForm, { title: '', description: '', classId: '', availableAt: '', dueAt: '', maxAttempts: 1 })
    await selectAssignment(created.id)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '作业创建失败') }
}

function answerText(questionId: string): string {
  const value = answers[questionId]
  return typeof value === 'string' ? value : ''
}

function answerArray(questionId: string): string[] {
  const value = answers[questionId]
  return Array.isArray(value) ? value : []
}

watch(courseId, () => { void loadList() })
onMounted(() => { void loadList() })
onBeforeUnmount(() => {
  courseGeneration += 1
  assignmentGeneration += 1
  if (saveTimer) clearTimeout(saveTimer)
})
</script>

<template>
  <div>
    <PageHeader title="作业与 AI Tutor" description="学生答案、Tutor 辅导记录和最终提交相互独立，自动保存不会覆盖正式提交。">
      <el-button v-if="canTeachCourse" type="primary" @click="createVisible = true">创建作业</el-button>
      <span v-else class="save-state" :class="`save-state--${saveState}`">{{ { idle: '等待保存', saving: '正在保存…', saved: '草稿已保存', error: '保存失败' }[saveState] }}</span>
    </PageHeader>

    <section class="assignment-layout" v-loading="loading">
      <aside class="assignment-list panel">
        <div class="panel__header"><h2>课程作业</h2><span class="muted">{{ assignments.length }}</span></div>
        <div v-if="assignments.length" class="assignment-list__items">
          <button v-for="item in assignments" :key="item.id" type="button" :class="{ active: item.id === selectedId }" @click="selectAssignment(item.id)">
            <div><strong>{{ item.title }}</strong><StatusBadge :status="item.status" /></div>
            <p>{{ item.description || '暂无说明' }}</p>
            <small>{{ item.dueAt ? `截止 ${new Date(item.dueAt).toLocaleString()}` : '无截止时间' }}<span v-if="item.submitted"> · 已提交</span></small>
          </button>
        </div>
        <EmptyState v-else title="暂无作业" />
      </aside>

      <div v-if="detail" class="assignment-detail stack">
        <section class="panel">
          <div class="panel__header"><div><h2>{{ detail.title }}</h2><div class="meta-row"><StatusBadge :status="detail.status" /><span>{{ detail.questions.length }} 题</span><span v-if="detail.dueAt">截止 {{ new Date(detail.dueAt).toLocaleString() }}</span></div></div><div class="button-row"><el-button v-if="isDraft && canTeachCourse" @click="openQuestion()">添加题目</el-button><el-button v-if="isDraft && canPublish" type="primary" @click="transition('PUBLISHED')">发布作业</el-button><el-button v-if="detail.status === 'PUBLISHED' && canPublish" type="warning" @click="transition('CLOSED')">关闭作业</el-button><el-button v-if="detail.status === 'CLOSED' && canPublish" @click="transition('ARCHIVED')">归档</el-button><el-button v-if="canEdit" type="primary" @click="submit">提交作业</el-button></div></div>
          <div class="panel__body"><p class="assignment-description">{{ detail.description }}</p><p v-if="canTeachCourse" class="muted">发布范围：{{ className(detail.classId) }}</p></div>
        </section>

        <section v-if="canTeachCourse" class="panel teacher-editor">
          <div class="panel__header"><div><h2>教师配置</h2><span class="muted">题目与评分设置仅在草稿阶段可编辑</span></div></div>
          <div class="panel__body stack">
            <el-form label-position="top"><div class="form-grid"><el-form-item label="标题"><el-input v-model="editForm.title" :disabled="!isDraft" /></el-form-item><el-form-item label="最大提交次数"><el-input-number v-model="editForm.maxAttempts" :min="1" :max="100" :disabled="!isDraft" /></el-form-item></div><el-form-item label="说明"><el-input v-model="editForm.description" type="textarea" :disabled="!isDraft" /></el-form-item><div class="form-grid"><el-form-item label="开放时间"><el-date-picker v-model="editForm.availableAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" clearable :disabled="!isDraft" style="width:100%" /></el-form-item><el-form-item label="截止时间"><el-date-picker v-model="editForm.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" clearable :disabled="!isDraft" style="width:100%" /></el-form-item></div><el-button v-if="isDraft" type="primary" @click="saveAssignment">保存作业设置</el-button></el-form>

            <div class="teacher-grid">
              <div class="subpanel"><div class="subpanel__header"><strong>Tutor 策略</strong><el-button v-if="canPublish" link type="primary" @click="saveTutorPolicy">保存策略</el-button></div><el-switch v-model="policyForm.allowFullSolutionBeforeSubmit" :disabled="!canPublish" active-text="提交前允许完整解析" /><el-switch v-model="policyForm.fullSolutionAfterSubmit" :disabled="!canPublish" active-text="提交后开放完整解析" /><el-switch v-model="policyForm.fullSolutionAfterDue" :disabled="!canPublish" active-text="截止后开放完整解析" /><el-switch v-model="policyForm.allowLateSubmission" :disabled="!canPublish" active-text="允许迟交" /></div>
              <div class="subpanel"><div class="subpanel__header"><strong>Rubric</strong><el-button v-if="isDraft" link type="primary" @click="rubricItemVisible = true" :disabled="!rubric">添加分项</el-button></div><div class="form-grid"><el-input v-model="rubricForm.title" :disabled="!isDraft" placeholder="Rubric 标题" /><el-input-number v-model="rubricForm.totalScore" :min="1" :disabled="!isDraft" /></div><el-select v-model="rubricForm.status" :disabled="!isDraft"><el-option label="草稿" value="DRAFT" /><el-option label="发布" value="PUBLISHED" /><el-option label="归档" value="ARCHIVED" /></el-select><el-button v-if="isDraft" @click="saveRubric">保存 Rubric</el-button><div v-if="rubric?.items.length" class="rubric-editor-list"><article v-for="item in rubric.items" :key="item.id"><span><strong>{{ item.title }}</strong><small>{{ item.maxScore }} 分</small></span><el-button v-if="isDraft" link type="danger" @click="deleteRubricItem(item.id)">删除</el-button></article></div></div>
            </div>

            <div class="subpanel"><div class="subpanel__header"><strong>学生提交</strong><span class="muted">{{ teacherSubmissions.length }} 条</span></div><el-table v-if="teacherSubmissions.length" :data="teacherSubmissions" size="small"><el-table-column prop="studentName" label="学生" /><el-table-column prop="submission.attemptNumber" label="次数" width="80" /><el-table-column prop="submission.status" label="状态" width="120" /><el-table-column label="提交时间" min-width="170"><template #default="scope">{{ scope.row.submission.submittedAt ? new Date(scope.row.submission.submittedAt).toLocaleString() : '—' }}</template></el-table-column><el-table-column label="个别延期" width="110"><template #default="scope"><el-button link type="primary" @click="openExtension(scope.row)">设置</el-button></template></el-table-column></el-table><p v-else class="muted">暂无正式提交。</p></div>
          </div>
        </section>

        <section v-for="(questionItem, index) in detail.questions" :key="questionItem.id" class="question-card panel">
          <div class="panel__header"><div class="question-title"><span>{{ index + 1 }}</span><div><h3>{{ questionItem.prompt }}</h3><small>{{ questionItem.type }} · {{ questionItem.points }} 分</small></div></div><div v-if="isDraft && canTeachCourse"><el-button link @click="openQuestion(questionItem)">编辑</el-button><el-button link type="danger" @click="deleteQuestion(questionItem)">删除</el-button></div></div>
          <div class="panel__body">
            <el-radio-group v-if="questionItem.type === 'SINGLE_CHOICE'" :model-value="answerText(questionItem.id)" :disabled="!canEdit" class="option-list" @update:model-value="updateAnswer(questionItem.id, String($event))"><el-radio v-for="option in questionItem.options" :key="option" :value="option">{{ option }}</el-radio></el-radio-group>
            <el-checkbox-group v-else-if="questionItem.type === 'MULTIPLE_CHOICE'" :model-value="answerArray(questionItem.id)" :disabled="!canEdit" class="option-list" @update:model-value="updateAnswer(questionItem.id, $event as string[])"><el-checkbox v-for="option in questionItem.options" :key="option" :value="option">{{ option }}</el-checkbox></el-checkbox-group>
            <el-radio-group v-else-if="questionItem.type === 'TRUE_FALSE'" :model-value="answers[questionItem.id]" :disabled="!canEdit" @update:model-value="updateAnswer(questionItem.id, Boolean($event))"><el-radio :value="true">正确</el-radio><el-radio :value="false">错误</el-radio></el-radio-group>
            <div v-else-if="questionItem.type === 'CODE'" class="code-answer">
              <el-input :model-value="answerText(questionItem.id)" type="textarea" :rows="10" :disabled="!canEdit" placeholder="粘贴代码或描述实现思路" @update:model-value="updateAnswer(questionItem.id, $event)" />
              <label class="attachment-upload" :class="{ disabled: !canEdit || uploadingQuestionId === questionItem.id }"><input type="file" accept=".zip,application/zip,application/x-zip-compressed" :disabled="!canEdit || uploadingQuestionId === questionItem.id" @change="uploadAttachment(questionItem.id, ($event.target as HTMLInputElement).files)" /><strong>{{ uploadingQuestionId === questionItem.id ? '上传中…' : '上传源码 ZIP' }}</strong><span>{{ attachmentNames[questionItem.id] || '最多 50 MB；不会执行源码' }}</span></label>
            </div>
            <el-input v-else :model-value="answerText(questionItem.id)" type="textarea" :rows="5" :disabled="!canEdit" placeholder="输入你的答案和推理过程" @update:model-value="updateAnswer(questionItem.id, $event)" />
          </div>
        </section>

        <section v-if="!canTeachCourse" class="tutor-panel panel">
          <div class="panel__header"><div><h2>AI Tutor</h2><span class="muted">Tutor 会遵守教师设置的解题策略</span></div></div>
          <div class="panel__body stack">
            <div class="tutor-controls"><el-select v-model="tutorQuestionId" placeholder="选择题目"><el-option v-for="(item, index) in detail.questions" :key="item.id" :label="`第 ${index + 1} 题`" :value="item.id" /></el-select><el-select v-model="tutorAction"><el-option label="给我提示" value="HINT" /><el-option label="解释知识点" value="EXPLAIN" /><el-option label="检查思路" value="CHECK_REASONING" /><el-option label="分析错误" value="ANALYZE_ERROR" /><el-option label="评价草稿" value="EVALUATE_DRAFT" /><el-option label="完整解析" value="FULL_SOLUTION" /></el-select><el-button type="primary" :loading="tutorLoading" @click="askTutor">请求辅导</el-button></div>
            <div v-if="tutorResult" class="tutor-response" :class="{ denied: !tutorResult.allowed }"><strong>{{ tutorResult.allowed ? 'Tutor 建议' : '当前操作受限' }}</strong><p>{{ tutorResult.policyMessage || tutorResult.content }}</p><p v-if="tutorResult.allowed && tutorResult.policyMessage" class="muted">{{ tutorResult.content }}</p></div>
          </div>
        </section>
      </div>
      <div v-else class="panel"><EmptyState title="请选择一份作业" /></div>
    </section>

    <el-dialog v-model="createVisible" title="创建作业" width="min(620px, 94vw)"><el-alert title="创建后可在作业详情中继续配置题目、Rubric 与 Tutor 策略。" type="info" :closable="false" /><el-form label-position="top" class="create-form"><el-form-item label="标题"><el-input v-model="createForm.title" /></el-form-item><el-form-item label="说明"><el-input v-model="createForm.description" type="textarea" /></el-form-item><div class="form-grid"><el-form-item label="发布范围"><el-select v-model="createForm.classId" clearable placeholder="全部教学班"><el-option label="全部教学班" value="" /><el-option v-for="item in courseClasses" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="最大提交次数"><el-input-number v-model="createForm.maxAttempts" :min="1" :max="20" /></el-form-item></div><div class="form-grid"><el-form-item label="开放时间"><el-date-picker v-model="createForm.availableAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" clearable style="width:100%" /></el-form-item><el-form-item label="截止时间"><el-date-picker v-model="createForm.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" clearable style="width:100%" /></el-form-item></div></el-form><template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" @click="createAssignment">创建草稿</el-button></template></el-dialog>
    <el-dialog v-model="extensionVisible" :title="`为 ${extensionForm.studentName} 设置个别延期`" width="min(460px, 94vw)"><el-form label-position="top"><el-form-item label="个人截止时间"><el-date-picker v-model="extensionForm.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" clearable style="width:100%" /></el-form-item><el-alert title="清空截止时间并保存，可取消该学生的个别延期。" type="info" :closable="false" /></el-form><template #footer><el-button @click="extensionVisible = false">取消</el-button><el-button type="primary" @click="saveExtension">保存</el-button></template></el-dialog>
    <el-dialog v-model="questionVisible" :title="editingQuestionId ? '编辑题目' : '添加题目'" width="min(680px, 94vw)"><el-form label-position="top"><div class="form-grid"><el-form-item label="题型"><el-select v-model="questionForm.type"><el-option v-for="value in (['SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE','SHORT_ANSWER','ANALYSIS','DESIGN','CODE'] as const)" :key="value" :label="value" :value="value" /></el-select></el-form-item><el-form-item label="分值"><el-input-number v-model="questionForm.points" :min="0.01" :precision="2" /></el-form-item></div><el-form-item label="题目"><el-input v-model="questionForm.prompt" type="textarea" :rows="4" /></el-form-item><el-form-item v-if="['SINGLE_CHOICE','MULTIPLE_CHOICE'].includes(questionForm.type)" label="选项（每行一个）"><el-input v-model="questionForm.optionsText" type="textarea" :rows="5" /></el-form-item><el-form-item label="参考答案（仅教师可见）"><el-input v-model="questionForm.referenceAnswer" type="textarea" :rows="3" :placeholder="editingQuestionId ? '留空表示保留原参考答案' : ''" /></el-form-item><div class="form-grid"><el-form-item label="知识点"><el-select v-model="questionForm.knowledgePointId" clearable><el-option v-for="point in knowledgePoints" :key="point.id" :label="point.title" :value="point.id" /></el-select></el-form-item><el-form-item label="排序"><el-input-number v-model="questionForm.orderIndex" :min="0" /></el-form-item></div></el-form><template #footer><el-button @click="questionVisible = false">取消</el-button><el-button type="primary" @click="saveQuestion">保存题目</el-button></template></el-dialog>
    <el-dialog v-model="rubricItemVisible" title="添加 Rubric 分项" width="min(560px, 94vw)"><el-form label-position="top"><el-form-item label="分项名称"><el-input v-model="rubricItemForm.title" /></el-form-item><el-form-item label="说明"><el-input v-model="rubricItemForm.description" type="textarea" /></el-form-item><div class="form-grid"><el-form-item label="关联题目"><el-select v-model="rubricItemForm.questionId" clearable><el-option v-for="(questionItem,index) in detail?.questions || []" :key="questionItem.id" :label="`第 ${index + 1} 题`" :value="questionItem.id" /></el-select></el-form-item><el-form-item label="最高分"><el-input-number v-model="rubricItemForm.maxScore" :min="0.01" :precision="2" /></el-form-item></div><el-form-item label="排序"><el-input-number v-model="rubricItemForm.orderIndex" :min="0" /></el-form-item></el-form><template #footer><el-button @click="rubricItemVisible = false">取消</el-button><el-button type="primary" @click="addRubricItem">添加</el-button></template></el-dialog>
  </div>
</template>

<style scoped>
.assignment-layout { display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: 20px; align-items: start; }
.assignment-list { position: sticky; top: 92px; overflow: hidden; }
.assignment-list__items { display: grid; padding: 8px; }
.assignment-list__items button { border: 0; border-radius: 9px; padding: 13px; background: transparent; color: var(--ink); text-align: left; }
.assignment-list__items button:hover, .assignment-list__items button.active { background: #eff2fc; }
.assignment-list__items button > div { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.assignment-list__items p { display: -webkit-box; overflow: hidden; margin: 7px 0; color: var(--muted); font-size: 12px; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.assignment-list__items small { color: #8f98a9; }
.assignment-description { margin: 0; color: #596477; line-height: 1.7; }
.question-title { display: flex; align-items: flex-start; gap: 12px; }.question-title > span { width: 29px; height: 29px; display: grid; place-items: center; border-radius: 8px; background: #eaf0ff; color: var(--brand); font-weight: 800; }
.question-title h3 { margin: 0 0 5px; font-size: 15px; line-height: 1.6; }.question-title small { color: var(--muted); }
.option-list { display: grid; gap: 12px; }
.code-answer { display: grid; gap: 12px; }.attachment-upload { display: flex; align-items: center; gap: 10px; border: 1px dashed #aab5c9; border-radius: 9px; padding: 11px 13px; background: #f8f9fc; cursor: pointer; }.attachment-upload input { position: absolute; width: 1px; height: 1px; opacity: 0; }.attachment-upload strong { color: var(--brand); font-size: 12px; }.attachment-upload span { overflow: hidden; color: var(--muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.attachment-upload.disabled { cursor: not-allowed; opacity: .55; }
.save-state { border-radius: 999px; padding: 7px 12px; background: #edf0f5; color: var(--muted); font-size: 12px; }.save-state--saved { background: #def7ef; color: #087b64; }.save-state--error { background: #ffe7e7; color: #ad3039; }
.tutor-controls { display: grid; grid-template-columns: 1fr 1fr auto; gap: 10px; }.tutor-response { border-left: 3px solid var(--accent); border-radius: 7px; padding: 14px 16px; background: #eefaf7; }.tutor-response.denied { border-color: #df9d2a; background: #fff7e7; }.tutor-response p { margin: 7px 0 0; white-space: pre-wrap; line-height: 1.7; }
.create-form { margin-top: 18px; }.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.teacher-editor .el-form { border-bottom: 1px solid var(--line); padding-bottom: 18px; }.teacher-grid { display: grid; grid-template-columns: 1fr 1.3fr; gap: 14px; }.subpanel { display: grid; gap: 10px; border: 1px solid var(--line); border-radius: 10px; padding: 14px; background: #fafbfc; }.subpanel__header { display: flex; align-items: center; justify-content: space-between; }.subpanel .el-switch { justify-content: flex-start; }.rubric-editor-list { display: grid; gap: 6px; }.rubric-editor-list article { display: flex; align-items: center; justify-content: space-between; border-radius: 7px; padding: 8px; background: #fff; }.rubric-editor-list article span { display: grid; }.rubric-editor-list small { color: var(--muted); }
@media (max-width: 900px) { .assignment-layout { grid-template-columns: 1fr; }.assignment-list { position: static; }.tutor-controls { grid-template-columns: 1fr; } }
@media (max-width: 760px) { .teacher-grid { grid-template-columns: 1fr; } }
@media (max-width: 580px) { .form-grid { grid-template-columns: 1fr; } }
</style>
