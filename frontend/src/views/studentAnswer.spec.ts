import { describe, expect, it } from 'vitest'
import { defaultStudentAnswer } from './studentAnswer'

describe('student answers', () => {
  it('never preselects either boolean, irrespective of the imported standard answer', () => {
    for (const correct of [true, false]) {
      const question = { type: 'TRUE_FALSE' as const, config: { answerSpec: { correct } } }
      expect(defaultStudentAnswer(question)).toBe('')
      expect(defaultStudentAnswer(question)).not.toBe(true)
      expect(defaultStudentAnswer(question)).not.toBe(false)
    }
  })
  it('keeps explicitly saved false and uses independent empty choice arrays', () => {
    const saved = false
    expect(saved ?? defaultStudentAnswer({ type: 'TRUE_FALSE' })).toBe(false)
    expect(defaultStudentAnswer({ type: 'MULTIPLE_CHOICE' })).toEqual([])
    expect(defaultStudentAnswer({ type: 'MULTIPLE_CHOICE' })).not.toBe(defaultStudentAnswer({ type: 'MULTIPLE_CHOICE' }))
  })
})
