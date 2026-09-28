import type { AssignmentQuestion, SubmissionAnswerInput } from '@/types/domain'

// Do not derive student defaults from teacher answerSpec (including imported boolean answers).
export function defaultStudentAnswer(question: Pick<AssignmentQuestion, 'type'>): SubmissionAnswerInput['answer'] {
  return question.type === 'MULTIPLE_CHOICE' ? [] : ''
}
