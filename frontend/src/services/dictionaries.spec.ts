import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import {
  batchCreateDictionaries,
  batchDeleteDictionaries,
  getDictionaries,
} from './dictionaries'
import { http } from './http'

const defaultAdapter = http.defaults.adapter

function apiResponse(config: InternalAxiosRequestConfig, value: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data: value,
      requestId: 'request-dictionary',
      timestamp: '2026-10-02T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

describe('dictionary API contracts', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('passes dictionary filters and sorting to the list endpoint', async () => {
    const page = { page: 1, size: 20, total: 0, totalPages: 0, items: [] }
    http.defaults.adapter = async (config) => {
      expect(config.url).toBe('/data/dictionaries')
      expect(config.params).toEqual({
        page: 1,
        size: 20,
        type: 'SKILL',
        keyword: 'pick',
        scope: 'PROJECT',
        projectId: 7,
        sort: 'ENGLISH',
        direction: 'ASC',
      })
      return apiResponse(config, page)
    }

    await expect(getDictionaries({
      page: 1,
      size: 20,
      type: 'SKILL',
      keyword: 'pick',
      scope: 'PROJECT',
      projectId: 7,
      sort: 'ENGLISH',
      direction: 'ASC',
    })).resolves.toEqual(page)
  })

  it('submits batch create and delete payloads', async () => {
    const requests: Array<{ method?: string; url?: string; body?: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({
        method: config.method,
        url: config.url,
        body: config.data ? JSON.parse(String(config.data)) : undefined,
      })
      return apiResponse(config, { created: 2, deleted: 0, skipped: 0 })
    }

    await batchCreateDictionaries({
      dictionaryType: 'OBJECT',
      scope: 'SHARED',
      projectId: null,
      content: 'apple,苹果\ncup,杯子',
    })
    await batchDeleteDictionaries([3, 8])

    expect(requests).toEqual([
      {
        method: 'post',
        url: '/data/dictionaries/batch',
        body: {
          dictionaryType: 'OBJECT',
          scope: 'SHARED',
          projectId: null,
          content: 'apple,苹果\ncup,杯子',
        },
      },
      {
        method: 'post',
        url: '/data/dictionaries/batch-delete',
        body: { ids: [3, 8] },
      },
    ])
  })
})
