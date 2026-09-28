<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { teachingContentApi, type PointDraft, type PointProposal } from '@/api/teachingContent'
const props = defineProps<{ courseId: string; chapterId: string }>()
const emit = defineEmits<{ confirmed: [] }>()
const draft = ref<PointDraft | null>(null)
const rows = ref<(PointProposal & { selected: boolean })[]>([])
const busy = ref(false)
const error = ref('')
const citationCoverage = computed(() => {
  const documents = new Map((draft.value?.sources || []).map(source => [String(source.documentId), source.name]))
  const citedIds = new Set(rows.value.filter(row => row.selected).flatMap(row => row.sourceIds.map(String)))
  const citedDocuments = new Set((draft.value?.sources || []).filter(source => citedIds.has(String(source.id))).map(source => String(source.documentId)))
  return { total: documents.size, cited: citedDocuments.size, uncited: [...documents].filter(([id]) => !citedDocuments.has(id)).map(([, name]) => name) }
})
let generation = 0
onBeforeUnmount(() => { generation++ })
watch(() => [props.courseId, props.chapterId], () => { generation++; draft.value = null; rows.value = []; busy.value = false; error.value = '' })
async function generate() {
  if (busy.value) return
  if (draft.value) { try { await ElMessageBox.confirm('重新生成将替换当前未确认的编辑。', '重新生成') } catch { return } }
  const current = ++generation
  busy.value = true; error.value = ''
  try { const value = await teachingContentApi.generate(props.courseId, props.chapterId); if (current !== generation) return; draft.value = value; rows.value = value.points.map(p => ({ ...p, selected: true })) }
  catch (e) { if (current === generation) error.value = e instanceof Error ? e.message : '生成失败' }
  finally { if (current === generation) busy.value = false }
}
function merge() {
  const chosen = rows.value.filter(p => p.selected)
  if (chosen.length < 2) return ElMessage.warning('请勾选至少两个草稿')
  const first = chosen[0]!
  first.description = chosen.map(p => p.description).join('\n')
  first.sourceIds = [...new Set(chosen.flatMap(p => p.sourceIds).map(String))]
  first.manual = chosen.every(p => p.manual)
  rows.value = rows.value.filter(p => !p.selected || p === first)
}
async function confirm() {
  if (!draft.value || busy.value) return
  const current = generation; busy.value = true; error.value = ''
  try { await teachingContentApi.confirm(props.courseId, props.chapterId, draft.value.id, rows.value.filter(p => p.selected).map(({ name, description, importance, sourceIds, manual }) => ({ name, description, importance, sourceIds, manual }))); if (current !== generation) return; draft.value = null; rows.value = []; emit('confirmed'); ElMessage.success('所选知识点已正式添加') }
  catch (e) { if (current === generation) error.value = e instanceof Error ? e.message : '确认失败' }
  finally { if (current === generation) busy.value = false }
}
</script>
<template>
  <section aria-label="AI 知识点草稿">
    <el-button :loading="busy" @click="generate">{{ draft ? '重新生成知识点' : 'AI 生成知识点' }}</el-button>
    <p>仅使用本章节 READY 文档。草稿暂存 24 小时，确认前不会创建正式知识点。</p>
    <p v-if="busy">正在处理，请勿重复提交。多文档推理可能需要数分钟；默认总时限 5 分钟。</p>
    <p v-if="draft?.coverage">已提供 {{ draft.coverage.documents }} 份文档，{{ draft.coverage.selectedChunks }} / {{ draft.coverage.totalChunks }} 个片段。{{ draft.coverage.sampled ? '受上下文预算限制使用均衡抽样/截取，并非全文穷尽，请核对来源后补充。' : '所有有效片段均已提供；模型草稿仍需教师核对完整性。' }}</p>
    <p v-if="draft">当前勾选草稿引用 {{ citationCoverage.cited }} / {{ citationCoverage.total }} 份资料。<span v-if="citationCoverage.uncited.length">尚未引用：{{ citationCoverage.uncited.join('、') }}。请检查是否遗漏重要概念；资料内容重复或不含教学知识时也可能无需单独引用。</span></p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="draft"><el-button :disabled="busy" @click="merge">合并所选草稿</el-button><el-button :disabled="busy" @click="rows.push({ name: '', description: '', importance: 'IMPORTANT', sourceIds: [], manual: true, selected: true })">手动新增草稿</el-button>
      <article v-for="(row, index) in rows" :key="index" class="draft-row"><el-checkbox v-model="row.selected" :disabled="busy">确认此项</el-checkbox><el-input v-model="row.name" placeholder="知识点名称" :disabled="busy" /><el-input v-model="row.description" type="textarea" placeholder="说明" :disabled="busy" /><el-select v-model="row.importance" :disabled="busy"><el-option label="核心" value="CORE" /><el-option label="重要" value="IMPORTANT" /><el-option label="选学" value="OPTIONAL" /></el-select><el-button :disabled="busy" type="danger" link @click="rows.splice(index, 1)">删除草稿</el-button>
        <details><summary>来源引用 {{ row.manual ? '（手工补充）' : '' }}</summary><blockquote v-for="source in draft.sources.filter(s => row.sourceIds.map(String).includes(String(s.id)))" :key="source.id">{{ source.name }} · {{ source.page ? `第 ${source.page} 页` : source.section }}<p>{{ source.quote }}</p></blockquote></details>
      </article>
      <el-button type="primary" :loading="busy" :disabled="!rows.some(p => p.selected)" @click="confirm">确认添加所选知识点</el-button>
    </template>
  </section>
</template>
<style scoped>.draft-row{border:1px solid var(--el-border-color);padding:12px;margin:12px 0;display:grid;gap:8px}blockquote{white-space:pre-wrap;font-size:13px}</style>
