import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import {
  changeAiModelStatus,
  createAiModel,
  getAiModels,
  testAiModel,
} from './ai-models'
import { http } from './http'

const defaultAdapter = http.defaults.adapter

function apiResponse(config: InternalAxiosRequestConfig, value: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data: value,
      requestId: 'request-model',
      timestamp: '2026-10-02T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

describe('AI model API contracts', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('passes model filters to the list endpoint', async () => {
    const page = { page: 1, size: 20, total: 0, totalPages: 0, items: [] }
    http.defaults.adapter = async (config) => {
      expect(config.url).toBe('/basic/models')
      expect(config.params).toEqual({
        page: 1,
        size: 20,
        keyword: 'qwen',
        modelType: 'CHAT',
        enabled: true,
      })
      return apiResponse(config, page)
    }

    await expect(getAiModels({
      page: 1,
      size: 20,
      keyword: 'qwen',
      modelType: 'CHAT',
      enabled: true,
    })).resolves.toEqual(page)
  })

  it('uses model-scoped create, status, and test endpoints', async () => {
    const requests: Array<{ method?: string; url?: string; body?: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({
        method: config.method,
        url: config.url,
        body: config.data ? JSON.parse(String(config.data)) : undefined,
      })
      return apiResponse(config, {})
    }

    await createAiModel({
      manufacturer: '豆包',
      name: 'doubao-seed-1-6',
      accessAddress: 'https://ark.cn-beijing.volces.com',
      modelUrl: 'https://ark.cn-beijing.volces.com/api/v3/chat/completions',
      modelType: 'CHAT',
      accessKey: '',
      secretKey: 'secret',
      enabled: true,
    })
    await changeAiModelStatus(7, false)
    await testAiModel(7)

    expect(requests).toEqual([
      {
        method: 'post',
        url: '/basic/models',
        body: {
          manufacturer: '豆包',
          name: 'doubao-seed-1-6',
          accessAddress: 'https://ark.cn-beijing.volces.com',
          modelUrl: 'https://ark.cn-beijing.volces.com/api/v3/chat/completions',
          modelType: 'CHAT',
          accessKey: '',
          secretKey: 'secret',
          enabled: true,
        },
      },
      {
        method: 'patch',
        url: '/basic/models/7/status',
        body: { enabled: false },
      },
      {
        method: 'post',
        url: '/basic/models/7/test',
        body: undefined,
      },
    ])
  })
})
