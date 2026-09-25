import { apiUrl, ensureCsrf, ApiError } from './client'
import type { ChatMessage, Citation, JobStatus } from '@/types/domain'

export type StreamEventName =
  | 'message.delta'
  | 'citation'
  | 'conversation.title'
  | 'job.status'
  | 'heartbeat'
  | 'done'
  | 'error'

export interface StreamEventMap {
  'message.delta': { delta: string }
  citation: Citation
  'conversation.title': { title: string }
  'job.status': { status: JobStatus; progress?: number; message?: string }
  heartbeat: Record<string, never>
  done: { message: ChatMessage }
  error: { code?: string; message: string; traceId?: string }
}

export type TypedSseEvent = {
  [K in StreamEventName]: {
    type: K
    data: StreamEventMap[K]
    eventId?: string
    requestId?: string
    traceId?: string
  }
}[StreamEventName]

interface RawSseEvent {
  event: string
  data: string
  id?: string
}

function parseEventBlock(block: string): RawSseEvent | null {
  let event = 'message'
  let id: string | undefined
  const data: string[] = []

  for (const line of block.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue
    const separator = line.indexOf(':')
    const field = separator < 0 ? line : line.slice(0, separator)
    const value = separator < 0 ? '' : line.slice(separator + 1).replace(/^ /, '')
    if (field === 'event') event = value
    if (field === 'id') id = value
    if (field === 'data') data.push(value)
  }

  return data.length ? { event, data: data.join('\n'), id } : null
}

export async function* parseSseStream(stream: ReadableStream<Uint8Array>): AsyncGenerator<RawSseEvent> {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      let boundary = /\r?\n\r?\n/.exec(buffer)
      while (boundary) {
        const block = buffer.slice(0, boundary.index)
        buffer = buffer.slice(boundary.index + boundary[0].length)
        const event = parseEventBlock(block)
        if (event) yield event
        boundary = /\r?\n\r?\n/.exec(buffer)
      }
      if (done) break
    }
    const finalEvent = parseEventBlock(buffer)
    if (finalEvent) yield finalEvent
  } finally {
    await reader.cancel().catch(() => undefined)
    reader.releaseLock()
  }
}

function typedEvent(raw: RawSseEvent): TypedSseEvent {
  const type = raw.event as StreamEventName
  let wire: Record<string, unknown>
  try {
    wire = JSON.parse(raw.data) as Record<string, unknown>
  } catch {
    wire = type === 'message.delta' ? { delta: raw.data } : { message: raw.data }
  }
  const isEnvelope = typeof wire.type === 'string' && Object.prototype.hasOwnProperty.call(wire, 'data')
  const payload = (isEnvelope ? wire.data : wire) as StreamEventMap[typeof type]
  return {
    type,
    data: payload,
    eventId: raw.id || (wire.eventId as string | undefined),
    requestId: wire.requestId as string | undefined,
    traceId: wire.traceId as string | undefined,
  } as TypedSseEvent
}

export async function streamJsonSse(
  path: string,
  body: unknown,
  options: { signal?: AbortSignal; onEvent: (event: TypedSseEvent) => void },
): Promise<void> {
  const csrf = await ensureCsrf()
  const response = await fetch(apiUrl(path), {
    method: 'POST',
    credentials: 'include',
    signal: options.signal,
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    let message = `流式请求失败 (${response.status})`
    let code = 'STREAM_REQUEST_FAILED'
    let traceId: string | undefined
    try {
      const error = (await response.json()) as { message?: string; code?: string; traceId?: string }
      message = error.message || message
      code = error.code || code
      traceId = error.traceId
    } catch {
      // Non-JSON proxy errors intentionally fall back to the HTTP status message.
    }
    throw new ApiError(message, response.status, code, traceId)
  }
  if (!response.body) throw new ApiError('浏览器未收到流式响应', response.status, 'EMPTY_STREAM')

  let terminalSeen = false
  for await (const raw of parseSseStream(response.body)) {
    if (!['message.delta', 'citation', 'conversation.title', 'job.status', 'heartbeat', 'done', 'error'].includes(raw.event)) {
      continue
    }
    if (terminalSeen) continue
    const event = typedEvent(raw)
    options.onEvent(event)
    if (event.type === 'done' || event.type === 'error') {
      terminalSeen = true
      break
    }
  }

  if (!terminalSeen && !options.signal?.aborted) {
    throw new ApiError('流式响应意外中断，请重试', 0, 'STREAM_INTERRUPTED')
  }
}
