import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GradingWorkbench from './GradingWorkbench.vue'
import { reviewApi, type GradingDetail } from '@/api/reviews'
import { assignmentApi } from '@/api/assignments'
vi.mock('@/api/reviews', () => ({ reviewApi: { grading: vi.fn(), manualReview: vi.fn(), confirmGrade: vi.fn(), publish: vi.fn() } }))
vi.mock('@/api/assignments', () => ({ assignmentApi: { rubric: vi.fn() } }))
let value: GradingDetail
beforeEach(() => {
  vi.clearAllMocks()
  value = { grade: { id: 'g', courseId: 'c', assignmentId: 'a', assignmentTitle: 'Assignment', submissionId: 's', score: null, maxScore: 5, status: 'PENDING_CONFIRMATION', suggestedScore: 5, rubricItems: [{ questionId: 'q', source: 'RULE', suggestedScore: 5 }] },
    assignment: { id: 'a', courseId: 'c', title: 'Assignment', status: 'PUBLISHED', questions: [{ id: 'q', type: 'TRUE_FALSE', prompt: '判断题', points: 5, orderIndex: 0, config: { answerSpec: { correct: true } } }] },
    submission: { id: 's', assignmentId: 'a', status: 'SUBMITTED', attemptNumber: 1, updatedAt: '', answers: [{ questionId: 'q', answer: true }] }, targets: [{ questionId: 'q', maximum: 5 }] }
  vi.mocked(reviewApi.grading).mockImplementation(async () => structuredClone(value))
  vi.mocked(assignmentApi.rubric).mockResolvedValue(null)
})
const render = (manualOnly = false) => mount(GradingWorkbench, { props: { submissionId: 's', canConfirm: true, manualOnly }, global: { plugins: [ElementPlus], stubs: { AssignmentMediaList: true } } })
describe('grading workspace separation', () => {
  it('shows no final grade as pending, confirms without publishing, then explicitly publishes', async () => {
    const w = render(); await flushPromises()
    expect(w.text()).toContain('待确认 / —'); expect(w.text()).toContain('教师参考'); expect(w.text()).toContain('RULE')
    vi.mocked(reviewApi.confirmGrade).mockImplementation(async () => { value.grade.status = 'CONFIRMED'; value.grade.score = 5; return value.grade })
    await w.findAll('button').find(b => b.text() === '确认最终成绩')!.trigger('click'); await flushPromises()
    expect(reviewApi.publish).not.toHaveBeenCalled()
    expect(w.text()).toContain('CONFIRMED')
    await w.findAll('button').find(b => b.text() === '发布成绩给学生')!.trigger('click'); await flushPromises()
    expect(reviewApi.publish).toHaveBeenCalledWith('s'); w.unmount()
  })
  it('manual-only mode never offers final confirmation and does not assume missing scores are zero', async () => {
    value.grade.rubricItems = []; value.grade.suggestedScore = null; value.grade.status = 'WAITING_REVIEW'
    const w = render(true); await flushPromises()
    expect(w.text()).toContain('待逐项评分 / —')
    expect(w.text()).not.toContain('确认最终成绩')
    const save = w.findAll('button').find(b => b.text() === '保存人工初评')!
    expect(save.attributes('disabled')).toBeDefined()
    await w.findComponent({ name: 'ElInputNumber' }).vm.$emit('update:modelValue', 0); await flushPromises()
    await save.trigger('click'); await flushPromises()
    expect(reviewApi.manualReview).toHaveBeenCalledWith('s', 0, '', '', [{ questionId: 'q', rubricItemId: undefined, score: 0, feedback: '' }])
    expect(reviewApi.confirmGrade).not.toHaveBeenCalled(); w.unmount()
  })
  it('fails closed when detail authorization is denied', async () => {
    vi.mocked(reviewApi.grading).mockRejectedValue(new Error('Forbidden'))
    const w = render(); await flushPromises(); expect(w.text()).toContain('Forbidden'); expect(w.text()).not.toContain('教师参考'); w.unmount()
  })
})
