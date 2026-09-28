<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { assignmentApi } from '@/api/assignments'
import AssignmentMediaList from './AssignmentMediaList.vue'
import SafeMarkdown from './SafeMarkdown.vue'
import { defaultChoices, newChoice, optionLabel, choiceText } from './choiceOptions'
import type { QuestionType, QuestionContentConfig, KnowledgePoint } from '@/types/domain'
export interface QuestionDraft {
  type: QuestionType; prompt: string; optionsText: string; referenceAnswer: string; points: number
  orderIndex: number; knowledgePointId: string; config: QuestionContentConfig
}
const form = defineModel<QuestionDraft>({ required: true })
const props = defineProps<{ assignmentId: string; knowledgePoints: KnowledgePoint[]; hideScore?: boolean }>()
const uploading = ref(false)
const referenceDraft = ref('')
const referenceWarning = ref('')
const referenceVisible = ref(false)
const recognizing = ref(false)
watch(() => [props.assignmentId, form.value.config], () => { referenceVisible.value = false })
async function recognizeReference(id: string) {
  const target = form.value.config
  const assignment = props.assignmentId
  recognizing.value = true
  try {
    const result = await assignmentApi.referenceMarkdown(assignment, id)
    if (target !== form.value.config || assignment !== props.assignmentId) return
    referenceDraft.value = result.markdown; referenceWarning.value = result.warning; referenceVisible.value = true
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '图片识别失败') }
  finally { recognizing.value = false }
}
const objective = computed(() => ['SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE'].includes(form.value.type))
const choices = computed(() => ['SINGLE_CHOICE','MULTIPLE_CHOICE'].includes(form.value.type))
function changeType() {
  form.value.config.gradingMode = objective.value ? 'RULE' : 'AI_ASSISTED'
  form.value.config.answerSpec = { assetIds: [], ...(form.value.type === 'TRUE_FALSE' ? { correct: false } : {}),
    ...(form.value.type === 'DOCUMENT_REPORT' ? { allowedFileTypes: ['pdf','docx'] } : {}) }
  form.value.config.choices = choices.value ? defaultChoices() : undefined
}
function addOption() { (form.value.config.choices ||= []).push(newChoice()) }
function removeOption(index: number) {
  const [removed] = form.value.config.choices!.splice(index, 1)
  const spec = form.value.config.answerSpec!
  if (Array.isArray(spec.correct)) spec.correct = spec.correct.filter(id => id !== removed?.id)
  else if (spec.correct === removed?.id) delete spec.correct
}
function moveOption(index: number, direction: number) {
  const options = form.value.config.choices!
  const target = index + direction
  if (target < 0 || target >= options.length) return
  const [option] = options.splice(index, 1)
  options.splice(target, 0, option!)
}
async function upload(file: File | undefined, reference = false) {
  if (!file) return
  const target = form.value.config
  const assignment = props.assignmentId
  uploading.value = true
  try {
    const saved = await assignmentApi.uploadMedia(assignment, reference ? 'REFERENCE_ANSWER' : 'QUESTION_CONTENT', file)
    if (target !== form.value.config || assignment !== props.assignmentId) return
    if (reference) (target.answerSpec!.assetIds ||= []).push(saved.id)
    else (target.assetIds ||= []).push(saved.id)
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '上传失败') }
  finally { uploading.value = false }
}
function paste(event: ClipboardEvent, reference = false) {
  const image = Array.from(event.clipboardData?.files || []).find(file => file.type.startsWith('image/'))
  if (image) { event.preventDefault(); void upload(image, reference) }
}
</script>
<template>
  <el-form label-position="top" class="typed-question">
    <div class="fields"><el-form-item label="题型" class="type-field"><el-select class="question-type-select" v-model="form.type" @change="changeType"><el-option v-for="value in (['SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE','SHORT_ANSWER','ANALYSIS','DESIGN','CODE','DOCUMENT_REPORT'] as const)" :key="value" :label="value" :value="value" /></el-select></el-form-item><el-form-item v-if="!hideScore" label="分值"><el-input-number v-model="form.points" :min="0.01" :precision="2" /></el-form-item></div>
    <el-form-item :label="form.type === 'DOCUMENT_REPORT' ? '报告任务说明（Markdown）' : '题目（Markdown，可粘贴图片）'"><el-input v-model="form.prompt" type="textarea" :rows="5" @paste.stop="paste($event)" /></el-form-item>
    <SafeMarkdown :content="form.prompt" />
    <label>上传题目图片 / 材料 / 模板 <input type="file" accept=".png,.jpg,.jpeg,.pdf,.docx,.md,.txt" :disabled="uploading" @change="upload(($event.target as HTMLInputElement).files?.[0])" /></label>
    <AssignmentMediaList :assignment-id="assignmentId" :ids="form.config.assetIds" /><el-button v-for="(id,index) in form.config.assetIds" :key="id" link @click="form.config.assetIds?.splice(index,1)">移除题干附件 {{ index + 1 }}</el-button>
    <div v-if="choices"><label>选项（显示序号随排列自动更新）</label><div v-for="(option,index) in form.config.choices" :key="option.id" class="fields choice-row"><span class="choice-label">{{ optionLabel(index) }}</span><el-input v-model="option.label" placeholder="选项内容" /><el-button :disabled="index === 0" @click="moveOption(index,-1)">上移</el-button><el-button :disabled="index === (form.config.choices?.length || 0)-1" @click="moveOption(index,1)">下移</el-button><el-button @click="removeOption(index)">移除</el-button></div><el-button @click="addOption">添加选项</el-button>
      <el-form-item label="正确选项"><el-select v-if="form.type === 'SINGLE_CHOICE'" v-model="form.config.answerSpec!.correct"><el-option v-for="(option,index) in form.config.choices" :key="option.id" :value="option.id" :label="choiceText(form.config,option.id,index)" /></el-select><el-select v-else v-model="form.config.answerSpec!.correct" multiple><el-option v-for="(option,index) in form.config.choices" :key="option.id" :value="option.id" :label="choiceText(form.config,option.id,index)" /></el-select></el-form-item>
    </div>
    <el-form-item v-if="form.type === 'TRUE_FALSE'" label="标准答案"><el-radio-group v-model="form.config.answerSpec!.correct"><el-radio :value="true">正确</el-radio><el-radio :value="false">错误</el-radio></el-radio-group></el-form-item>
    <p v-if="objective">客观题使用 RULE 精确匹配评分，不调用模型评分；最终成绩仍需教师确认。</p>
    <el-form-item v-else label="评分模式"><el-select v-model="form.config.gradingMode" placeholder="AI_ASSISTED"><el-option label="AI 辅助建议 + 教师确认" value="AI_ASSISTED" /><el-option label="MANUAL · 教师人工评分" value="MANUAL" /></el-select><p>仅图片题干或仅图片参考答案会由服务端设为 MANUAL；原图保留，不强迫 AI 评审。</p></el-form-item>
    <div v-if="!objective"><el-button v-for="(id,index) in form.config.answerSpec?.assetIds" :key="id" :loading="recognizing" @click="recognizeReference(id)">参考附件 {{ index + 1 }}：识别为 Markdown（仅图片）</el-button></div>
    <template v-if="!objective"><el-form-item label="参考答案 / 评分要求（仅教师可见）"><el-input v-model="form.referenceAnswer" type="textarea" :rows="4" @paste.stop="paste($event,true)" /></el-form-item><label>参考答案图片 / 文件 <input type="file" accept=".png,.jpg,.jpeg,.pdf,.docx,.md,.txt" :disabled="uploading" @change="upload(($event.target as HTMLInputElement).files?.[0],true)" /></label><AssignmentMediaList :assignment-id="assignmentId" :ids="form.config.answerSpec?.assetIds" /><el-button v-for="(id,index) in form.config.answerSpec?.assetIds" :key="id" link @click="form.config.answerSpec?.assetIds?.splice(index,1)">移除参考附件 {{ index + 1 }}</el-button><p>保存题目后，为本题添加 Rubric 分项；主观题由 AI 建议，教师确认。</p></template>
    <template v-if="form.type === 'CODE'"><el-form-item label="语言"><el-select v-model="form.config.language"><el-option v-for="language in ['Java','Python','JavaScript','TypeScript','C','C++','C#']" :key="language" :label="language" :value="language" /></el-select></el-form-item><el-form-item label="约束"><el-input v-model="form.config.constraints" type="textarea" /></el-form-item><el-form-item label="输入输出示例（Markdown）"><el-input v-model="form.config.examples" type="textarea" /></el-form-item><p>学生可输入代码或上传源码 ZIP；仅做静态分析，不执行代码。</p></template>
    <el-form-item v-if="form.type === 'DOCUMENT_REPORT'" label="允许提交的文档类型"><el-checkbox-group v-model="form.config.answerSpec!.allowedFileTypes"><el-checkbox v-for="ext in ['pdf','docx','md','txt']" :key="ext" :value="ext">{{ ext }}</el-checkbox></el-checkbox-group></el-form-item>
    <el-button @click="(form.config.codeBlocks ||= []).push({language:'java',code:''})">添加题干代码块</el-button>
    <div v-for="(block,index) in form.config.codeBlocks" :key="index"><el-input v-model="block.language" placeholder="语言" /><el-input v-model="block.code" type="textarea" :rows="4" placeholder="示例代码" /><el-button @click="form.config.codeBlocks?.splice(index,1)">移除代码块</el-button></div>
    <div class="fields"><el-form-item label="知识点"><el-select v-model="form.config.knowledgePointIds" multiple><el-option v-for="point in knowledgePoints" :key="point.id" :label="point.title" :value="point.id" /></el-select></el-form-item><el-form-item label="排序"><el-input-number v-model="form.orderIndex" :min="0" /></el-form-item></div>
  </el-form>
  <el-dialog v-model="referenceVisible" title="参考答案 Markdown 草稿（尚未覆盖原答案）" append-to-body><el-alert :title="referenceWarning" type="warning" :closable="false" /><el-input v-model="referenceDraft" type="textarea" :rows="10" /><SafeMarkdown :content="referenceDraft" /><template #footer><el-button @click="referenceVisible = false">放弃</el-button><el-button type="primary" @click="form.referenceAnswer = referenceDraft; referenceVisible = false">确认使用草稿</el-button></template></el-dialog>
</template>
<style scoped>
.fields{display:flex;flex-wrap:wrap;gap:12px;align-items:center;margin:10px 0}
.fields>*{min-width:0}.type-field{flex:1 1 300px;min-width:min(100%,280px)}
.question-type-select{width:100%;--el-select-width:100%}
.typed-question :deep(.el-select__selected-item){white-space:normal;overflow-wrap:anywhere}
.typed-question :deep(.el-select__wrapper){min-height:32px;height:auto}
.fields>.el-form-item{max-width:100%}.choice-row>.el-input{flex:1 1 200px}
label,p{font-size:13px}input[type=file]{max-width:100%}
</style>
