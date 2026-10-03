import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import { getChartData, getChartProjects } from './charts'
import { http } from './http'

const defaultAdapter = http.defaults.adapter

function apiResponse(config: InternalAxiosRequestConfig, value: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data: value,
      requestId: 'request-chart',
      timestamp: '2026-10-02T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

describe('chart API contracts', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('loads project options from the chart-scoped endpoint', async () => {
    http.defaults.adapter = async (config) => {
      expect(config.url).toBe('/data/charts/projects')
      return apiResponse(config, [{ id: 7, code: 'VISION', name: 'Vision' }])
    }

    await expect(getChartProjects()).resolves.toEqual([
      { id: 7, code: 'VISION', name: 'Vision' },
    ])
  })

  it('requests only the selected chart and passes locale for relationships', async () => {
    const requests: Array<{ url?: string, params?: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({ url: config.url, params: config.params })
      return apiResponse(config, [])
    }

    await getChartData('relationships', 7, 'zh-CN')
    await getChartData('planning', 7, 'zh-CN')
    await getChartData('durations', 7, 'zh-CN')

    expect(requests).toEqual([
      {
        url: '/data/charts/relationships',
        params: { projectId: 7, locale: 'zh-CN' },
      },
      {
        url: '/data/charts/planning',
        params: { projectId: 7, locale: 'zh-CN' },
      },
      {
        url: '/data/charts/durations',
        params: { projectId: 7 },
      },
    ])
  })
})
