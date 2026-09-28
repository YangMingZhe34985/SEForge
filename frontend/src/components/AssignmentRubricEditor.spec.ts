import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import AssignmentRubricEditor from './AssignmentRubricEditor.vue'
import { allocation } from './rubricAllocation'
import { assignmentApi } from '@/api/assignments'
import type { AssignmentQuestion, AssignmentRubric } from '@/types/domain'

vi.mock('@/api/assignments', () => ({ assignmentApi: { saveRubric: vi.fn(), addRubricItem: vi.fn(), deleteRubricItem: vi.fn() } }))
const question = (id: string, points: number, type: AssignmentQuestion['type'] = 'SHORT_ANSWER'): AssignmentQuestion => ({ id, points, type, prompt: `题目 ${id}`, orderIndex: 0 })
const rubric = (): AssignmentRubric => ({ id: 'r', assignmentId: '1', title: '评分规则', totalScore: 7, status: 'DRAFT', items: [] })
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(assignmentApi.saveRubric).mockResolvedValue(rubric())
  vi.mocked(assignmentApi.addRubricItem).mockImplementation(async (_id, item) => ({ id: 'i', ...item }))
})
function render(questions: AssignmentQuestion[], modelValue: AssignmentRubric | null = null) {
  const wrapper = mount(AssignmentRubricEditor, { props: { assignmentId: '1', questions, editable: true, modelValue,
    'onUpdate:modelValue': value => { void wrapper.setProps({ modelValue: value }) } }, global: { plugins: [ElementPlus] } })
  return wrapper
}
describe('rubric allocation by grading mode', () => {
  it('labels AI criteria clearly and disables adding after the entire score is allocated', async () => {
    const value = rubric(); value.items = [{ id:'i',questionId:'1',title:'设计完整性',description:'覆盖所有模块',maxScore:7,orderIndex:0 }]
    const w = render([question('1',7)],value)
    await w.findComponent({ name:'ElSelect' }).vm.$emit('update:modelValue','1'); await flushPromises()
    for (const label of ['AI 评分准则','评分维度名称','AI 判断依据','分值','简单综合评分','分项评分','本题总分已全部分配','覆盖所有模块']) expect(w.text()).toContain(label)
    const add = w.findAll('button').find(b => b.text() === '添加分项')!
    expect(add.attributes('disabled')).toBeDefined()
    await add.trigger('click'); expect(assignmentApi.addRubricItem).not.toHaveBeenCalled()
    w.unmount()
  })
  it('objective-only UI needs no rubric editor or API call', () => {
    const wrapper = render([question('1', 4, 'SINGLE_CHOICE'), question('2', 3, 'TRUE_FALSE')])
    expect(wrapper.text()).toContain('无需配置 Rubric')
    expect(wrapper.findAll('button')).toHaveLength(0)
    expect(assignmentApi.saveRubric).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('selecting questions uses their remaining allocation and blocks over-allocation', async () => {
    const value = rubric(); value.items = [{ id: 'i', questionId: '1', title: '已有分项', maxScore: 2.5, orderIndex: 0 }]
    const wrapper = render([question('1', 7), question('2', 3.2)], value)
    const selector = wrapper.findComponent({ name: 'ElSelect' })
    await selector.vm.$emit('update:modelValue', '1'); await flushPromises()
    const score = wrapper.findComponent({ name: 'ElInputNumber' })
    expect(score.props('modelValue')).toBe(4.5)
    expect(wrapper.text()).toContain('已分配 2.5 / 7')
    expect(wrapper.text()).toContain('未完成')
    await score.vm.$emit('update:modelValue', 5); await flushPromises()
    expect(score.props('modelValue')).toBe(4.5) // Element Plus clamps input to the remaining allocation.
    expect(assignmentApi.addRubricItem).not.toHaveBeenCalled()
    await selector.vm.$emit('update:modelValue', '2'); await flushPromises()
    expect(score.props('modelValue')).toBe(3.2)
    wrapper.unmount()
  })
  it('quick default uses the selected score and enables publication only after exact allocation', async () => {
    const wrapper = render([question('1', 7)])
    await wrapper.findAll('button').find(b => b.text() === '综合评分 / 7')!.trigger('click'); await flushPromises()
    expect(assignmentApi.addRubricItem).toHaveBeenCalledWith('1', expect.objectContaining({ questionId: '1', maxScore: 7, title: '综合评分' }))
    expect(wrapper.text()).toContain('已分配 7 / 7')
    const publish = wrapper.findAll('button').find(b => b.text() === '保存并发布评分规则')!
    expect(publish.attributes('disabled')).toBeUndefined()
    await publish.trigger('click'); await flushPromises()
    expect(assignmentApi.saveRubric).toHaveBeenLastCalledWith('1', expect.objectContaining({ totalScore: 7, status: 'PUBLISHED' }))
    wrapper.unmount()
  })
  it('manual allocation is optional and decimal totals compare exactly', () => {
    const q = { ...question('1', 0.3), config: { gradingMode: 'MANUAL' as const } }
    expect(allocation(q, []).incomplete).toBe(false)
    const items = [0.1, 0.2].map((maxScore, i) => ({ id: String(i), questionId: '1', title: 'Part', maxScore, orderIndex: i }))
    expect(allocation(q, items)).toEqual({ assigned: 0.3, remaining: 0, over: false, incomplete: false })
    const wrapper = render([q]); expect(wrapper.text()).toContain('Rubric 可选'); wrapper.unmount()
  })
})
