import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import QuestionImportEditor from './QuestionImportEditor.vue'
import { assignmentApi } from '@/api/assignments'
vi.mock('@/api/assignments', () => ({ assignmentApi: { uploadMedia: vi.fn(), extractQuestions: vi.fn(), confirmImport: vi.fn(), media: vi.fn() } }))
const fixture = () => ({ id: '1', sourceFile: '10', sourceName: 'exam.md', confirmed: false, questions: [
  { draftKey: 'draft-one', type: 'SHORT_ANSWER' as const, contentMarkdown: 'Explain cohesion', choices: [], correctAnswer: null, referenceAnswer: '', score: null, order: 0, sourcePage: 1, sourceRegion: [], warnings: ['未识别分值'] },
] })
beforeEach(() => { vi.clearAllMocks(); vi.mocked(assignmentApi.uploadMedia).mockResolvedValue({ id: '10', fileName: 'exam.md', mediaType: 'text/markdown', sizeBytes: 100 }); vi.mocked(assignmentApi.extractQuestions).mockResolvedValue(fixture()); vi.mocked(assignmentApi.confirmImport).mockResolvedValue([]) })
async function render() {
  const wrapper = mount(QuestionImportEditor, { props: { assignmentId: '1', knowledgePoints: [] }, global: { plugins: [ElementPlus], stubs: { AssignmentMediaList: true } } })
  const input = wrapper.find('input[type="file"]')
  Object.defineProperty(input.element, 'files', { value: [new File(['Q1'], 'exam.md', { type: 'text/markdown' })] })
  await input.trigger('change'); await wrapper.findAll('button').find(b => b.text() === '解析题目')!.trigger('click'); await flushPromises()
  return wrapper
}
describe('teacher-confirmed question import', () => {
  it('accepts three ordered images, preserves unaffected edits on single-image reparse and removes one source', async () => {
    const w = mount(QuestionImportEditor, { props: { assignmentId:'1',knowledgePoints:[] },global:{plugins:[ElementPlus],stubs:{AssignmentMediaList:true}} })
    vi.mocked(assignmentApi.uploadMedia).mockImplementation(async (_a,_p,file) => ({ id:file.name,fileName:file.name,mediaType:file.type,sizeBytes:10 }))
    const result = { ...fixture(),sources:[{id:'two.png',name:'two.png'},{id:'one.png',name:'one.png'},{id:'three.png',name:'three.png'}],questions:['two.png','one.png','three.png'].map((id,i) => ({...fixture().questions[0]!,draftKey:id,sourceFile:id,order:i,score:5})) }
    vi.mocked(assignmentApi.extractQuestions).mockResolvedValue(result)
    const input=w.find('input[type="file"]')
    Object.defineProperty(input.element,'files',{value:['one.png','two.png','three.png'].map(name=>new File(['png'],name,{type:'image/png'}))})
    await input.trigger('change')
    await w.findAll('.source-list li')[1]!.findAll('button').find(b=>b.text()==='图片上移')!.trigger('click')
    await w.findAll('button').find(b=>b.text()==='解析题目')!.trigger('click'); await flushPromises()
    expect(assignmentApi.extractQuestions).toHaveBeenCalledWith('1',['two.png','one.png','three.png'],false,undefined)
    await w.findAll('.import-question')[0]!.find('textarea').setValue('Keep teacher edit')
    vi.mocked(assignmentApi.extractQuestions).mockResolvedValue({...result,questions:result.questions.map(q=>q.sourceFile==='one.png'?{...q,draftKey:'new-one',contentMarkdown:'Reparsed one'}:q)})
    await w.findAll('.source-list li')[1]!.findAll('button').find(b=>b.text().startsWith('重新解析此图'))!.trigger('click'); await flushPromises()
    expect(assignmentApi.extractQuestions).toHaveBeenLastCalledWith('1',['two.png','one.png','three.png'],true,'one.png')
    expect((w.findAll('.import-question')[0]!.find('textarea').element as HTMLTextAreaElement).value).toBe('Keep teacher edit')
    expect((w.findAll('.import-question')[1]!.find('textarea').element as HTMLTextAreaElement).value).toBe('Reparsed one')
    await w.findAll('.source-list li')[2]!.findAll('button').find(b=>b.text()==='删除来源')!.trigger('click')
    expect(w.findAll('.import-question')).toHaveLength(2)
    expect(assignmentApi.confirmImport).not.toHaveBeenCalled(); w.unmount()
  })
  it('rejects four files or mixed PDF/image before uploading', async () => {
    const w=mount(QuestionImportEditor,{props:{assignmentId:'1',knowledgePoints:[]},global:{plugins:[ElementPlus]}})
    const input=w.find('input[type="file"]')
    for(const files of [Array.from({length:4},(_,i)=>new File(['x'],i+'.png',{type:'image/png'})),[new File(['x'],'a.pdf',{type:'application/pdf'}),new File(['x'],'b.png',{type:'image/png'})]]) {
      Object.defineProperty(input.element,'files',{value:files,configurable:true});await input.trigger('change')
      expect(w.text()).toContain('每次请选择 1～3 张')
    }
    expect(assignmentApi.uploadMedia).not.toHaveBeenCalled();w.unmount()
  })
  it('does not create questions during extraction; blocks missing score then sends teacher edits once', async () => {
    const wrapper = await render()
    expect(assignmentApi.confirmImport).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('未识别分值')
    const confirm = wrapper.findAll('button').find(b => b.text() === '确认导入所选题目')!
    await confirm.trigger('click'); expect(assignmentApi.confirmImport).not.toHaveBeenCalled()
    await wrapper.findComponent({ name: 'ElInputNumber' }).vm.$emit('update:modelValue', 8)
    await wrapper.find('textarea').setValue('Teacher corrected Markdown')
    await confirm.trigger('click'); await flushPromises()
    expect(assignmentApi.confirmImport).toHaveBeenCalledWith('1', '1', [expect.objectContaining({ draftKey: 'draft-one', question: expect.objectContaining({ points: 8, prompt: 'Teacher corrected Markdown' }) })])
    expect(wrapper.emitted('imported')).toHaveLength(1)
    expect(confirm.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
  it('does not fabricate unknown type or allow deselected drafts to be imported', async () => {
    const result = fixture(); result.questions[0]!.type = null as unknown as 'SHORT_ANSWER'
    vi.mocked(assignmentApi.extractQuestions).mockResolvedValue(result)
    const wrapper = await render()
    expect(wrapper.text()).toContain('未知题型，请确认')
    await wrapper.findComponent({ name: 'ElCheckbox' }).vm.$emit('update:modelValue', false)
    await wrapper.findAll('button').find(b => b.text() === '确认导入所选题目')!.trigger('click')
    expect(assignmentApi.confirmImport).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
