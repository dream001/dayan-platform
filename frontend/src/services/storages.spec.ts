import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import {
  createStorage,
  getStorageOverview,
  getStorages,
  setDefaultStorage,
  testStorage,
} from './storages'
import { http } from './http'

const defaultAdapter = http.defaults.adapter

function apiResponse(config: InternalAxiosRequestConfig, value: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data: value,
      requestId: 'request-storage',
      timestamp: '2026-10-02T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

describe('storage API contracts', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('loads connection details and real usage overview', async () => {
    const requests: string[] = []
    http.defaults.adapter = async (config) => {
      requests.push(config.url ?? '')
      return apiResponse(config, config.url?.endsWith('/overview') ? { total: 1 } : [])
    }

    await getStorages()
    await getStorageOverview()

    expect(requests).toEqual(['/basic/storages', '/basic/storages/overview'])
  })

  it('uses storage-scoped mutation endpoints', async () => {
    const requests: Array<{ method?: string; url?: string }> = []
    http.defaults.adapter = async (config) => {
      requests.push({ method: config.method, url: config.url })
      return apiResponse(config, {})
    }

    await createStorage({
      storageKey: 'archive',
      name: 'Archive',
      provider: 'AWS_S3',
      endpoint: 'https://s3.amazonaws.com',
      region: 'us-east-1',
      bucket: 'archive',
      accessKey: 'access',
      secretKey: 'secret',
      enabled: true,
    })
    await testStorage(7)
    await setDefaultStorage(7)

    expect(requests).toEqual([
      { method: 'post', url: '/basic/storages' },
      { method: 'post', url: '/basic/storages/7/test' },
      { method: 'patch', url: '/basic/storages/7/default' },
    ])
  })
})
