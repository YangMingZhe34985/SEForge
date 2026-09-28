<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Close, Plus, Promotion, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import SafeMarkdown from '@/components/SafeMarkdown.vue'
import { assistantApi } from '@/api/assistant'
import { ApiError } from '@/api/client'
import { streamJsonSse, type TypedSseEvent } from '@/api/sse'
import type { ChatMessage, Conversation } from '@/types/domain'

const route = useRoute()
const router = useRouter()
const courseId = computed(() => String(route.params.courseId))
const conversations = ref<Conversation[]>([])
const activeConversationId = ref<string | null>(null)
const messages = ref<ChatMessage[]>([])
const question = ref('')
const loading = ref(false)
const sending = ref(false)
const lastQuestion = ref('')
const streamError = ref('')
const messageList = ref<HTMLElement>()
const generationRequestId = ref<string | null>(null)
let recoveryTimer: ReturnType<typeof setTimeout> | undefined
let courseGeneration = 0
let conversationRequestGeneration = 0
let activeStream: {
  controller: AbortController
  requestId: string
  courseId: string
  conversationId: string
  courseVersion: number
} | null = null

const activeConversation = computed(() => conversations.value.find((item) => item.id === activeConversationId.value))

async function loadConversations(requestedCourseId = courseId.value, courseVersion = courseGeneration) {
  loading.value = true
  try {
    const page = await assistantApi.conversations(requestedCourseId)
    if (courseVersion !== courseGeneration || requestedCourseId !== courseId.value) return
    conversations.value = page.items
    const preferred = String(route.query.conversation || sessionStorage.getItem(`assistant:${requestedCourseId}`) || '')
    const firstId = conversations.value.find((item) => String(item.id) === preferred)?.id || conversations.value[0]?.id
    if (firstId) await selectConversation(firstId, requestedCourseId, courseVersion)
    else await createConversation(requestedCourseId, courseVersion)
  } catch (error) {
    if (courseVersion === courseGeneration) ElMessage.error(error instanceof Error ? error.message : '会话加载失败')
  } finally {
    if (courseVersion === courseGeneration) loading.value = false
  }
}

async function createConversation(requestedCourseId = courseId.value, courseVersion = courseGeneration) {
  try {
    const conversation = await assistantApi.createConversation(requestedCourseId)
    if (courseVersion !== courseGeneration || requestedCourseId !== courseId.value) return
    conversations.value.unshift(conversation)
    await selectConversation(conversation.id, requestedCourseId, courseVersion)
  } catch (error) {
    if (courseVersion === courseGeneration) ElMessage.error(error instanceof Error ? error.message : '创建会话失败')
  }
}

async function selectConversation(
  id: string,
  requestedCourseId = courseId.value,
  courseVersion = courseGeneration,
) {
  detachStream()
  streamError.value = ''
  lastQuestion.value = ''
  const requestVersion = ++conversationRequestGeneration
  activeConversationId.value = id
  sessionStorage.setItem(`assistant:${requestedCourseId}`, String(id))
  void router.replace({ query: { ...route.query, conversation: id } })
  messages.value = []
  loading.value = true
  try {
    const state = await assistantApi.generation(requestedCourseId, id)
    const page = await assistantApi.messages(requestedCourseId, id)
    if (courseVersion !== courseGeneration || requestVersion !== conversationRequestGeneration
      || requestedCourseId !== courseId.value || activeConversationId.value !== id) return
    messages.value = page.items
    applyGeneration(state)
    if (sending.value) scheduleRecovery()
    await scrollToBottom()
  } catch (error) {
    if (courseVersion === courseGeneration && requestVersion === conversationRequestGeneration) {
      ElMessage.error(error instanceof Error ? error.message : '消息加载失败')
    }
  } finally {
    if (courseVersion === courseGeneration && requestVersion === conversationRequestGeneration) loading.value = false
  }
}

