import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, it, expect, vi } from 'vitest'
import TypedQuestionFields, { type QuestionDraft } from './TypedQuestionFields.vue'
import { defaultChoices, choiceText, optionLabel } from './choiceOptions'
import { assignmentApi } from '@/api/assignments'
import { flushPromises } from '@vue/test-utils'
vi.mock('@/api/assignments', () => ({ assignmentApi: { media: vi.fn(), mediaBlob: vi.fn(), uploadMedia: vi.fn(), referenceMarkdown: vi.fn() } }))
function render(type: QuestionDraft['type']) {
  return mount(TypedQuestionFields, { props: { assignmentId: '1', knowledgePoints: [], modelValue: {
    type, prompt: '# Requirement', optionsText: '', referenceAnswer: '', points: 10, orderIndex: 0, knowledgePointId: '',
    config: { schemaVersion: 1, answerSpec: { correct: type === 'TRUE_FALSE' ? false : 'A', allowedFileTypes: ['pdf'] }, choices: [{ id: 'A', label: 'Alpha' }, { id: 'B', label: 'Beta' }] },
  } }, global: { plugins: [ElementPlus] } })
}
describe('typed question editor', () => {
  it('scopes pasted and uploaded reference images to REFERENCE_ANSWER, never content', async () => {
    vi.mocked(assignmentApi.uploadMedia).mockImplementation(async (_a, purpose, file) => ({ id: purpose === 'QUESTION_CONTENT' ? '1' : file.name === 'r1.png' ? '2' : '3', fileName: file.name, mediaType: file.type, sizeBytes: 10 }))
    const w = render('SHORT_ANSWER')
    const paste = (name: string) => ({ clipboardData: { files: [new File(['png'], name, { type: 'image/png' })] } })
    await w.findAll('textarea')[0]!.trigger('paste', paste('q.png')); await flushPromises()
    await w.findAll('textarea')[1]!.trigger('paste', paste('r1.png')); await flushPromises()
    const input = w.findAll('input[type="file"]')[1]!
    Object.defineProperty(input.element, 'files', { value: [new File(['png'], 'r2.png', { type: 'image/png' })] })
    await input.trigger('change'); await flushPromises()
    expect(w.props('modelValue').config.assetIds).toEqual(['1'])
    expect(w.props('modelValue').config.answerSpec?.assetIds).toEqual(['2','3'])
    expect(vi.mocked(assignmentApi.uploadMedia).mock.calls.slice(-3).map(c => c[1])).toEqual(['QUESTION_CONTENT','REFERENCE_ANSWER','REFERENCE_ANSWER'])
    w.unmount()
  })
  it('keeps original reference and image until the teacher explicitly accepts OCR Markdown', async () => {
    vi.mocked(assignmentApi.referenceMarkdown).mockResolvedValue({ markdown: '# Recognized answer', warning: 'Verify original image' })
    const wrapper = render('SHORT_ANSWER')
    const form = wrapper.props('modelValue')
    form.referenceAnswer = 'Original reference'
    form.config.answerSpec!.assetIds = ['100']
    await wrapper.setProps({ modelValue: { ...form } })
    await wrapper.findAll('button').find(b => b.text().includes('识别为 Markdown'))!.trigger('click')
    await flushPromises()
    expect(wrapper.props('modelValue').referenceAnswer).toBe('Original reference')
    const button = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.includes('确认使用草稿'))
    expect(button).toBeDefined(); button!.click(); await flushPromises()
    expect(wrapper.props('modelValue').referenceAnswer).toBe('# Recognized answer')
    expect(form.config.answerSpec!.assetIds).toEqual(['100'])
    wrapper.unmount()
  })
  it('initializes A-D when changing to either choice type', async () => {
    for (const type of ['SINGLE_CHOICE','MULTIPLE_CHOICE'] as const) {
      const wrapper = render('SHORT_ANSWER')
      const form = wrapper.props('modelValue')
      form.type = type
      await wrapper.setProps({ modelValue: { ...form } })
      wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('change', type)
      await wrapper.vm.$nextTick()
      expect(wrapper.findAll('.choice-label').map(n => n.text())).toEqual(['A','B','C','D'])
      expect(form.config.answerSpec?.correct).toBeUndefined()
      wrapper.unmount()
    }
  })
  it('defaults to four stable options and derives labels without exposing IDs', () => {
    const choices = defaultChoices()
    expect(choices).toHaveLength(4)
    expect(new Set(choices.map(c => c.id)).size).toBe(4)
    expect(choices.map((c, i) => choiceText({ choices }, c.id, i))).toEqual(['A. ', 'B. ', 'C. ', 'D. '])
    expect(optionLabel(26)).toBe('AA')
  })
  it('relabels after add/move/remove, preserves selected ID on reorder and clears deleted selection', async () => {
    const wrapper = render('SINGLE_CHOICE')
    const form = wrapper.props('modelValue')
    form.config.choices = defaultChoices()
    form.config.answerSpec!.correct = form.config.choices[0]!.id
    await wrapper.setProps({ modelValue: { ...form } })
    const selected = form.config.answerSpec!.correct
    await wrapper.findAll('button').find(b => b.text() === '添加选项')!.trigger('click')
    expect(wrapper.findAll('.choice-label').map(n => n.text())).toEqual(['A','B','C','D','E'])
    await wrapper.findAll('.choice-row')[0]!.findAll('button').find(b => b.text() === '下移')!.trigger('click')
    expect(form.config.choices[1]!.id).toBe(selected)
    expect(form.config.answerSpec!.correct).toBe(selected)
    await wrapper.findAll('.choice-row')[1]!.findAll('button').find(b => b.text() === '移除')!.trigger('click')
    expect(form.config.answerSpec!.correct).toBeUndefined()
    expect(wrapper.findAll('.choice-label').map(n => n.text())).toEqual(['A','B','C','D'])
    expect(wrapper.text()).not.toContain('opt_')
    wrapper.unmount()
  })
  it('offers stable choices and RULE grading without subjective reference field', async () => {
    const wrapper = render('SINGLE_CHOICE')
    expect(wrapper.text()).toContain('正确选项')
    expect(wrapper.text()).toContain('RULE')
    expect(wrapper.text()).not.toContain('参考答案 / 评分要求')
    await wrapper.findAll('button').find(button => button.text() === '添加选项')!.trigger('click')
    expect(wrapper.findAll('input[placeholder="选项内容"]')).toHaveLength(3)
    wrapper.unmount()
  })
  it('shows boolean, report and code-specific controls', () => {
    for (const [type, expected] of [['TRUE_FALSE', '标准答案'], ['DOCUMENT_REPORT','允许提交的文档类型'], ['CODE','输入输出示例']] as const) {
      const wrapper = render(type); expect(wrapper.text()).toContain(expected); wrapper.unmount()
    }
  })
  it('renders Markdown and offers original media for subjective types', () => {
    for (const type of ['SHORT_ANSWER','ANALYSIS','DESIGN'] as const) {
      const wrapper = render(type)
      expect(wrapper.find('h1').text()).toBe('Requirement')
      expect(wrapper.text()).toContain('参考答案图片 / 文件')
      expect(wrapper.findAll('input[type="file"]')).toHaveLength(2)
      wrapper.unmount()
    }
  })
})
