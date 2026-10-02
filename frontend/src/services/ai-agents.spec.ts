import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import {
  changeAiAgentStatus,
  createAiAgent,
  debugAiAgent,
  getAiAgents,
} from './ai-agents'
import { http } from './http'

const defaultAdapter = http.defaults.adapter

function apiResponse(config: InternalAxiosRequestConfig, value: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data: value,
      requestId: 'request-agent',
      timestamp: '2026-10-02T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

describe('AI Agent API contracts', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('uses Agent CRUD, status, and debug endpoints', async () => {
    const requests: Array<{ method?: string; url?: string; body?: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({
        method: config.method,
        url: config.url,
        body: config.data ? JSON.parse(String(config.data)) : undefined,
      })
      return apiResponse(config, [])
    }

    const payload = {
      name: 'Quality Agent',
      description: 'Checks records',
      systemPrompt: 'Return strict JSON.',
      modelId: 9,
      temperature: 0.4,
      maxTokens: 1024,
      enabled: true,
    }
    await getAiAgents()
    await createAiAgent(payload)
    await changeAiAgentStatus(5, false)
    await debugAiAgent(5, 'Check this record')

    expect(requests).toEqual([
      { method: 'get', url: '/basic/agents', body: undefined },
      { method: 'post', url: '/basic/agents', body: payload },
      { method: 'patch', url: '/basic/agents/5/status', body: { enabled: false } },
      { method: 'post', url: '/basic/agents/5/debug', body: { message: 'Check this record' } },
    ])
  })
})
