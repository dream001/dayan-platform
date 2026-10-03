import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import {
  getSkillLibrary,
  getSkillProjects,
  getSkillSamples,
  saveSkillAsset,
} from './skill-library'
import { http } from './http'

const defaultAdapter = http.defaults.adapter

function apiResponse(config: InternalAxiosRequestConfig, value: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data: value,
      requestId: 'request-skill',
      timestamp: '2026-10-03T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

describe('skill library API contracts', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('loads project-scoped skill summaries', async () => {
    http.defaults.adapter = async (config) => {
      expect(config.url).toBe('/data/skills')
      expect(config.params).toEqual({ projectId: 7, locale: 'zh-CN' })
      return apiResponse(config, { skillCount: 0, annotationCount: 0, skills: [] })
    }

    await getSkillLibrary(7, 'zh-CN')
  })

  it('saves skill catalog metadata', async () => {
    http.defaults.adapter = async (config) => {
      expect(config.method).toBe('put')
      expect(config.url).toBe('/data/skills/catalog/pick-and-place')
      expect(JSON.parse(String(config.data))).toEqual(expect.objectContaining({
        category: 'COMPOSITE',
        currentVersion: '1.1.0',
        tags: ['manipulation'],
      }))
      return apiResponse(config, null)
    }

    await saveSkillAsset('pick-and-place', {
      nameZh: '抓取放置',
      nameEn: 'Pick and place',
      description: 'Move an object to a target pose.',
      category: 'COMPOSITE',
      difficulty: 'INTERMEDIATE',
      status: 'PUBLISHED',
      currentVersion: '1.1.0',
      usageScene: 'Assembly',
      tags: ['manipulation'],
      template: true,
    })
  })

  it('loads projects and paged media samples', async () => {
    const requests: Array<{ url?: string, params?: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({ url: config.url, params: config.params })
      return apiResponse(config, [])
    }

    await getSkillProjects()
    await getSkillSamples('Pick cube', {
      projectId: 7,
      mediaType: 'DEPTH',
      page: 2,
    })

    expect(requests).toEqual([
      { url: '/data/skills/projects', params: undefined },
      {
        url: '/data/skills/Pick%20cube/samples',
        params: { projectId: 7, mediaType: 'DEPTH', page: 2, size: 30 },
      },
    ])
  })
})
