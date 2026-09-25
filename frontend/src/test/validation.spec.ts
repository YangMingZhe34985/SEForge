import { describe, expect, it } from 'vitest'
import { USERNAME_RULE, fieldErrors } from '@/api/validation'

describe('platform account validation contract', () => {
  it.each(['alice', 'User_01', 'teacher.name-2', 'a'.repeat(64)])('accepts %s', value => {
    expect(USERNAME_RULE.test(value)).toBe(true)
  })
  it.each(['中文用户名', 'ab', 'a'.repeat(65), 'user name', 'user@school', ' user'])('rejects invalid username %s', value => {
    expect(USERNAME_RULE.test(value)).toBe(false)
  })
  it('preserves field messages but rejects unstructured details', () => {
    expect(fieldErrors({ username: 'Invalid format', nested: { secret: 'hidden' } })).toEqual({ username: 'Invalid format' })
    expect(fieldErrors('internal exception')).toEqual({})
  })
})
