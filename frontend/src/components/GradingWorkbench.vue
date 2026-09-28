<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { reviewApi, type GradingDetail } from '@/api/reviews'
import { assignmentApi } from '@/api/assignments'
import { apiUrl } from '@/api/client'
import type { AssignmentQuestion, SubmissionAnswerInput } from '@/types/domain'
import SafeMarkdown from './SafeMarkdown.vue'
import AssignmentMediaList from './AssignmentMediaList.vue'
import TeacherReference from './TeacherReference.vue'
import { choiceText } from './choiceOptions'
const props = defineProps<{ submissionId: string; manualOnly?: boolean; canConfirm?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const detail = ref<GradingDetail>(), busy = ref(false), error = ref(''), feedback = ref(''), reason = ref('')
const rows = ref<{ rubricItemId?: string; questionId?: string; title: string; maximum: number; score: number | undefined; feedback: string }[]>([])
let generation = 0
const final = computed(() => ['CONFIRMED', 'PUBLISHED'].includes(detail.value?.grade.status || ''))
function targetTitle(item: { rubricItemId?: string; questionId?: string }) { return rows.value.find(r => r.rubricItemId === item.rubricItemId && r.questionId === item.questionId)?.title || '总体反馈' }
const complete = computed(() => rows.value.length > 0 && rows.value.every(r => r.score != null && r.score >= 0 && r.score <= r.maximum))
const total = computed(() => complete.value ? Math.round(rows.value.reduce((s, r) => s + (r.score ?? 0), 0) * 100) / 100 : null)
async function load() {
  const current = ++generation; detail.value = undefined; rows.value = []; error.value = ''; feedback.value = ''; reason.value = ''; busy.value = true
  try {
    const value = await reviewApi.grading(props.submissionId)
    const rubric = await assignmentApi.rubric(value.assignment.id)
    if (current !== generation) return
    detail.value = value
    rows.value = value.targets.map(target => {
      const previous = [...(value.grade.rubricItems || [])].reverse().filter(f => f.rubricItemId === target.rubricItemId && f.questionId === target.questionId)
      const chosen = previous.find(f => f.source === 'TEACHER') || previous.find(f => f.source === 'MANUAL') || previous[0]
      const q = value.assignment.questions.find(q => q.id === target.questionId)
      return { ...target, title: target.rubricItemId ? rubric?.items.find(i => i.id === target.rubricItemId)?.title || '历史评分分项' : q?.prompt.slice(0, 60) || '题目评分', score: chosen?.finalScore ?? chosen?.suggestedScore ?? undefined, feedback: chosen?.feedback || '' }
    })
  } catch (e) { if (current === generation) error.value = e instanceof Error ? e.message : '批改详情加载失败' }
  finally { if (current === generation) busy.value = false }
}
watch(() => props.submissionId, load, { immediate: true })
async function save(manual: boolean) {
  if (busy.value || total.value == null || final.value) return
  busy.value = true; error.value = ''
  try {
    const values = rows.value.map(r => ({ rubricItemId: r.rubricItemId || undefined, questionId: r.questionId || undefined, score: r.score!, feedback: r.feedback }))
    if (manual) await reviewApi.manualReview(props.submissionId, total.value, feedback.value, reason.value, values)
    else await reviewApi.confirmGrade(props.submissionId, total.value, feedback.value, reason.value || undefined, values, detail.value?.grade.aiTraceId)
    ElMessage.success(manual ? '人工初评已保存，尚未生成正式成绩' : '成绩已确认，发布前学生不可见')
    emit('changed'); await load()
  } catch (e) { error.value = e instanceof Error ? e.message : '评分保存失败' }
  finally { busy.value = false }
}
async function publish() {
  if (busy.value) return
  busy.value = true
  try { await reviewApi.publish(props.submissionId); ElMessage.success('成绩已发布'); emit('changed'); await load() }
  catch (e) { error.value = e instanceof Error ? e.message : '发布失败' }
  finally { busy.value = false }
}
function answer(q: AssignmentQuestion): SubmissionAnswerInput['answer'] | undefined { return detail.value?.submission.answers.find(a => a.questionId === q.id)?.answer }
function assets(q: AssignmentQuestion): string[] { const a = answer(q); return a && typeof a === 'object' && !Array.isArray(a) ? a.assetIds : [] }
function text(q: AssignmentQuestion): string {
  const a = answer(q)
  if (a == null) return '未作答'
  if (typeof a === 'boolean') return a ? '正确 / True' : '错误 / False'
  if (q.config?.choices && (typeof a === 'string' || Array.isArray(a))) return (Array.isArray(a) ? a : [a]).map(id => choiceText(q.config, id, q.config!.choices!.findIndex(c => c.id === id))).join('；')
  return typeof a === 'object' && !Array.isArray(a) ? a.text : Array.isArray(a) ? a.join('；') : a
}
</script>
<template><section v-loading="busy" class="workbench">
  <el-alert v-if="error" :title="error" type="error" :closable="false" /><el-button v-if="error && !detail" @click="load">重试</el-button>
  <template v-if="detail">
    <h3>{{ detail.assignment.title }} · {{ detail.grade.studentName }} · 第 {{ detail.submission.attemptNumber }} 次提交</h3>
    <p>状态：{{ detail.grade.status }} · 初评分：{{ detail.grade.suggestedScore ?? '—' }} · 最终成绩：{{ detail.grade.score ?? '待确认 / —' }}</p>
    <article v-for="(q, index) in detail.assignment.questions" :key="q.id" class="question">
      <h4>第 {{ index + 1 }} 题 · {{ q.type }} · {{ q.points }} 分</h4><SafeMarkdown :content="q.prompt" />
      <AssignmentMediaList :assignment-id="detail.assignment.id" :ids="q.config?.assetIds" />
      <TeacherReference :question="q" :assignment-id="detail.assignment.id" />
      <strong>学生答案</strong><SafeMarkdown :content="text(q)" /><AssignmentMediaList :assignment-id="detail.assignment.id" :ids="assets(q)" />
      <a v-if="detail.submission.answers.some(a => a.questionId === q.id && a.attachmentObjectKey)" :href="apiUrl(`/submissions/${submissionId}/grading/attachments/${q.id}`)" target="_blank" rel="noopener">下载原始提交附件</a>
    </article>
    <h3>初评记录（RULE / AI_ASSISTED / MANUAL）与教师确认</h3>
    <el-table :data="detail.grade.rubricItems || []"><el-table-column label="题目 / 评分分项"><template #default="s">{{ targetTitle(s.row) }}</template></el-table-column><el-table-column prop="source" label="来源" /><el-table-column label="分值"><template #default="s">{{ s.row.finalScore ?? s.row.suggestedScore ?? '—' }}</template></el-table-column><el-table-column prop="feedback" label="反馈 / 修改理由" /></el-table>
    <article v-for="row in rows" :key="row.rubricItemId || `q:${row.questionId}`" class="score-row">
      <label>{{ row.title }} / {{ row.maximum }} 分</label><el-input-number v-model="row.score" :min="0" :max="row.maximum" :precision="2" :disabled="final" aria-label="分项得分" /><el-input v-model="row.feedback" :disabled="final" placeholder="分项反馈" />
    </article>
    <p>本次评分合计：{{ total ?? '待逐项评分 / —' }} / {{ detail.grade.maxScore }}</p>
    <template v-if="!final"><el-input v-model="feedback" type="textarea" placeholder="教师总体反馈" /><el-input v-model="reason" placeholder="覆盖 RULE / AI / 人工初评时须填写修改理由" />
      <el-button :disabled="!complete || busy" @click="save(true)">保存人工初评</el-button>
      <el-button v-if="!manualOnly && canConfirm" type="primary" :disabled="!complete || busy" @click="save(false)">确认最终成绩</el-button>
    </template>
    <el-button v-if="!manualOnly && canConfirm && detail.grade.status === 'CONFIRMED'" type="success" :disabled="busy" @click="publish">发布成绩给学生</el-button>
    <p v-if="manualOnly">这里只保存初评；请到“成绩与反馈”确认和发布正式成绩。</p>
  </template>
</section></template>
<style scoped>.workbench{display:grid;gap:14px}.question,.score-row{padding:14px;border:1px solid var(--line);border-radius:8px}.score-row{display:grid;gap:10px}</style>
