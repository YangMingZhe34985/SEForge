import { beforeEach, describe, expect, it } from 'vitest'
import { forgetDraft, forgetSubmissionKey, recoverDraft, rememberDraft, submissionKey } from './assignmentDraftCache'

describe('assignment draft recovery', () => {
  beforeEach(() => localStorage.clear())

  it('keeps the final edit across reload and isolates user and assignment', () => {
    rememberDraft('student-1', 'assignment-1', [{ questionId: 'q-1', answer: 'first' }])
    rememberDraft('student-1', 'assignment-1', [{ questionId: 'q-1', answer: 'last edit' }])
    expect(recoverDraft('student-1', 'assignment-1')).toEqual([
      { questionId: 'q-1', answer: 'last edit' },
    ])
    expect(recoverDraft('student-2', 'assignment-1')).toBeNull()
    expect(recoverDraft('student-1', 'assignment-2')).toBeNull()
    forgetDraft('student-1', 'assignment-1')
    expect(recoverDraft('student-1', 'assignment-1')).toBeNull()
  })

  it('reuses the same submission key until authoritative success clears it', () => {
    const first = submissionKey('student-1', 'assignment-1')
    expect(submissionKey('student-1', 'assignment-1')).toBe(first)
    expect(submissionKey('student-2', 'assignment-1')).not.toBe(first)
    forgetSubmissionKey('student-1', 'assignment-1')
    expect(submissionKey('student-1', 'assignment-1')).not.toBe(first)
  })
})
