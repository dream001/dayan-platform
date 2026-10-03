import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import {
  createRobot,
  deleteRobot,
  getRobotDatasets,
  getRobots,
  updateRobot,
} from './robots'
import { http } from './http'

const defaultAdapter = http.defaults.adapter

function apiResponse(config: InternalAxiosRequestConfig, value: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data: value,
      requestId: 'request-robot',
      timestamp: '2026-10-02T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

const payload = {
  name: 'test-arm',
  iconUrl: 'https://example.com/robot.png',
  titleZh: '测试机械臂',
  titleEn: 'Test Arm',
  robotType: 'DESKTOP_ARM' as const,
  actionMappingSupport: 'SUPPORTED' as const,
  description: 'Test robot',
  company: 'Dayan',
  introductionUrl: 'https://example.com/robots/test-arm',
}

describe('robot API contracts', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('loads all robots or filters by robot type', async () => {
    const requests: Array<{ url?: string; params?: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({ url: config.url, params: config.params })
      return apiResponse(config, [])
    }

    await getRobots()
    await getRobots('HUMANOID')

    expect(requests).toEqual([
      { url: '/basic/robots', params: undefined },
      { url: '/basic/robots', params: { robotType: 'HUMANOID' } },
    ])
  })

  it('uses robot-scoped mutation and dataset endpoints', async () => {
    const requests: Array<{ method?: string; url?: string; body?: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({
        method: config.method,
        url: config.url,
        body: config.data ? JSON.parse(String(config.data)) : undefined,
      })
      return apiResponse(config, [])
    }

    await createRobot(payload)
    await updateRobot(7, payload)
    await getRobotDatasets(7)
    await deleteRobot(7)

    expect(requests).toEqual([
      { method: 'post', url: '/basic/robots', body: payload },
      { method: 'put', url: '/basic/robots/7', body: payload },
      { method: 'get', url: '/basic/robots/7/datasets', body: undefined },
      { method: 'delete', url: '/basic/robots/7', body: undefined },
    ])
  })
})