async function renameConversation(id: string) {
  const course = courseId.value; const version = courseGeneration
  try {
    const current = conversations.value.find(c => c.id === id)
    const input = await ElMessageBox.prompt('请输入会话名称', '重命名会话', { inputValue: current?.title, inputValidator: value => !!value?.trim() && value.length <= 255 || '请输入 1 至 255 个字符' })
    const updated = await assistantApi.renameConversation(course, id, input.value)
    if (course !== courseId.value || version !== courseGeneration) return
    const index = conversations.value.findIndex(c => c.id === id)
    if (index >= 0) conversations.value[index] = updated
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof Error ? e.message : '重命名失败') }
}

async function deleteConversation(id: string) {
  const course = courseId.value; const version = courseGeneration
  try {
    await ElMessageBox.confirm('将永久删除此会话的消息、引用和反馈，无法恢复。正在生成的会话需要先取消。', '删除会话', { type: 'warning' })
    await assistantApi.deleteConversation(course, id)
    if (course !== courseId.value || version !== courseGeneration) return
    conversations.value = conversations.value.filter(item => item.id !== id)
    if (activeConversationId.value === id) {
      detachStream(); conversationRequestGeneration++; messages.value = []; activeConversationId.value = null
      sessionStorage.removeItem(`assistant:${course}`)
      await router.replace({ query: { ...route.query, conversation: undefined } })
      const first = conversations.value[0]
      if (first) await selectConversation(first.id)
      else await createConversation()
    }
    ElMessage.success('会话已删除')
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof Error ? e.message : '删除失败') }
}

async function scrollToBottom() {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}

function applyGeneration(state: Awaited<ReturnType<typeof assistantApi.generation>>) {
  generationRequestId.value = state?.requestId || null
  sending.value = state?.status === 'PROCESSING'
  if (state) lastQuestion.value = state.question
  streamError.value = state && ['FAILED', 'CANCELLED'].includes(state.status)
    ? `${state.errorMessage || '生成失败，可重试'}（${state.errorCode || state.status}），追踪号 ${state.traceId}` : ''
}

function scheduleRecovery() {
  clearTimeout(recoveryTimer)
  recoveryTimer = setTimeout(() => void recoverGeneration(), 1500)
}

async function recoverGeneration(expectedRequestId?: string) {
  const id = activeConversationId.value
  const course = courseId.value
  const version = conversationRequestGeneration
  if (!id || activeStream) return
  const current = () => version === conversationRequestGeneration && id === activeConversationId.value && course === courseId.value
  try {
    const state = await assistantApi.generation(course, id)
    const page = await assistantApi.messages(course, id)
    if (!current() || activeStream) return
    if (expectedRequestId && state?.requestId !== expectedRequestId && state?.status !== 'PROCESSING') return
    messages.value = page.items
    applyGeneration(state)
    if (sending.value) scheduleRecovery()
    await scrollToBottom()
  } catch (error) {
    if (!current()) return
    streamError.value = `状态恢复失败：${error instanceof Error ? error.message : '请检查网络'}；将自动重试。`
    sending.value = true
    scheduleRecovery()
  }
}

function applyStreamEvent(event: TypedSseEvent, assistantMessage: ChatMessage, conversationId: string) {
  if (event.type === 'message.delta') {
    assistantMessage.content += event.data.delta
  } else if (event.type === 'citation') {
    assistantMessage.citations = [...(assistantMessage.citations || []), event.data]
  } else if (event.type === 'conversation.title') {
    const title = event.data.title
    const conversation = conversations.value.find((item) => item.id === conversationId)
    if (conversation) conversation.title = title
  } else if (event.type === 'done') {
    if (event.data.message?.id) assistantMessage.id = String(event.data.message.id)
    assistantMessage.pending = false
  } else if (event.type === 'error') {
    streamError.value = `${event.data.message || '回答生成失败，请重试'}${event.data.code ? `（${event.data.code}）` : ''}${event.traceId ? `，追踪号 ${event.traceId}` : ''}`
    assistantMessage.content ||= streamError.value
    assistantMessage.pending = false
  }
  void scrollToBottom()
}

