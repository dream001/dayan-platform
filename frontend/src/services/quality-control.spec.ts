import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import {
  createQualityRule,
  getQualityLogs,
  overrideQualityExecution,
  runQualityCheck,
} from './quality-control'
import { http } from './http'

const defaultAdapter = http.defaults.adapter

function response(config: InternalAxiosRequestConfig, value: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data: value,
      requestId: 'quality-request',
      timestamp: '2026-10-02T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

describe('quality control API contracts', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('uses scoped rule, execution, and override endpoints', async () => {
    const requests: Array<{ method?: string; url?: string; body?: unknown; params?: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({
        method: config.method,
        url: config.url,
        body: config.data ? JSON.parse(String(config.data)) : undefined,
        params: config.params,
      })
      return response(config, config.url?.endsWith('/logs')
        ? { page: 1, size: 20, total: 0, totalPages: 0, items: [] }
        : {})
    }

    await createQualityRule({
      name: 'Frame rate',
      description: '',
      scope: 'PROJECT',
      projectId: 3,
      dataType: 'MCAP',
      datasetPattern: '*.mcap',
      enabled: true,
      priority: 100,
      assertions: [{
        type: 'NUMERIC',
        metric: 'frame_rate',
        operator: '>=',
        threshold: 20,
        severity: 'ERROR',
        metricScope: 'ALL',
        matchPattern: '',
      }],
    })
    await runQualityCheck(8)
    await getQualityLogs({ page: 1, size: 20, status: 'FAILED' })
    await overrideQualityExecution(11, true, 'Verified manually')

    expect(requests).toEqual([
      expect.objectContaining({ method: 'post', url: '/data/quality-check/rules' }),
      {
        method: 'post',
        url: '/data/quality-check/datasets/8/run',
        body: { ruleIds: [] },
        params: undefined,
      },
      {
        method: 'get',
        url: '/data/quality-check/logs',
        body: undefined,
        params: { page: 1, size: 20, status: 'FAILED' },
      },
      {
        method: 'put',
        url: '/data/quality-check/executions/11/override',
        body: { passed: true, reason: 'Verified manually' },
        params: undefined,
      },
    ])
  })
})
