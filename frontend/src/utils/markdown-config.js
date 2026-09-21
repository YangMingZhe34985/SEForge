import MarkdownIt from 'markdown-it'
import * as emoji from 'markdown-it-emoji'
import taskLists from 'markdown-it-task-lists'
import hljs from 'highlight.js'

// 配置highlight.js
hljs.configure({
    languages: ['javascript', 'python', 'java', 'cpp', 'css', 'xml', 'sql']
})

const md = new MarkdownIt({
    html: true,
    xhtmlOut: true,
    breaks: true,
    linkify: true,
    typographer: true,
    highlight: (str, lang) => {
        if (lang && hljs.getLanguage(lang)) {
            try {
                return `<pre class="hljs" data-lang="${lang}"><code>${
                    hljs.highlight(str, { language: lang, ignoreIllegals: true }).value
                }</code></pre>`
            } catch (__) {}
        }
        return `<pre class="hljs"><code>${
            md.utils.escapeHtml(str)
        }</code></pre>`
    }
})
    .use(emoji.default || emoji)  // 兼容性处理
    .use(taskLists, {
        label: true,
        labelAfter: true
    })

// 自定义列表项渲染
md.renderer.rules.list_item_open = (tokens, idx, options, env, self) => {
    const token = tokens[idx]
    let className = ''

    // 检测特殊符号
    if (token.content.includes('✓')) {
        className = 'checkmark'
    } else if (token.content.includes('✗')) {
        className = 'xmark'
    } else if (token.content.includes('○')) {
        className = 'custom-circle'
    } else if (token.content.includes('·')) {
        className = 'custom-dot'
    }

    return `<li${className ? ` class="${className}"` : ''}>`
}

// 添加明确的导出
export const processMarkdown = (content) => {
    return md.render(content)
}

// 保留默认导出
export default md