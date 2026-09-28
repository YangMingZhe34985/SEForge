<script setup lang="ts">
import type { AssignmentQuestion } from '@/types/domain'
import { computed } from 'vue'
import { optionLabel } from './choiceOptions'
import { gradingMode } from './rubricAllocation'
import SafeMarkdown from './SafeMarkdown.vue'
import AssignmentMediaList from './AssignmentMediaList.vue'
const props = defineProps<{ question: AssignmentQuestion; assignmentId: string }>()
const standard = computed(() => {
  const q = props.question, answer = q.config?.answerSpec?.correct
  if (q.type === 'TRUE_FALSE') return answer == null ? q.referenceAnswer || '未设置' : answer ? '正确 / True' : '错误 / False'
  if (q.type === 'SINGLE_CHOICE' || q.type === 'MULTIPLE_CHOICE') {
    if (answer == null) return q.referenceAnswer || '未设置'
    const ids = Array.isArray(answer) ? answer : [String(answer)]
    return ids.map(id => { const index = q.config?.choices?.findIndex(c => c.id === id) ?? -1; return index >= 0 ? `${optionLabel(index)}. ${q.config?.choices?.[index]?.label || ''}` : '标准选项无效，请检查配置' }).join('；')
  }
  return q.referenceAnswer || '未设置文本参考答案'
})
</script>
<template><aside class="reference"><strong>教师参考 · {{ gradingMode(question) }}</strong><SafeMarkdown :content="standard" /><AssignmentMediaList :assignment-id="assignmentId" :ids="question.config?.answerSpec?.assetIds" /></aside></template>
<style scoped>.reference{padding:12px;border:1px dashed var(--line);margin:10px 0;background:var(--el-fill-color-light)}</style>
