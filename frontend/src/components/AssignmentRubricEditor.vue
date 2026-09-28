<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { assignmentApi } from '@/api/assignments'
import type { AssignmentQuestion, AssignmentRubric } from '@/types/domain'
import { allocation, gradingMode } from './rubricAllocation'

const props = defineProps<{ assignmentId: string; questions: AssignmentQuestion[]; editable: boolean }>()
const rubric = defineModel<AssignmentRubric | null>({ required: true })
const busy = ref(false)
const questionId = ref('')
const title = ref('综合评分')
const description = ref('')
const maximum = ref(0)
const editorMode = ref<'SIMPLE' | 'ITEMIZED'>('ITEMIZED')
const subjective = computed(() => props.questions.filter(q => gradingMode(q) !== 'RULE'))
const selected = computed(() => subjective.value.find(q => q.id === questionId.value))
const remaining = computed(() => selected.value ? allocation(selected.value, rubric.value?.items || []).remaining : 0)
const blocked = computed(() => props.questions.some(q => { const value = allocation(q, rubric.value?.items || []); return value.over || value.incomplete }))
const canAdd = computed(() => !!selected.value && !!title.value.trim() && maximum.value > 0 && maximum.value <= remaining.value && !busy.value)
watch([questionId, remaining], () => { maximum.value = remaining.value })
watch(() => props.assignmentId, () => { questionId.value = ''; title.value = '综合评分'; description.value = '' })

async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  try { await action() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '评分规则保存失败') }
  finally { busy.value = false }
}
async function ensureDraft(id: string) {
  if (rubric.value?.status === 'DRAFT') return
  const saved = await assignmentApi.saveRubric(id, { title: rubric.value?.title || '课程作业评分量表',
    totalScore: rubric.value?.totalScore || Math.max(1, subjective.value.reduce((sum, q) => sum + q.points, 0)), status: 'DRAFT' })
  if (props.assignmentId === id) rubric.value = saved
}
async function add(question: AssignmentQuestion, name: string, score: number, detail = '') {
  if (score <= 0 || score > allocation(question, rubric.value?.items || []).remaining) return
  const id = props.assignmentId
  await run(async () => {
    await ensureDraft(id)
    if (props.assignmentId !== id) return
    const item = await assignmentApi.addRubricItem(id, { questionId: question.id, title: name, description: detail,
      maxScore: score, orderIndex: rubric.value?.items.length || 0, criteria: {} })
    if (props.assignmentId === id && rubric.value) rubric.value = { ...rubric.value, items: [...rubric.value.items, item] }
  })
}
async function remove(itemId: string) {
  const id = props.assignmentId
  await run(async () => {
    await ensureDraft(id)
    await assignmentApi.deleteRubricItem(id, itemId)
    if (props.assignmentId === id && rubric.value) rubric.value = { ...rubric.value, items: rubric.value.items.filter(i => i.id !== itemId) }
  })
}
async function publish() {
  if (blocked.value || !rubric.value?.items.length) return
  const id = props.assignmentId
  const current = rubric.value
  await run(async () => {
    const saved = await assignmentApi.saveRubric(id, { title: current.title,
      totalScore: Math.round(current.items.reduce((sum, item) => sum + item.maxScore, 0) * 100) / 100, status: 'PUBLISHED' })
    if (props.assignmentId === id) rubric.value = saved
  })
}
</script>

