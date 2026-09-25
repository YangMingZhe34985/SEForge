#!/usr/bin/env node

import { randomUUID } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'

const options = parseArguments(process.argv.slice(2))
const baseUrl = required(options.baseUrl || process.env.SEFORGE_RAG_EVAL_BASE_URL, '--base-url')
  .replace(/\/$/, '')
const courseId = required(options.courseId || process.env.SEFORGE_RAG_EVAL_COURSE_ID, '--course-id')
const identifier = required(options.identifier || process.env.SEFORGE_RAG_EVAL_IDENTIFIER, '--identifier')
const password = required(options.password || process.env.SEFORGE_RAG_EVAL_PASSWORD, '--password')
const portal = options.portal || process.env.SEFORGE_RAG_EVAL_PORTAL || 'TEACHER'
if (!['STUDENT', 'TEACHER'].includes(portal)) throw new Error('--portal must be STUDENT or TEACHER')
const goldenPath = path.resolve(required(options.golden || process.env.SEFORGE_RAG_EVAL_GOLDEN, '--golden'))
const outputPath = path.resolve(options.output || process.env.SEFORGE_RAG_EVAL_OUTPUT || 'rag-evaluation-results.jsonl')
const thresholds = {
  recallAt5: number(options.recallAt5 || process.env.SEFORGE_RAG_EVAL_RECALL_AT_5, 0.80),
  citationHitRate: number(options.citationHitRate || process.env.SEFORGE_RAG_EVAL_CITATION_HIT_RATE, 0.90),
  refusalRate: number(options.refusalRate || process.env.SEFORGE_RAG_EVAL_REFUSAL_RATE, 0.90),
}

const goldenSet = parseJsonLines(await readFile(goldenPath, 'utf8'))
validateGoldenSet(goldenSet)

const client = new SessionClient(baseUrl)
await client.initializeCsrf()
await client.mutate('/auth/login', { identifier, password, portal })
await client.initializeCsrf() // Login rotates the session; do not reuse the anonymous token.

const results = []
try {
  for (const [index, item] of goldenSet.entries()) {
    process.stdout.write(`[${index + 1}/${goldenSet.length}] ${item.id} `)
    const conversation = await client.mutate(`/courses/${courseId}/conversations`, {})
    const result = await ask(client, courseId, conversation.id, item)
    results.push(result)
    process.stdout.write(`${result.terminal}${result.error ? ` (${result.error})` : ''}\n`)
  }
} finally {
  await client.mutate('/auth/logout', {}).catch(() => undefined)
}

await writeFile(outputPath, `${results.map((item) => JSON.stringify(item)).join('\n')}\n`, 'utf8')
const metrics = calculateMetrics(goldenSet, results)
printMetrics(metrics, thresholds, outputPath)

const passed = results.every((item) => item.terminal === 'done')
  && metrics.recallAt5 >= thresholds.recallAt5
  && metrics.citationHitRate >= thresholds.citationHitRate
  && metrics.refusalRate >= thresholds.refusalRate
process.exitCode = passed ? 0 : 1