async function sendMessage(retryText?: string) {
  const content = (retryText ?? question.value).trim()
  if (!content || sending.value || !activeConversationId.value) return
  const requestedCourseId = courseId.value
  const conversationId = activeConversationId.value
  const courseVersion = courseGeneration
  question.value = ''
  lastQuestion.value = content
  streamError.value = ''
  messages.value.push({ id: `local-user-${Date.now()}`, role: 'USER', content, createdAt: new Date().toISOString() })
  const assistantMessage = reactive<ChatMessage>({
    id: `local-assistant-${Date.now()}`,
    role: 'ASSISTANT',
    content: '',
    citations: [],
    createdAt: new Date().toISOString(),
    pending: true,
  })
  messages.value.push(assistantMessage)
  sending.value = true
  const stream = {
    controller: new AbortController(),
    requestId: crypto.randomUUID(),
    courseId: requestedCourseId,
    conversationId,
    courseVersion,
  }
  activeStream = stream
  generationRequestId.value = stream.requestId
  let completed = false
  await scrollToBottom()

  try {
    await streamJsonSse(
      `/courses/${requestedCourseId}/conversations/${conversationId}/messages`,
      { requestId: stream.requestId, content },
      {
        signal: stream.controller.signal,
        onEvent: (event) => {
          if (activeStream === stream && courseVersion === courseGeneration
            && activeConversationId.value === conversationId) {
            applyStreamEvent(event, assistantMessage, conversationId)
            if (event.type === 'done') completed = true
          }
        },
      },
    )
  } catch (error) {
    if (activeStream === stream && !stream.controller.signal.aborted && courseVersion === courseGeneration) {
      streamError.value = error instanceof ApiError
        ? `${error.message}（${error.code}）${error.traceId ? `，追踪号 ${error.traceId}` : ''}`
        : error instanceof Error ? error.message : '回答生成失败'
      assistantMessage.content ||= '回答未能完整生成，请重试。'
    }
  } finally {
    if (activeStream === stream) {
      assistantMessage.pending = false
      sending.value = false
      activeStream = null
      if (!completed) await recoverGeneration(stream.requestId)
    }
  }
}

function detachStream() {
  clearTimeout(recoveryTimer)
  const stream = activeStream
  activeStream = null
  stream?.controller.abort()
  sending.value = false
  generationRequestId.value = null
}

async function stopStream() {
  const id = activeConversationId.value
  const requestId = generationRequestId.value
  const version = conversationRequestGeneration
  if (!id || !requestId) return
  try {
    await assistantApi.cancel(courseId.value, id, requestId)
    if (version !== conversationRequestGeneration) return
    detachStream()
    await recoverGeneration()
  } catch (error) {
    if (version === conversationRequestGeneration) ElMessage.error(error instanceof Error ? error.message : '取消失败，请重试')
  }
}

async function sendFeedback(messageId: string, helpful: boolean) {
  if (!activeConversationId.value) return
  const requestedCourseId = courseId.value
  const conversationId = activeConversationId.value
  const courseVersion = courseGeneration
  try {
    await assistantApi.feedback(requestedCourseId, conversationId, messageId, helpful)
    if (courseVersion === courseGeneration && requestedCourseId === courseId.value) ElMessage.success('反馈已记录')
  } catch (error) {
    if (courseVersion === courseGeneration) ElMessage.error(error instanceof Error ? error.message : '反馈提交失败')
  }
}

function activateCourse() {
  detachStream()
  question.value = ''
  lastQuestion.value = ''
  courseGeneration += 1
  conversationRequestGeneration += 1
  conversations.value = []
  activeConversationId.value = null
  messages.value = []
  streamError.value = ''
  loading.value = false
  void loadConversations(courseId.value, courseGeneration)
}

