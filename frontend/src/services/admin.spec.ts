import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import {
  assignUserRoles,
  getAuditLogs,
  getDashboardStatistics,
  grantRolePermissions,
  uploadFile,
} from './admin'
import { http } from './http'

const defaultAdapter = http.defaults.adapter

function apiResponse(config: InternalAxiosRequestConfig, value: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data: value,
      requestId: 'request-1',
      timestamp: '2026-10-02T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

describe('management API contracts', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('reads DashboardView from the statistics endpoint', async () => {
    const dashboard = {
      totalUsers: 2,
      enabledUsers: 1,
      totalFiles: 3,
      totalFileSizeBytes: 1024,
      recentOperationCount: 4,
      recentOperations: [],
      generatedAt: '2026-10-02T00:00:00Z',
    }
    http.defaults.adapter = async (config) => {
      expect(config.url).toBe('/dashboard/statistics')
      return apiResponse(config, dashboard)
    }

    await expect(getDashboardStatistics()).resolves.toEqual(dashboard)
  })

  it('uses IdSetRequest for user role and role permission assignment', async () => {
    const requests: Array<{ url?: string; body: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({ url: config.url, body: JSON.parse(String(config.data)) })
      return apiResponse(config, null)
    }

    await assignUserRoles(8, [1, 3])
    await grantRolePermissions(3, [1000, 1111])

    expect(requests).toEqual([
      { url: '/system/users/8/roles', body: { ids: [1, 3] } },
      { url: '/system/roles/3/permissions', body: { ids: [1000, 1111] } },
    ])
  })

  it('passes audit filters as controller query parameters', async () => {
    const page = { page: 2, size: 20, total: 0, totalPages: 0, items: [] }
    http.defaults.adapter = async (config) => {
      expect(config.url).toBe('/audit/logs')
      expect(config.params).toEqual({
        page: 2,
        size: 20,
        user: 'operator',
        module: 'USER',
        result: 'FAILURE',
        startTime: '2026-10-01T00:00:00Z',
        endTime: '2026-10-02T00:00:00Z',
      })
      return apiResponse(config, page)
    }

    await expect(getAuditLogs({
      page: 2,
      size: 20,
      user: 'operator',
      module: 'USER',
      result: 'FAILURE',
      startTime: '2026-10-01T00:00:00Z',
      endTime: '2026-10-02T00:00:00Z',
    })).resolves.toEqual(page)
  })

  it('uploads the selected file under the multipart field named file', async () => {
    const storedFile = {
      id: 9,
      originalName: 'report.txt',
      contentType: 'text/plain',
      sizeBytes: 6,
      etag: 'etag',
      uploaderId: 1,
      status: 'AVAILABLE',
      createdAt: '2026-10-02T00:00:00Z',
      updatedAt: '2026-10-02T00:00:00Z',
    }
    const adapter: AxiosAdapter = async (config) => {
      expect(config.url).toBe('/files')
      expect(config.data).toBeInstanceOf(FormData)
      expect((config.data as FormData).get('file')).toBeInstanceOf(File)
      return apiResponse(config, storedFile)
    }
    http.defaults.adapter = adapter

    await expect(uploadFile(new File(['report'], 'report.txt', {
      type: 'text/plain',
    }))).resolves.toEqual(storedFile)
  })
})
