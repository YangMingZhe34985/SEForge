import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SafeMarkdown from './SafeMarkdown.vue'

describe('SafeMarkdown', () => {
  it('renders supported syntax and keeps streaming/final rendering consistent', async () => {
    const content = '# Heading\n\n1. **Bold**\n2. *Italic*\n\n- Item\n\n> Quote\n\n`inline`\n\n```js\nconst x = "<safe>"\n```\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n[Docs](https://example.org)\nline two'
    const wrapper = mount(SafeMarkdown, { props: { content: '# Head' } })
    await wrapper.setProps({ content })
    for (const tag of ['h1', 'ol', 'ul', 'strong', 'em', 'blockquote', 'code', 'pre', 'table', 'a', 'br']) {
      expect(wrapper.find(tag).exists()).toBe(true)
    }
    expect(wrapper.find('a').attributes('href')).toBe('https://example.org')
    expect(wrapper.find('pre').text()).toContain('<safe>')
    expect(wrapper.html()).toBe(mount(SafeMarkdown, { props: { content } }).html())
  })

  it.each([
    '<script>alert(1)</script><img src=x onerror=alert(1)>',
    '[x](javascript:alert%281%29)', '[x](JaVaScRiPt:alert%281%29)',
    '[x](jav&#x61;script:alert%281%29)', '[x](data:text/html;base64,abc)',
    '<svg onload=alert(1)><a href="javascript:alert(1)">x</a></svg>',
    '[x](file:///secret) ![image](https://example.org/tracker)',
    '[x](//example.org) <a href="/" onclick="alert(1)">x</a>',
  ])('blocks executable HTML, unsafe URLs and image tracking: %s', (content) => {
    const wrapper = mount(SafeMarkdown, { props: { content } })
    expect(wrapper.find('script,img,svg,iframe,style,[onclick],[onerror],[onload],a').exists()).toBe(false)
  })
})
