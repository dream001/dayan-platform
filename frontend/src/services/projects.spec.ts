import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import {
  changeProjectStatus,
  getProjects,
  saveProjectMember,
} from './projects'
import { http } from './http'

const defaultAdapter = http.defaults.adapter

function apiResponse(config: InternalAxiosRequestConfig, value: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data: value,
      requestId: 'request-project',
      timestamp: '2026-10-02T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

describe('project API contracts', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('passes project filters to the list endpoint', async () => {
    const page = { page: 1, size: 20, total: 0, totalPages: 0, items: [] }
    http.defaults.adapter = async (config) => {
      expect(config.url).toBe('/basic/projects')
      expect(config.params).toEqual({
        page: 1,
        size: 20,
        keyword: 'vision',
        status: 'ACTIVE',
        projectType: 'TEAM',
      })
      return apiResponse(config, page)
    }

    await expect(getProjects({
      page: 1,
      size: 20,
      keyword: 'vision',
      status: 'ACTIVE',
      projectType: 'TEAM',
    })).resolves.toEqual(page)
  })

  it('submits lifecycle and member changes to project-scoped endpoints', async () => {
    const requests: Array<{ method?: string; url?: string; body: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({
        method: config.method,
        url: config.url,
        body: JSON.parse(String(config.data)),
      })
      return apiResponse(config, {})
    }

    await changeProjectStatus(7, 'ACTIVE')
    await saveProjectMember(7, {
      userId: 12,
      role: 'ANNOTATOR',
      dataAccessLevel: 'READ_WRITE',
      validFrom: null,
      validUntil: null,
    })

    expect(requests).toEqual([
      { method: 'patch', url: '/basic/projects/7/status', body: { status: 'ACTIVE' } },
      {
        method: 'put',
        url: '/basic/projects/7/members',
        body: {
          userId: 12,
          role: 'ANNOTATOR',
          dataAccessLevel: 'READ_WRITE',
          validFrom: null,
          validUntil: null,
        },
      },
    ])
  })
})
