import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import {
  addDatasetTags,
  assignDatasetRobot,
  createAnnotationTask,
  deleteDatasets,
  getDataset,
  getDatasetStats,
  getDatasetStorageTotal,
  getDatasets,
  getRobotOptions,
  getTagOptions,
  getTrashDatasets,
  getUserOptions,
  importDatasets,
  refreshDatasets,
  removeDatasetTags,
  renameDatasets,
  restoreDataset,
} from './dataset'
import { http } from './http'

const defaultAdapter = http.defaults.adapter

function apiResponse(config: InternalAxiosRequestConfig, value: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data: value,
      requestId: 'request-dataset',
      timestamp: '2026-10-02T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

const baseQuery = {
  page: 1,
  size: 20,
  scope: 'ALL' as const,
}

describe('dataset API contracts', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('loads datasets, storage total and unwraps detail', async () => {
    const requests: string[] = []
    http.defaults.adapter = async (config) => {
      requests.push(config.url ?? '')
      if (config.url?.startsWith('/datasets/storage-total')) {
        return apiResponse(config, 4096)
      }
      if (config.url?.startsWith('/datasets/9')) {
        return apiResponse(config, { dataset: { id: 9 } })
      }
      return apiResponse(config, { items: [], total: 0 })
    }

    const page = await getDatasets(baseQuery)
    const total = await getDatasetStorageTotal(baseQuery)
    const detail = await getDataset(9)

    expect(page).toEqual({ items: [], total: 0 })
    expect(total).toBe(4096)
    expect(detail).toEqual({ dataset: { id: 9 } })
    expect(requests[0]).toBe('/datasets')
    expect(requests[1]).toBe('/datasets/storage-total')
    expect(requests[2]).toBe('/datasets/9')
  })

  it('uses the batch mutation endpoints with the documented routes', async () => {
    const requests: Array<{ method?: string; url?: string; data?: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({ method: config.method, url: config.url, data: config.data })
      return apiResponse(config, [])
    }

    await renameDatasets([{ id: 1, name: 'renamed' }])
    await createAnnotationTask([1, 2], 'task')
    await addDatasetTags([1], ['a'])
    await removeDatasetTags([1], ['a'])
    await refreshDatasets([1])
    await deleteDatasets([1])
    await importDatasets([1], 5)
    await assignDatasetRobot([1], 'R-1')
    await getDatasetStats([1])
    await restoreDataset(1)

    expect(requests).toEqual([
      { method: 'post', url: '/datasets/batch/rename', data: { items: [{ id: 1, name: 'renamed' }] } },
      { method: 'post', url: '/datasets/batch/annotate', data: { ids: [1, 2], name: 'task' } },
      { method: 'post', url: '/datasets/batch/tags/add', data: { ids: [1], tags: ['a'] } },
      { method: 'post', url: '/datasets/batch/tags/remove', data: { ids: [1], tags: ['a'] } },
      { method: 'post', url: '/datasets/batch/refresh', data: { ids: [1] } },
      { method: 'post', url: '/datasets/batch/delete', data: { ids: [1] } },
      { method: 'post', url: '/datasets/batch/import', data: { ids: [1], projectId: 5 } },
      { method: 'post', url: '/datasets/batch/robot', data: { ids: [1], robotCode: 'R-1' } },
      { method: 'post', url: '/datasets/batch/stats', data: { ids: [1] } },
      { method: 'post', url: '/datasets/1/restore' },
    ])
  })

  it('loads trash and option endpoints', async () => {
    const requests: string[] = []
    http.defaults.adapter = async (config) => {
      requests.push(config.url ?? '')
      return apiResponse(config, [])
    }

    await getTrashDatasets()
    await getRobotOptions()
    await getTagOptions()
    await getUserOptions()

    expect(requests).toEqual([
      '/datasets/trash',
      '/datasets/options/robots',
      '/datasets/options/tags',
      '/datasets/options/users',
    ])
  })
})
