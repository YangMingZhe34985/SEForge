import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import KnowledgePointDraftEditor from './KnowledgePointDraftEditor.vue'
import { teachingContentApi } from '@/api/teachingContent'
vi.mock('@/api/teachingContent', () => ({ teachingContentApi: { generate: vi.fn(), confirm: vi.fn() } }))
function render() { return mount(KnowledgePointDraftEditor, { props: { courseId: '1', chapterId: '2' }, global: { plugins: [ElementPlus] } }) }
const click = async (wrapper: ReturnType<typeof render>, text: string) => { await wrapper.findAll('button').find(b => b.text() === text)!.trigger('click'); await flushPromises() }
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(teachingContentApi.generate).mockResolvedValue({ id: 'draft-1', sources: [{ id: '10', documentId: '3', chapterId: '2', name: 'lecture.pdf', page: 1, quote: '<img src=x onerror=alert(1)>' }], points: [
    { name: 'Cohesion', description: 'One', importance: 'CORE', sourceIds: ['10'], manual: false },
    { name: 'Coupling', description: 'Two', importance: 'IMPORTANT', sourceIds: ['10'], manual: false },
  ] })
  vi.mocked(teachingContentApi.confirm).mockResolvedValue(['40'])
})
describe('teacher-reviewed knowledge-point drafts', () => {
  it('shows bounded multi-document coverage rather than claiming exhaustive processing', async () => {
    vi.mocked(teachingContentApi.generate).mockResolvedValueOnce({ id: 'draft', sources: [], points: [], coverage: { documents: 3, totalChunks: 80, selectedChunks: 40, sampled: true } })
    const wrapper=render(); await click(wrapper,'AI 生成知识点')
    expect(wrapper.text()).toContain('3 份文档')
    expect(wrapper.text()).toContain('40 / 80')
    expect(wrapper.text()).toContain('并非全文穷尽')
    wrapper.unmount()
  })
  it('distinguishes supplied documents from documents cited by selected drafts', async () => {
    vi.mocked(teachingContentApi.generate).mockResolvedValueOnce({ id: 'draft', sources: [
      { id: '1', documentId: 'a', chapterId: '2', name: 'first.pdf', quote: 'one' },
      { id: '2', documentId: 'b', chapterId: '2', name: 'second.pdf', quote: 'two' },
      { id: '3', documentId: 'c', chapterId: '2', name: 'uncited.pdf', quote: 'three' },
    ], points: [{ name: 'Concept', description: 'Draft', importance: 'CORE', sourceIds: ['1','2'], manual: false }] })
    const wrapper=render(); await click(wrapper,'AI 生成知识点')
    expect(wrapper.text()).toContain('当前勾选草稿引用 2 / 3 份资料')
    expect(wrapper.text()).toContain('尚未引用：uncited.pdf')
    await click(wrapper,'删除草稿')
    expect(wrapper.text()).toContain('当前勾选草稿引用 0 / 3 份资料')
    wrapper.unmount()
  })
  it('never confirms automatically; merges edited items with citation provenance and escapes source HTML', async () => {
    const wrapper = render(); await click(wrapper, 'AI 生成知识点')
    expect(teachingContentApi.confirm).not.toHaveBeenCalled()
    expect(wrapper.findAll('.draft-row')).toHaveLength(2)
    expect(wrapper.find('img').exists()).toBe(false)
    await click(wrapper, '合并所选草稿')
    expect(wrapper.findAll('.draft-row')).toHaveLength(1)
    await wrapper.find('input[placeholder="知识点名称"]').setValue('Teacher edited')
    await click(wrapper, '确认添加所选知识点')
    expect(teachingContentApi.confirm).toHaveBeenCalledWith('1', '2', 'draft-1', [expect.objectContaining({ name: 'Teacher edited', description: 'One\nTwo', sourceIds: ['10'], manual: false })])
    expect(wrapper.emitted('confirmed')).toHaveLength(1); wrapper.unmount()
  })
  it('clears a draft on chapter switch and preserves explicit generator errors', async () => {
    const wrapper = render(); await click(wrapper, 'AI 生成知识点')
    await wrapper.setProps({ chapterId: '9' })
    expect(wrapper.findAll('.draft-row')).toHaveLength(0)
    vi.mocked(teachingContentApi.generate).mockRejectedValue(new Error('没有 READY 文档'))
    await click(wrapper, 'AI 生成知识点')
    expect(wrapper.text()).toContain('没有 READY 文档'); expect(teachingContentApi.confirm).not.toHaveBeenCalled(); wrapper.unmount()
  })
})
