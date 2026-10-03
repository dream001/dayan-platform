import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import {
  cancelQueueTask,
  collectMonitoringMetrics,
  getAccessLogs,
  getMonitoringOverview,
  setQueuePaused,
} from './monitoring'
import { http } from './http'

const defaultAdapter = http.defaults.adapter

function apiResponse(config: InternalAxiosRequestConfig, value: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data: value,
      requestId: 'request-monitoring',
      timestamp: '2026-10-03T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

describe('monitoring API contracts', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('loads overview and filtered access logs from monitor endpoints', async () => {
    const requests: Array<{ url?: string; params?: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({ url: config.url, params: config.params })
      return config.url?.endsWith('/overview')
        ? apiResponse(config, { range: '24h', trend: [] })
        : apiResponse(config, { items: [], page: 1, size: 20, total: 0, totalPages: 0 })
    }

    await getMonitoringOverview('24h')
    await getAccessLogs({
      page: 1,
      size: 20,
      path: '/api/v1/files',
      statusCode: 500,
    })

    expect(requests).toEqual([
      { url: '/monitor/overview', params: { range: '24h' } },
      {
        url: '/monitor/access-logs',
        params: {
          page: 1,
          size: 20,
          path: '/api/v1/files',
          statusCode: 500,
        },
      },
    ])
  })

  it('uses explicit write endpoints for collection and queue control', async () => {
    const requests: Array<{ method?: string; url?: string; params?: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({ method: config.method, url: config.url, params: config.params })
      return apiResponse(config, {})
    }

    await collectMonitoringMetrics()
    await setQueuePaused(true)
    await cancelQueueTask(19)

    expect(requests).toEqual([
      { method: 'post', url: '/monitor/collect', params: undefined },
      { method: 'post', url: '/monitor/queue/pause', params: { paused: true } },
      { method: 'post', url: '/monitor/queue/tasks/19/cancel', params: undefined },
    ])
  })
})
