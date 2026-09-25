import axios, { type AxiosRequestConfig } from 'axios'
import type { ApiEnvelope, CsrfToken } from '@/types/domain'
import { fieldErrors } from './validation'

export const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')

const http = axios.create({
  baseURL: apiBaseUrl,
  timeout: 30_000,
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: { Accept: 'application/json' },
})

let csrfToken: CsrfToken | null = null
let csrfRequest: Promise<CsrfToken> | null = null

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code = 'REQUEST_FAILED',
    public readonly traceId?: string,
    public readonly details?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

function normalizeError(error: unknown): ApiError {
  if (!axios.isAxiosError(error)) {
    return error instanceof ApiError
      ? error
      : new ApiError(error instanceof Error ? error.message : '请求失败', 0)
  }

  const envelope = error.response?.data as Partial<ApiEnvelope<unknown>> & { details?: unknown }
  const status = error.response?.status ?? 0
  if (status === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('seforge:unauthorized'))
  }
  const fields = fieldErrors(envelope?.details)
  const message = Object.keys(fields).length && envelope?.code === 'VALIDATION_FAILED'
    ? Object.entries(fields).map(([field, text]) => `${field}: ${text}`).join('；')
    : envelope?.message || (status === 0 ? '无法连接到服务器' : '请求失败')
  return new ApiError(
    status >= 500 && envelope?.traceId ? `${message}（${envelope.code}，追踪号 ${envelope.traceId}）` : message,
    status,
    String(envelope?.code || 'REQUEST_FAILED'),
    envelope?.traceId,
    envelope?.details,
  )
}

export async function refreshCsrf(): Promise<CsrfToken> {
  if (!csrfRequest) {
    csrfRequest = http
      .get<ApiEnvelope<CsrfToken>>('/auth/csrf')
      .then(({ data }) => {
        csrfToken = data.data
        return data.data
      })
      .catch((error) => {
        throw normalizeError(error)
      })
      .finally(() => {
        csrfRequest = null
      })
  }
  return csrfRequest
}

export async function ensureCsrf(): Promise<CsrfToken> {
  return csrfToken ?? refreshCsrf()
}

export function clearCsrf(): void {
  csrfToken = null
}

export async function apiRequest<T>(config: AxiosRequestConfig): Promise<T> {
  const method = (config.method || 'GET').toUpperCase()
  const isMutation = !['GET', 'HEAD', 'OPTIONS'].includes(method)
  let headers = config.headers

  if (isMutation) {
    const csrf = await ensureCsrf()
    headers = { ...headers, [csrf.headerName]: csrf.token }
  }

  try {
    const response = await http.request<ApiEnvelope<T>>({ ...config, headers })
    if (response.status === 204) return undefined as T
    if (!response.data || !Object.prototype.hasOwnProperty.call(response.data, 'data')) {
      throw new ApiError('服务器响应格式不正确', response.status, 'INVALID_RESPONSE')
    }
    return response.data.data
  } catch (error) {
    const normalized = normalizeError(error)
    if (isMutation && normalized.status === 403 && normalized.code.includes('CSRF')) {
      clearCsrf()
    }
    throw normalized
  }
}

export function apiUrl(path: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${apiBaseUrl}${normalizedPath}`
}
