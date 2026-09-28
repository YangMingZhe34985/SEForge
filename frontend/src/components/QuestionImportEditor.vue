<script setup lang="ts">
import { ref } from 'vue'
import { assignmentApi, type QuestionImport } from '@/api/assignments'
import type { AssignmentQuestionInput, KnowledgePoint, QuestionType } from '@/types/domain'
import TypedQuestionFields, { type QuestionDraft } from './TypedQuestionFields.vue'
import AssignmentMediaList from './AssignmentMediaList.vue'
import { defaultChoices } from './choiceOptions'

const props = defineProps<{ assignmentId: string; knowledgePoints: KnowledgePoint[] }>()
const emit = defineEmits<{ imported: [] }>()
const busy = ref(false)
const error = ref('')
const sources = ref<{ key: string; file: File; id?: string }[]>([])
const imported = ref<QuestionImport>()
const entries = ref<{ key: string; selected: boolean; typeConfirmed: boolean; score: number | undefined; page: number; sourceFile: string; region: number[]; warnings: string[]; form: QuestionDraft }[]>([])
function choose(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) return
  if (files.length > 3 || (files.length > 1 && files.some(f => !['image/png','image/jpeg'].includes(f.type)))) {
    error.value = '每次请选择 1～3 张 PNG/JPEG 图片，或 1 份 PDF/Markdown'; return
  }
  sources.value = files.map(file => ({ key: crypto.randomUUID(), file }))
  imported.value = undefined; entries.value = []; error.value = ''
}
function reorderSources(index: number, direction: number) {
  const target = index + direction
  if (busy.value || target < 0 || target >= sources.value.length) return
  const [item] = sources.value.splice(index, 1); sources.value.splice(target, 0, item!)
  entries.value.sort((a,b) => sources.value.findIndex(s => s.id === a.sourceFile) - sources.value.findIndex(s => s.id === b.sourceFile))
  entries.value.forEach((entry, order) => { entry.form.orderIndex = order })
}
function removeSource(index: number) {
  const [item] = sources.value.splice(index, 1)
  entries.value = entries.value.filter(q => q.sourceFile !== item?.id)
  entries.value.forEach((entry, order) => { entry.form.orderIndex = order })
}
async function extract(reparseFile?: string) {
  if (!sources.value.length || busy.value) return
  busy.value = true; error.value = ''
  try {
    for (const source of sources.value) {
      if (!source.id) source.id = (await assignmentApi.uploadMedia(props.assignmentId, 'IMPORT', source.file)).id
    }
    const ids = reparseFile && imported.value ? (imported.value.sources?.map(s => s.id) || [imported.value.sourceFile]) : sources.value.map(s => s.id!)
    const result = await assignmentApi.extractQuestions(props.assignmentId, ids, Boolean(imported.value), reparseFile)
    const previous = new Map(entries.value.map(q => [q.key, q]))
    imported.value = result
    entries.value = result.questions.filter(q => sources.value.some(s => s.id === (q.sourceFile || result.sourceFile))).map(q => {
      const existing = reparseFile ? previous.get(q.draftKey) : undefined
      if (existing) return existing
      return { key: q.draftKey, selected: true, typeConfirmed: q.type !== null,
        sourceFile: q.sourceFile || result.sourceFile, score: q.score ?? undefined, page: q.sourcePage, region: q.sourceRegion, warnings: q.warnings,
        form: { type: q.type || 'SHORT_ANSWER', prompt: q.contentMarkdown, optionsText: '', referenceAnswer: q.referenceAnswer,
          points: q.score ?? 0, orderIndex: q.order, knowledgePointId: '', config: { schemaVersion: 1, assetIds: [],
            choices: q.choices.length ? q.choices : undefined, gradingMode: ['SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE'].includes(q.type || '') ? 'RULE' : 'AI_ASSISTED',
            answerSpec: { assetIds: [], ...(q.correctAnswer === null ? {} : { correct: q.correctAnswer }), ...(q.type === 'DOCUMENT_REPORT' ? { allowedFileTypes: ['pdf','docx'] } : {}) } } } }
    })
    entries.value.sort((a,b) => sources.value.findIndex(s => s.id === a.sourceFile) - sources.value.findIndex(s => s.id === b.sourceFile))
    entries.value.forEach((entry, order) => { entry.form.orderIndex = order })
  } catch (e) { error.value = e instanceof Error ? e.message : '导入失败' }
  finally { busy.value = false }
}
function move(index: number, direction: number) {
  const target = index + direction
  if (target < 0 || target >= entries.value.length) return
  const [entry] = entries.value.splice(index, 1); entries.value.splice(target, 0, entry!)
  entries.value.forEach((entry, order) => { entry.form.orderIndex = order })
}
function confirmType(entry: typeof entries.value[number], type: QuestionType) {
  entry.form.type = type; entry.typeConfirmed = true
  const choice = ['SINGLE_CHOICE','MULTIPLE_CHOICE'].includes(type)
  entry.form.config.gradingMode = choice || type === 'TRUE_FALSE' ? 'RULE' : 'AI_ASSISTED'
  if (choice && !entry.form.config.choices?.length) entry.form.config.choices = defaultChoices()
  if (type === 'DOCUMENT_REPORT') entry.form.config.answerSpec!.allowedFileTypes = ['pdf','docx']
}
async function confirm() {
  if (!imported.value || busy.value) return
  const selected = entries.value.filter(q => q.selected)
  if (!selected.length || selected.some(q => !q.typeConfirmed || !q.score || q.score <= 0)) {
    error.value = '请至少选择一题，并明确确认每题的题型与分值'; return
  }
  busy.value = true; error.value = ''
  try {
    await assignmentApi.confirmImport(props.assignmentId, imported.value.id, selected.map(q => ({ draftKey: q.key,
      question: { type: q.form.type, prompt: q.form.prompt, referenceAnswer: q.form.referenceAnswer, points: q.score!,
        orderIndex: q.form.orderIndex, config: { ...q.form.config }, knowledgePointId: q.form.config.knowledgePointIds?.[0],
        options: ['SINGLE_CHOICE','MULTIPLE_CHOICE'].includes(q.form.type) ? q.form.config.choices?.map(c => c.id) : undefined } satisfies AssignmentQuestionInput })))
    imported.value.confirmed = true; emit('imported')
  } catch (e) { error.value = e instanceof Error ? e.message : '确认失败，可安全重试' }
  finally { busy.value = false }
}
const types: QuestionType[] = ['SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE','SHORT_ANSWER','ANALYSIS','DESIGN','CODE','DOCUMENT_REPORT']
</script>
<template>
  <section>
    <p>PDF / PNG / JPEG / Markdown → 可编辑草稿 → 确认导入。不会自动发布作业、答案或 Rubric。整份源文件仅教师可见。</p>
    <input type="file" multiple accept=".pdf,.png,.jpg,.jpeg,.md" :disabled="busy" @change="choose" />
    <p>一次 1～3 张图片（按列表顺序解析），或 1 份 PDF/Markdown。单图重解析仅替换该图草稿，保留其他题目的编辑。</p>
    <ol class="source-list"><li v-for="(source,index) in sources" :key="source.key"><span>{{ index + 1 }}. {{ source.file.name }}</span><el-button :disabled="busy || imported?.confirmed || index === 0" @click="reorderSources(index,-1)">图片上移</el-button><el-button :disabled="busy || imported?.confirmed || index === sources.length-1" @click="reorderSources(index,1)">图片下移</el-button><el-button :disabled="busy || imported?.confirmed" @click="removeSource(index)">删除来源</el-button><el-button v-if="imported && source.id && source.file.type.startsWith('image/')" :disabled="busy || imported.confirmed" @click="extract(source.id)">重新解析此图（替换该图草稿）</el-button></li></ol>
    <el-button :loading="busy" :disabled="!sources.length || imported?.confirmed" @click="extract()">{{ imported ? '重新解析（放弃当前草稿）' : '解析题目' }}</el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="imported">
      <AssignmentMediaList :assignment-id="assignmentId" :ids="sources.map(s => s.id!).filter(Boolean)" />
      <article v-for="(entry,index) in entries" :key="entry.key" class="import-question">
        <el-checkbox v-model="entry.selected">导入第 {{ index + 1 }} 题</el-checkbox>
        <span>来源 {{ sources.find(s => s.id === entry.sourceFile)?.file.name }} · 第 {{ entry.page }} 页 <template v-if="entry.region.length">区域 {{ entry.region.join(', ') }}</template></span>
        <el-button :disabled="busy || index === 0" @click="move(index,-1)">上移</el-button><el-button :disabled="busy || index === entries.length-1" @click="move(index,1)">下移</el-button><el-button :disabled="busy" @click="entries.splice(index,1)">删除草稿</el-button>
        <p v-for="warning in entry.warnings" :key="warning">{{ warning }}</p>
        <div v-if="!entry.typeConfirmed"><label>未知题型，请确认</label><el-select class="unknown-type" placeholder="请选择题型" @change="confirmType(entry, $event)"><el-option v-for="type in types" :key="type" :value="type" :label="type" /></el-select></div>
        <label>确认分值（未识别时留空）<el-input-number v-model="entry.score" :min="0.01" :precision="2" @change="entry.form.points = entry.score || 0" /></label>
        <TypedQuestionFields v-model="entry.form" :assignment-id="assignmentId" :knowledge-points="knowledgePoints" hide-score />
      </article>
      <el-button type="primary" :loading="busy" :disabled="imported.confirmed" @click="confirm">确认导入所选题目</el-button>
    </template>
    <p v-else-if="!busy">请选择文件。缺失答案或分值会保留为待确认，不自动猜测。</p>
  </section>
</template>
<style scoped>.import-question{border:1px solid var(--el-border-color);padding:16px;margin:16px 0}p{font-size:13px}.unknown-type{width:min(100%,340px)}.source-list{padding-left:20px}.source-list li{display:flex;flex-wrap:wrap;gap:8px;margin:8px 0}.source-list span{overflow-wrap:anywhere;flex:1 1 200px}input[type=file]{max-width:100%}.import-question{min-width:0}:deep(.unknown-type .el-select__selected-item){white-space:normal;overflow-wrap:anywhere}</style>
