<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import GradingWorkbench from '@/components/GradingWorkbench.vue'
import { reviewApi } from '@/api/reviews'
import { assignmentApi } from '@/api/assignments'
import { useCourseStore } from '@/stores/courses'
import type { AssignmentSummary, GradeRecord } from '@/types/domain'
const courseStore = useCourseStore(), route = useRoute()
const grades = ref<GradeRecord[]>([]), assignments = ref<AssignmentSummary[]>([]), selectedAssignment = ref('')
const loading = ref(false), error = ref(''), selected = ref(''), visible = ref(false), page = ref(0), total = ref(0), publishing = ref(false)
const staff = computed(() => ['TEACHER', 'TA'].includes(courseStore.selectedCourse?.role || ''))
const canConfirm = computed(() => courseStore.selectedCourse?.role === 'TEACHER')
let generation = 0
const labels: Record<string, string> = { WAITING_REVIEW: '待初评', REVIEWED: '已有部分初评', PENDING_CONFIRMATION: '待确认', CONFIRMED: '已确认 · 未发布', PUBLISHED: '已发布' }
async function load() {
  const n = ++generation; loading.value = true; error.value = ''; grades.value = []
  try {
    const result = await reviewApi.grades(courseStore.selectedCourseId || undefined, page.value)
    if (n !== generation) return
    grades.value = result.items; total.value = result.total
  } catch (e) { if (n === generation) error.value = e instanceof Error ? e.message : '成绩加载失败' }
  finally { if (n === generation) loading.value = false }
}
function open(id: string) { selected.value = id; visible.value = true }
async function batchPublish() {
  if (!selectedAssignment.value || publishing.value) return
  const id = selectedAssignment.value, course = courseStore.selectedCourseId
  publishing.value = true
  try {
    const p = await reviewApi.publicationPreview(id)
    await ElMessageBox.confirm('按每位学生最新正式提交统计：已确认 ' + p.confirmed + ' 人，未确认 ' + p.unconfirmed + ' 人，可发布 ' + p.publishable + ' 人。仅发布已确认成绩，是否继续？', '批量发布成绩')
    if (course !== courseStore.selectedCourseId) return
    await reviewApi.publishAssignment(id); ElMessage.success('已发布可发布成绩'); await load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof Error ? e.message : '发布失败') }
  finally { publishing.value = false }
}
watch(() => courseStore.selectedCourseId, async course => {
  visible.value = false; selected.value = ''; selectedAssignment.value = ''; assignments.value = []; page.value = 0
  await load()
  if (course && staff.value) { try { const result = await assignmentApi.list(course); if (course === courseStore.selectedCourseId) assignments.value = result.items } catch { /* Main list error remains independent. */ } }
  if (staff.value && typeof route.query.submission === 'string') open(route.query.submission)
}, { immediate: true })
</script>
<template><div>
  <PageHeader title="成绩与反馈" :description="staff ? '最终批改工作台：查看初评、人工批改、确认并主动发布成绩。' : '仅展示教师已正式发布的成绩与反馈。'"><el-button @click="load">刷新</el-button></PageHeader>
  <section v-if="canConfirm" class="panel panel__body"><el-select v-model="selectedAssignment" placeholder="选择作业批量发布"><el-option v-for="a in assignments" :key="a.id" :value="a.id" :label="a.title" /></el-select><el-button :disabled="!selectedAssignment || publishing" @click="batchPublish">预览并批量发布</el-button></section>
  <section class="panel" v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-if="grades.length" :data="grades">
      <el-table-column prop="assignmentTitle" label="作业" min-width="160" />
      <el-table-column v-if="staff" prop="studentName" label="学生" />
      <el-table-column v-if="staff" label="初评分"><template #default="s">{{ s.row.suggestedScore ?? '—' }}</template></el-table-column>
      <el-table-column label="最终成绩"><template #default="s">{{ s.row.score ?? '待确认 / —' }} / {{ s.row.maxScore }}</template></el-table-column>
      <el-table-column label="状态"><template #default="s"><StatusBadge :status="labels[s.row.status] || s.row.status" /></template></el-table-column>
      <el-table-column prop="feedback" label="教师反馈" />
      <el-table-column label="更新时间"><template #default="s">{{ s.row.gradedAt ? new Date(s.row.gradedAt).toLocaleString() : '—' }}</template></el-table-column>
      <el-table-column v-if="staff" label="操作"><template #default="s"><el-button @click="open(s.row.submissionId)">查看与批改</el-button></template></el-table-column>
    </el-table>
    <EmptyState v-else-if="!loading && !error" title="暂无成绩记录" :description="staff ? '学生正式提交后会进入批改工作台。' : '教师发布后，正式成绩才会在这里显示。'" />
    <el-pagination v-if="total > 20" :total="total" :page-size="20" :current-page="page+1" @current-change="page=$event-1; load()" />
  </section>
  <el-dialog v-model="visible" title="批改与成绩发布" width="min(1100px, 96vw)" destroy-on-close :close-on-click-modal="false"><GradingWorkbench v-if="selected && staff" :submission-id="selected" :can-confirm="canConfirm" @changed="load" /></el-dialog>
</div></template>
