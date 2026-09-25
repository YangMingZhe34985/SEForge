import { afterEach, describe, expect, it, vi } from 'vitest'
import { parseSseStream, streamJsonSse } from '@/api/sse'

vi.mock('@/api/client', async (original) => ({
  ...await original<typeof import('@/api/client')>(),
  ensureCsrf: vi.fn(async () => ({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' })),
}))
afterEach(() => vi.unstubAllGlobals())

function streamOf(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder()
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
      controller.close()
    },
  })
}

describe('parseSseStream', () => {
  it('handles CRLF boundaries split across network chunks', async () => {
    const events = []
    for await (const event of parseSseStream(streamOf([
      'event: done\r', '\ndata: {"message":{}}\r', '\n\r', '\n',
    ]))) events.push(event)
    expect(events).toEqual([{ event: 'done', data: '{"message":{}}', id: undefined }])
  })
  it('parses named, chunked and multiline events', async () => {
    const stream = streamOf([
      'event: message.delta\nid: evt-1\ndata: {"delta":"hel',
      'lo"}\n\nevent: citation\ndata: {"documentId":"doc-1",\n',
      'data: "page":2}\n\n',
    ])
    const events = []
    for await (const event of parseSseStream(stream)) events.push(event)
    expect(events).toEqual([
      { event: 'message.delta', id: 'evt-1', data: '{"delta":"hello"}' },
      { event: 'citation', data: '{"documentId":"doc-1",\n"page":2}' },
    ])
  })
})

describe('POST-SSE contract', () => {
  it('delivers infrastructure errors with a diagnostic code and trace exactly once', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(streamOf([
      'event: error\ndata: {"type":"error","traceId":"trace-milvus","data":{"code":"VECTOR_STORE_UNAVAILABLE","message":"Milvus unavailable"}}\n\n',
      'event: done\ndata: {"message":{}}\n\n',
    ]))))
    const onEvent = vi.fn()
    await streamJsonSse('/test', {}, { onEvent })
    expect(onEvent).toHaveBeenCalledOnce()
    expect(onEvent).toHaveBeenCalledWith(expect.objectContaining({ type: 'error', traceId: 'trace-milvus', data: { code: 'VECTOR_STORE_UNAVAILABLE', message: 'Milvus unavailable' } }))
  })
  it('sends credentials and CSRF, stops at the first terminal and cancels the reader', async () => {
    const cancel = vi.fn()
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(
          'event: message.delta\ndata: {"type":"message.delta","data":{"delta":"hi"},"requestId":"r"}\n\n'
          + 'event: done\ndata: {"type":"done","data":{"message":{"id":"1"}}}\n\n'
          + 'event: error\ndata: {"message":"must not appear"}\n\n',
        ))
      }, cancel,
    })
    const fetch = vi.fn(async () => new Response(stream, { status: 200 }))
    vi.stubGlobal('fetch', fetch)
    const events: string[] = []
    await streamJsonSse('/test', { requestId: 'r' }, { onEvent: (event) => events.push(event.type) })
    expect(events).toEqual(['message.delta', 'done'])
    expect(cancel).toHaveBeenCalledOnce()
    expect(fetch).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({
      method: 'POST', credentials: 'include', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'test-csrf' }),
    }))
  })

  it('reports transport EOF without a terminal as interruption, not success', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(streamOf(['event: message.delta\ndata: {"delta":"partial"}\n\n']))))
    await expect(streamJsonSse('/test', {}, { onEvent: vi.fn() })).rejects.toMatchObject({ code: 'STREAM_INTERRUPTED' })
  })

  it('preserves safe HTTP errors and trace ids', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ code: 'FORBIDDEN', message: 'Denied', traceId: 't' }), { status: 403 })))
    await expect(streamJsonSse('/test', {}, { onEvent: vi.fn() })).rejects.toMatchObject({ code: 'FORBIDDEN', traceId: 't' })
  })
})