<template>
  <section class="rubric-editor">
    <p v-if="questions.some(q => gradingMode(q) === 'RULE')">客观题按标准答案进行 RULE 评分，无需配置 Rubric。</p>
    <template v-if="!subjective.length && editable && rubric?.items.length">
      <small>历史可选评分分项，可删除后直接使用题目分值。</small>
      <article v-for="item in rubric.items" :key="item.id" class="allocation-row">
        <span>{{ item.title }} · {{ item.maxScore }} 分</span>
        <el-button :disabled="busy" link type="danger" @click="remove(item.id)">删除历史分项</el-button>
      </article>
    </template>
    <template v-if="subjective.length">
      <strong>AI 评分准则</strong>
      <p>为 AI 辅助评分明确“评价什么、依据什么、最多多少分”。AI 只给建议，最终成绩仍由教师确认；MANUAL 题目的准则可选。</p>
      <el-radio-group v-if="editable" v-model="editorMode" aria-label="评分结构"><el-radio-button value="SIMPLE">简单综合评分</el-radio-button><el-radio-button value="ITEMIZED">分项评分</el-radio-button></el-radio-group>
      <article v-for="question in subjective" :key="question.id" class="allocation-row">
        <span>{{ question.prompt.slice(0, 50) || '图片题' }} · {{ gradingMode(question) }}</span>
        <span>已分配 {{ allocation(question, rubric?.items || []).assigned }} / {{ question.points }}</span>
        <small v-if="allocation(question, rubric?.items || []).over" role="alert">超过题目分值，禁止保存和发布</small>
        <small v-else-if="allocation(question, rubric?.items || []).incomplete">未完成：请分配剩余分值</small>
        <small v-else-if="gradingMode(question) === 'MANUAL'">Rubric 可选，由教师人工评分</small>
        <el-button v-if="editable && allocation(question, rubric?.items || []).assigned === 0" :disabled="busy" @click="add(question, '综合评分', question.points)">综合评分 / {{ question.points }}</el-button>
      </article>
      <template v-if="editable">
        <p v-if="editorMode === 'SIMPLE'">点击题目下的“综合评分”可创建一个等于题目总分的维度。需要拆分时切换“分项评分”，先删除原综合分项再重新分配。</p>
        <el-form v-show="editorMode === 'ITEMIZED'" label-position="top">
        <el-form-item label="关联主观题">
        <el-select v-model="questionId" placeholder="选择关联主观题" aria-label="关联题目">
          <el-option v-for="q in subjective" :key="q.id" :value="q.id" :label="q.prompt.slice(0, 40) || '图片题'" />
        </el-select>
        </el-form-item>
        <el-form-item label="评分维度名称"><el-input v-model="title" placeholder="例如：设计完整性" aria-label="分项名称" /></el-form-item>
        <el-form-item label="AI 判断依据"><el-input v-model="description" type="textarea" :rows="3" placeholder="例如：是否覆盖全部需求；缺少关键模块扣 2 分；请写明可验证的得分和扣分依据" aria-label="评分要求" /></el-form-item>
        <el-form-item label="分值"><el-input-number v-model="maximum" :min="0" :max="remaining" :disabled="!selected || remaining <= 0" :precision="2" aria-label="最高分" /></el-form-item>
        <small>剩余可分配 {{ remaining }} 分；第一项默认使用该题全部分值。</small>
        <p v-if="selected && remaining <= 0" role="status">本题总分已全部分配，不能继续添加。请先删除或调整已有分项，再重新分配。</p>
        <el-button :disabled="!canAdd" @click="selected && add(selected, title, maximum, description)">添加分项</el-button>
        </el-form>
      </template>
      <article v-for="item in rubric?.items || []" :key="item.id" class="allocation-row">
        <span>评分维度名称：{{ item.title }}</span><span>AI 判断依据：{{ item.description || '未填写具体判断依据，请补充可验证的评分标准' }}</span><span>分值：{{ item.maxScore }} 分</span>
        <el-button v-if="editable" :disabled="busy" link type="danger" @click="remove(item.id)">删除分项</el-button>
      </article>
      <small>评分规则状态：{{ rubric?.status === 'PUBLISHED' ? '已发布' : '草稿' }}。调整分项后需重新发布评分规则。</small>
      <el-button v-if="editable" :disabled="blocked || !rubric?.items.length || busy" @click="publish">保存并发布评分规则</el-button>
    </template>
  </section>
</template>

<style scoped>
.rubric-editor { display: grid; gap: 10px; border: 1px solid var(--line); border-radius: 10px; padding: 14px; }
.allocation-row { display: grid; gap: 6px; padding: 8px; border-bottom: 1px solid var(--line); }
.rubric-editor :deep(.el-select){width:100%}.rubric-editor :deep(.el-radio-group){flex-wrap:wrap}
small { color: var(--muted); } [role="alert"] { color: var(--el-color-danger); }
</style>
