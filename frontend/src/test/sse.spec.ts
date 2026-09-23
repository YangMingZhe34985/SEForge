import { describe, expect, it } from 'vitest'
import { parseSseStream } from '@/api/sse'

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