watch(courseId, activateCourse)
onMounted(activateCourse)
onBeforeUnmount(() => {
  detachStream()
  courseGeneration += 1
  conversationRequestGeneration += 1
})
</script>

<template>
  <div class="assistant-page">
    <PageHeader title="课程助手" description="回答严格限定在当前课程知识范围内，并用独立引用卡片展示依据。">
      <el-button :icon="Plus" @click="createConversation()">新建会话</el-button>
    </PageHeader>

    <section class="assistant-layout panel" v-loading="loading">
      <aside class="conversation-list">
        <div class="conversation-list__header"><strong>会话记录</strong><span>{{ conversations.length }}</span></div>
        <div v-for="conversation in conversations" :key="conversation.id"><button type="button" :class="{ active: conversation.id === activeConversationId }" @click="selectConversation(conversation.id)">
          <ChatDotRound /><span><strong>{{ conversation.title }}</strong><small>{{ new Date(conversation.updatedAt).toLocaleDateString() }}</small></span>
        </button><el-button link :aria-label="`重命名会话 ${conversation.title}`" @click="renameConversation(conversation.id)">重命名</el-button><el-button link type="danger" :aria-label="`删除会话 ${conversation.title}`" @click="deleteConversation(conversation.id)">删除</el-button></div>
      </aside>

      <div class="chat-workspace">
        <div class="chat-title"><div><strong>{{ activeConversation?.title || '课程问答' }}</strong><small>回答可能有误，请结合引用资料核验</small></div></div>
        <div ref="messageList" class="message-list">
          <EmptyState v-if="!messages.length" title="从课程资料开始提问" description="例如：需求可追踪矩阵如何帮助验证 SRS 的完整性？" />
          <article v-for="message in messages" :key="message.id" class="message" :class="`message--${message.role.toLowerCase()}`">
            <div class="message__label">{{ message.role === 'USER' ? '你' : 'SEForge Assistant' }}</div>
            <div class="message__bubble">
              <SafeMarkdown v-if="message.role === 'ASSISTANT'" :content="message.content" />
              <p v-else>{{ message.content }}</p>
              <span v-if="message.pending" class="typing-cursor" />
              <div v-if="message.citations?.length" class="citations">
                <strong>引用依据</strong>
                <div v-for="(citation, index) in message.citations" :key="citation.id || `${citation.documentId}-${index}`" class="citation-card">
                  <span>{{ index + 1 }}</span><div><b>{{ citation.documentName || citation.source || citation.documentId }}</b><small>{{ [citation.chapter, citation.section, citation.page ? `第 ${citation.page} 页` : ''].filter(Boolean).join(' · ') }}</small><p v-if="citation.excerpt || citation.quote">{{ citation.excerpt || citation.quote }}</p></div>
                </div>
              </div>
              <div v-if="message.role === 'ASSISTANT' && !message.pending && !String(message.id).startsWith('local-')" class="message__feedback"><span>这条回答有帮助吗？</span><el-button text size="small" @click="sendFeedback(message.id, true)">有帮助</el-button><el-button text size="small" @click="sendFeedback(message.id, false)">需改进</el-button></div>
            </div>
          </article>
        </div>

        <div v-if="sending" class="generation-status" role="status">正在生成，离开页面不会取消；返回后将恢复完整回答。</div>
        <div v-if="streamError" class="stream-error"><span>{{ streamError }}</span><el-button :icon="Refresh" text :disabled="sending || !lastQuestion" @click="sendMessage(lastQuestion)">重试</el-button></div>
        <div class="composer">
          <el-input v-model="question" type="textarea" :rows="3" resize="none" maxlength="4000" show-word-limit placeholder="输入与本课程相关的问题…" @keydown.ctrl.enter="sendMessage()" />
          <div class="composer__actions"><span>Ctrl + Enter 发送</span><el-button v-if="sending" :icon="Close" @click="stopStream">停止</el-button><el-button v-else type="primary" :icon="Promotion" :disabled="!question.trim()" @click="sendMessage()">发送</el-button></div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.assistant-layout { min-height: calc(100vh - 210px); display: grid; grid-template-columns: 260px minmax(0, 1fr); overflow: hidden; }
