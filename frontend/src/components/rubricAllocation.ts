import type { AssignmentQuestion, RubricItem } from '@/types/domain'

export function gradingMode(question: AssignmentQuestion): 'RULE' | 'AI_ASSISTED' | 'MANUAL' {
  if (['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(question.type)) return 'RULE'
  return question.config?.gradingMode === 'MANUAL' ? 'MANUAL' : 'AI_ASSISTED'
}

export function allocation(question: AssignmentQuestion, items: RubricItem[]) {
  const assigned = items.filter(item => item.questionId === question.id).reduce((sum, item) => sum + Math.round(item.maxScore * 100), 0)
  const maximum = Math.round(question.points * 100)
  return { assigned: assigned / 100, remaining: Math.max(0, maximum - assigned) / 100,
    over: assigned > maximum, incomplete: gradingMode(question) === 'AI_ASSISTED' && assigned < maximum }
}
