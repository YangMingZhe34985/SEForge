<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const props = defineProps<{ content: string }>()
const markdown = new MarkdownIt({ html: false, breaks: true, linkify: false })
const validateLink = markdown.validateLink.bind(markdown)
markdown.validateLink = (url: string) => validateLink(url)
  && !Array.from(url).some((char) => char.charCodeAt(0) <= 32 || char.charCodeAt(0) === 127)
  && !url.startsWith('//')
  && !url.includes('\\')
  && (!/^[a-z][a-z0-9+.-]*:/i.test(url) || /^(https?:|mailto:)/i.test(url))
markdown.renderer.rules.image = (tokens, index) => markdown.utils.escapeHtml(tokens[index].content)
// Both layers are deliberate: parser rejects raw HTML; sanitizer limits the output vocabulary.
const safeHtml = computed(() => DOMPurify.sanitize(markdown.render(props.content), {
  ALLOWED_TAGS: ['p', 'br', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'li',
    'strong', 'em', 's', 'code', 'pre', 'blockquote', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'a', 'hr'],
  ALLOWED_ATTR: ['href', 'title', 'start'],
  ALLOW_DATA_ATTR: false,
  ALLOW_ARIA_ATTR: false,
}))
</script>

<template>
  <!-- Only this narrowly allow-listed, sanitized output may reach the HTML sink. -->
  <div class="safe-markdown" v-html="safeHtml" />
</template>

<style scoped>
.safe-markdown { overflow-wrap: anywhere; line-height: 1.75; }
.safe-markdown :deep(> :first-child) { margin-top: 0; }
.safe-markdown :deep(> :last-child) { margin-bottom: 0; }
.safe-markdown :deep(pre) { overflow-x: auto; padding: 14px; border-radius: 8px; background: #172033; color: #e4eafa; white-space: pre; }
.safe-markdown :deep(code) { font-family: Consolas, monospace; font-size: .9em; }
.safe-markdown :deep(:not(pre) > code) { background: #eef1f6; padding: 2px 5px; border-radius: 4px; }
.safe-markdown :deep(blockquote) { margin-left: 0; padding-left: 14px; border-left: 3px solid #9cadd7; color: #526079; }
.safe-markdown :deep(table) { display: block; overflow-x: auto; border-collapse: collapse; }
.safe-markdown :deep(th), .safe-markdown :deep(td) { border: 1px solid #d9e0eb; padding: 6px 12px; }
.safe-markdown :deep(a) { color: #294fc4; text-decoration: underline; }
</style>
