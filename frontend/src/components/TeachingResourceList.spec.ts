import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it } from 'vitest'
import TeachingResourceList from './TeachingResourceList.vue'
describe('unified teaching resources', () => {
  it('shows one resource with knowledge status, safe external links and membership actions', async () => {
    const wrapper = mount(TeachingResourceList, { props: { manage: true, resources: [
      { id: '1', courseId: '1', name: 'lecture.pdf', resourceType: 'DOCUMENT', objectKey: 'key', createdAt: '' },
      { id: '2', courseId: '1', name: 'unsafe', resourceType: 'LINK', objectKey: 'link', createdAt: '', externalUrl: 'javascript:alert(1)' },
    ], documents: [{ id: '3', resourceId: '1', courseId: '1', name: 'lecture.pdf', mediaType: 'application/pdf', sizeBytes: 10, checksum: 'x', status: 'READY', createdAt: '' }], status: d => d.status, error: () => '', cancelable: () => false, cancelling: () => false, formatBytes: n => String(n) }, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).toContain('移出知识库'); expect(wrapper.find('a[href^="javascript:"]').exists()).toBe(false)
    await wrapper.findAll('button').find(b => b.text() === '移出知识库')!.trigger('click')
    expect(wrapper.emitted('exclude')?.[0]?.[0]).toMatchObject({ id: '3' }); wrapper.unmount()
  })
})
