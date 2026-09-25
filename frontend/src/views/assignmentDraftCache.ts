import type { SubmissionAnswerInput } from '@/types/domain'

const PREFIX = 'seforge:assignment-draft:v1:'

function key(userId: string, assignmentId: string): string {
  return `${PREFIX}${userId}:${assignmentId}`
}

export function rememberDraft(userId: string, assignmentId: string, answers: SubmissionAnswerInput[]): void {
  try {
    localStorage.setItem(key(userId, assignmentId), JSON.stringify(answers))
  } catch {
    // Network autosave remains available when browser storage is disabled.
  }
}

export function recoverDraft(userId: string, assignmentId: string): SubmissionAnswerInput[] | null {
  try {
    const value = localStorage.getItem(key(userId, assignmentId))
    if (!value) return null
    const parsed: unknown = JSON.parse(value)
    return Array.isArray(parsed) && parsed.every((answer) =>
      answer && typeof answer === 'object' && typeof answer.questionId === 'string'
      && Object.prototype.hasOwnProperty.call(answer, 'answer'))
      ? parsed as SubmissionAnswerInput[] : null
  } catch {
    return null
  }
}

export function forgetDraft(userId: string, assignmentId: string): void {
  try {
    localStorage.removeItem(key(userId, assignmentId))
  } catch {
    // A blocked storage backend does not prevent normal submission.
  }
}

export function submissionKey(userId: string, assignmentId: string): string {
  const storageKey = `${PREFIX}submit:${userId}:${assignmentId}`
  try {
    const existing = localStorage.getItem(storageKey)
    if (existing) return existing
    const value = crypto.randomUUID()
    localStorage.setItem(storageKey, value)
    return value
  } catch {
    return crypto.randomUUID()
  }
}

export function forgetSubmissionKey(userId: string, assignmentId: string): void {
  try {
    localStorage.removeItem(`${PREFIX}submit:${userId}:${assignmentId}`)
  } catch {
    // A completed submission remains complete even if storage is blocked.
  }
}
