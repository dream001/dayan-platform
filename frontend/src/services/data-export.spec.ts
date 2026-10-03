import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import {
  createExportTask,
  downloadExport,
  getExportDatasets,
  getExportQuota,
  getExportTasks,
} from './data-export'
import { http } from './http'

const defaultAdapter = http.defaults.adapter

function apiResponse(config: InternalAxiosRequestConfig, value: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data: value,
      requestId: 'request-export',
      timestamp: '2026-10-02T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

describe('data export API contracts', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('loads export candidates, quota, and task history', async () => {
    const requests: Array<{ url?: string; params?: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({ url: config.url, params: config.params })
      if (config.url?.endsWith('/datasets')) return apiResponse(config, [])
      if (config.url?.endsWith('/quota/current')) {
        return apiResponse(config, { used: 1, limit: 1000, remaining: 999 })
      }
      return apiResponse(config, { items: [], page: 1, size: 20, total: 0, totalPages: 0 })
    }

    await getExportDatasets({ projectId: 7, keyword: 'robot' })
    await getExportQuota()
    await getExportTasks({ page: 1, size: 20, status: 'PROCESSING' })

    expect(requests).toEqual([
      { url: '/data/exports/datasets', params: { projectId: 7, keyword: 'robot' } },
      { url: '/data/exports/quota/current', params: undefined },
      {
        url: '/data/exports',
        params: { page: 1, size: 20, status: 'PROCESSING' },
      },
    ])
  })

  it('creates tasks and downloads generated files from task-scoped endpoints', async () => {
    const requests: Array<{ method?: string; url?: string; responseType?: string }> = []
    http.defaults.adapter = async (config) => {
      requests.push({
        method: config.method,
        url: config.url,
        responseType: config.responseType,
      })
      return config.responseType === 'blob'
        ? { ...apiResponse(config, new Blob()), data: new Blob() }
        : apiResponse(config, { id: 12 })
    }

    await createExportTask({
      name: 'training-set',
      format: 'LEROBOT',
      datasetIds: [3, 4],
      mediaMode: 'VIDEO',
      sampleRate: 30,
    })
    await downloadExport(12)

    expect(requests).toEqual([
      { method: 'post', url: '/data/exports', responseType: undefined },
      { method: 'get', url: '/data/exports/12/download', responseType: 'blob' },
    ])
  })
})