async function ask(client, targetCourseId, conversationId, item) {
  const requestId = randomUUID()
  const response = await client.fetch(`/courses/${targetCourseId}/conversations/${conversationId}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ requestId, content: item.question }),
  }, true)
  if (!response.ok) throw await httpError(response)

  const citations = []
  let answer = ''
  let terminal = 'missing'
  let error
  for (const event of parseSse(await response.text())) {
    const payload = event.payload?.data
    if (event.type === 'citation' && payload) citations.push(payload)
    if (event.type === 'message.delta') answer += payload?.delta || ''
    if (event.type === 'done' || event.type === 'error') {
      if (terminal !== 'missing') throw new Error(`Question ${item.id} emitted more than one terminal event`)
      terminal = event.type
      if (event.type === 'error') error = payload?.message || payload?.code || 'stream failed'
    }
  }
  if (terminal === 'missing') error = 'stream ended without a terminal event'
  const sources = citations.slice(0, 5).map((citation) => normalizeSource(
    citation.source || citation.documentName || citation.documentId,
  ))
  const expected = item.expectedSources.map(normalizeSource)
  const matched = expected.filter((source) => sources.includes(source))
  return {
    id: item.id,
    answerable: item.answerable,
    expectedSources: item.expectedSources,
    retrievedSources: citations.slice(0, 5).map((citation) => citation.source || citation.documentName || String(citation.documentId)),
    matchedSources: matched,
    refused: refusal(answer, citations),
    terminal,
    error,
    answer,
  }
}

function calculateMetrics(golden, actual) {
  const byId = new Map(actual.map((item) => [item.id, item]))
  const answerable = golden.filter((item) => item.answerable)
  const unanswerable = golden.filter((item) => !item.answerable)
  const expectedCount = answerable.reduce((sum, item) => sum + item.expectedSources.length, 0)
  const matchedCount = answerable.reduce((sum, item) => sum + (byId.get(item.id)?.matchedSources.length || 0), 0)
  const citationHits = answerable.filter((item) => (byId.get(item.id)?.matchedSources.length || 0) > 0).length
  const refusals = unanswerable.filter((item) => byId.get(item.id)?.refused).length
  return {
    questions: golden.length,
    completed: actual.filter((item) => item.terminal === 'done').length,
    answerable: answerable.length,
    unanswerable: unanswerable.length,
    recallAt5: ratio(matchedCount, expectedCount),
    citationHitRate: ratio(citationHits, answerable.length),
    refusalRate: ratio(refusals, unanswerable.length),
  }
}

function validateGoldenSet(items) {
  if (items.length < 50) throw new Error(`Golden Set requires at least 50 questions; found ${items.length}`)
  const ids = new Set()
  let answerable = 0
  let unanswerable = 0
  for (const item of items) {
    if (!item || typeof item.id !== 'string' || !item.id.trim() || ids.has(item.id)) {
      throw new Error('Every Golden Set item requires a unique non-empty string id')
    }
    ids.add(item.id)
    if (typeof item.question !== 'string' || !item.question.trim()) throw new Error(`${item.id}: question is required`)
    if (typeof item.answerable !== 'boolean') throw new Error(`${item.id}: answerable must be boolean`)
    if (!Array.isArray(item.expectedSources)) throw new Error(`${item.id}: expectedSources must be an array`)
    if (item.answerable && item.expectedSources.length === 0) {
      throw new Error(`${item.id}: answerable questions require at least one expected source`)
    }
    item.answerable ? answerable += 1 : unanswerable += 1
  }
  if (answerable === 0 || unanswerable === 0) {
    throw new Error('Golden Set must contain both answerable and unanswerable questions')
  }
}

function parseJsonLines(value) {
  return value.split(/\r?\n/).map((line) => line.trim()).filter(Boolean).map((line, index) => {
    try { return JSON.parse(line) }
    catch (error) { throw new Error(`Invalid JSON on Golden Set line ${index + 1}: ${error.message}`) }
  })
}

function parseSse(value) {
  return value.replace(/\r\n/g, '\n').split('\n\n').filter(Boolean).map((block) => {
    const lines = block.split('\n')
    const type = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message'
    const data = lines.filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trim()).join('\n')
    return { type, payload: data ? JSON.parse(data) : null }
  })
}

function refusal(answer, citations) {
  const normalized = answer.toLowerCase()
  const markers = [
    '没有足够可靠的依据', '没有足够依据', '无法根据当前资料', '资料不足',
    'insufficient evidence', 'not enough evidence', 'cannot answer from the supplied',
  ]
  return citations.length === 0 || markers.some((marker) => normalized.includes(marker))
}

function normalizeSource(value) {
  return String(value || '').replaceAll('\\', '/').split('/').at(-1).trim().toLowerCase()
}

function ratio(numerator, denominator) {
  return denominator === 0 ? 0 : numerator / denominator
}

function printMetrics(metrics, requiredThresholds, resultPath) {
  const percent = (value) => `${(value * 100).toFixed(2)}%`
  console.log('\nSEForge RAG Golden Set')
  console.log(`Questions: ${metrics.questions}; completed: ${metrics.completed}`)
  console.log(`Recall@5: ${percent(metrics.recallAt5)} (required ${percent(requiredThresholds.recallAt5)})`)
  console.log(`Citation hit rate: ${percent(metrics.citationHitRate)} (required ${percent(requiredThresholds.citationHitRate)})`)
  console.log(`Unsupported-question refusal rate: ${percent(metrics.refusalRate)} (required ${percent(requiredThresholds.refusalRate)})`)
  console.log(`Detailed results: ${resultPath}`)
}

function parseArguments(args) {
  const parsed = {}
  for (let index = 0; index < args.length; index += 1) {
    if (!args[index].startsWith('--')) throw new Error(`Unknown argument: ${args[index]}`)
    const key = args[index].slice(2).replace(/-([a-z])/g, (_, character) => character.toUpperCase())
    parsed[key] = args[++index]
  }
  return parsed
}

function required(value, flag) {
  if (!value) throw new Error(`${flag} is required`)
  return value
}

function number(value, fallback) {
  const parsed = Number(value ?? fallback)
  if (!Number.isFinite(parsed) || parsed < 0 || parsed > 1) throw new Error(`Invalid threshold: ${value}`)
  return parsed
}

async function httpError(response) {
  let detail = `${response.status} ${response.statusText}`
  try { detail = (await response.json()).message || detail } catch { /* non-JSON proxy response */ }
  return new Error(detail)
}

class SessionClient {
  constructor(targetBaseUrl) {
    this.baseUrl = `${targetBaseUrl}/api/v1`
    this.cookies = new Map()
    this.csrf = null
  }

  async initializeCsrf() {
    const response = await this.fetch('/auth/csrf')
    if (!response.ok) throw await httpError(response)
    const envelope = await response.json()
    this.csrf = envelope.data
  }

  async mutate(url, body) {
    const response = await this.fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }, true)
    if (!response.ok) throw await httpError(response)
    return (await response.json()).data
  }

  async fetch(url, init = {}, mutation = false) {
    const headers = new Headers(init.headers)
    if (this.cookies.size) headers.set('Cookie', [...this.cookies].map(([key, value]) => `${key}=${value}`).join('; '))
    if (mutation && this.csrf) headers.set(this.csrf.headerName, this.csrf.token)
    const response = await fetch(`${this.baseUrl}${url}`, { ...init, headers, signal: AbortSignal.timeout(180_000) })
    const setCookies = typeof response.headers.getSetCookie === 'function'
      ? response.headers.getSetCookie()
      : [response.headers.get('set-cookie')].filter(Boolean)
    for (const cookie of setCookies) {
      const [pair] = cookie.split(';', 1)
      const separator = pair.indexOf('=')
      if (separator > 0) this.cookies.set(pair.slice(0, separator), pair.slice(separator + 1))
    }
    return response
  }
}