.conversation-list { border-right: 1px solid var(--line); padding: 14px; background: #f9fafc; }
.conversation-list__header { display: flex; justify-content: space-between; padding: 8px 9px 15px; color: var(--muted); font-size: 12px; }
.conversation-list button { width: 100%; display: flex; align-items: flex-start; gap: 9px; border: 0; border-radius: 9px; padding: 11px; background: transparent; color: #5f6a7d; text-align: left; }
.conversation-list button:hover, .conversation-list button.active { background: #e9edfb; color: #223cba; }
.conversation-list button svg { width: 17px; flex: 0 0 17px; }
.conversation-list button span { min-width: 0; display: grid; gap: 4px; }
.conversation-list button strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.conversation-list button small { color: #8a94a7; }
.chat-workspace { min-width: 0; display: grid; grid-template-rows: auto minmax(320px, 1fr) auto auto; }
.chat-title { display: flex; justify-content: space-between; border-bottom: 1px solid var(--line); padding: 16px 20px; }
.chat-title div { display: grid; gap: 3px; }.chat-title small { color: var(--muted); }
.message-list { overflow-y: auto; padding: 24px clamp(16px, 5vw, 68px); }
.message { display: grid; gap: 6px; margin: 0 auto 22px; max-width: 900px; }
.message__label { color: #7d8798; font-size: 11px; font-weight: 700; }
.message__bubble { border: 1px solid var(--line); border-radius: 4px 14px 14px; padding: 16px 18px; background: #fff; line-height: 1.75; }
.message--user .message__bubble { border-color: #d9e0ff; background: #f1f4ff; }
.message__bubble > p { margin: 0; white-space: pre-wrap; word-break: break-word; }
.typing-cursor { display: inline-block; width: 7px; height: 16px; margin-left: 3px; background: var(--brand); vertical-align: -2px; animation: blink .8s infinite; }
.citations { display: grid; gap: 8px; margin-top: 16px; border-top: 1px solid var(--line); padding-top: 13px; }
.citations > strong { color: var(--muted); font-size: 11px; }
.citation-card { display: grid; grid-template-columns: 24px 1fr; gap: 9px; border-radius: 8px; padding: 10px; background: #f6f8fb; }
.citation-card > span { width: 22px; height: 22px; display: grid; place-items: center; border-radius: 50%; background: #e1e6f8; color: var(--brand); font-size: 10px; font-weight: 800; }
.citation-card div { display: grid; gap: 2px; }.citation-card b { font-size: 12px; }.citation-card small { color: var(--muted); }.citation-card p { margin: 5px 0 0; color: #596477; font-size: 12px; }
.message__feedback { display: flex; align-items: center; gap: 4px; margin-top: 12px; color: var(--muted); font-size: 11px; }
.composer { border-top: 1px solid var(--line); padding: 14px 18px; background: #fff; }
.composer__actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; padding-top: 9px; }.composer__actions span { margin-right: auto; color: #929aac; font-size: 10px; }
.stream-error { display: flex; align-items: center; justify-content: space-between; padding: 7px 18px; background: #fff0f0; color: #a72c38; font-size: 12px; }
@keyframes blink { 50% { opacity: 0; } }
@media (max-width: 760px) { .assistant-layout { grid-template-columns: 1fr; }.conversation-list { display: flex; overflow-x: auto; border-right: 0; border-bottom: 1px solid var(--line); }.conversation-list__header { display: none; }.conversation-list button { min-width: 180px; }.message-list { padding: 18px 12px; } }
</style>
