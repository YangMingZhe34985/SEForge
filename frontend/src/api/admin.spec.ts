import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))

vi.mock('./client', () => ({ apiRequest: requestMock }))

import { adminApi } from './admin'

describe('adminApi contracts', () => {
  beforeEach(() => {
    requestMock.mockReset()
  })

  it('updates platform roles through the administrator contract', async () => {
    requestMock.mockResolvedValueOnce({ id: 'user-1' })

    await adminApi.setRoles('user-1', ['USER', 'ADMIN'])

    expect(requestMock).toHaveBeenCalledWith({
      url: '/admin/users/user-1/roles',
      method: 'PUT',
      data: { roles: ['USER', 'ADMIN'] },
    })
  })

  it('reads the platform audit trail with server-side pagination', async () => {
    requestMock.mockResolvedValueOnce({ items: [], page: 1, size: 20, total: 0 })

    await adminApi.auditLogs(1, 20)

    expect(requestMock).toHaveBeenCalledWith({
      url: '/admin/audit-logs',
      params: { page: 1, size: 20 },
    })
  })

  it('uses separate preview and confirm requests for CSV account imports', async () => {
    requestMock.mockResolvedValueOnce({ digest: 'abc', valid: 1, rejected: 0, rows: [] })
    requestMock.mockResolvedValueOnce({ created: 1, skipped: 0, failed: 0, rows: [] })
    const file = new File(['accountType,studentNo,username,email,displayName'], 'users.csv', { type: 'text/csv' })
    await adminApi.previewImport(file)
    await adminApi.confirmImport(file, 'abc')
    expect(requestMock.mock.calls[0]?.[0]).toMatchObject({ url: '/admin/users/import/preview', method: 'POST' })
    expect(requestMock.mock.calls[1]?.[0]).toMatchObject({ url: '/admin/users/import/confirm', method: 'POST', params: { digest: 'abc' } })
    expect((requestMock.mock.calls[1]?.[0]?.data as FormData).get('file')).toBe(file)
  })
})
