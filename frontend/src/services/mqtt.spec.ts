import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import {
  getMqttConnections,
  getMqttOverview,
  getMqttSubscriptions,
  saveMqttConnection,
  saveMqttSubscription,
  testMqttConnection,
} from './mqtt'
import { http } from './http'

const defaultAdapter = http.defaults.adapter

function apiResponse(config: InternalAxiosRequestConfig, value: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data: value,
      requestId: 'request-mqtt',
      timestamp: '2026-10-03T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

describe('MQTT management API contracts', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('loads overview, connections, and filtered subscriptions', async () => {
    const requests: Array<{ url?: string, params?: unknown }> = []
    http.defaults.adapter = async (config) => {
      requests.push({ url: config.url, params: config.params })
      return apiResponse(config, config.url?.endsWith('/overview') ? {} : [])
    }

    await getMqttOverview()
    await getMqttConnections()
    await getMqttSubscriptions(7)

    expect(requests).toEqual([
      { url: '/basic/mqtt/overview', params: undefined },
      { url: '/basic/mqtt/connections', params: undefined },
      { url: '/basic/mqtt/subscriptions', params: { connectionId: 7 } },
    ])
  })

  it('uses MQTT connection and subscription mutation endpoints', async () => {
    const requests: Array<{ method?: string, url?: string }> = []
    http.defaults.adapter = async (config) => {
      requests.push({ method: config.method, url: config.url })
      return apiResponse(config, {})
    }

    await saveMqttConnection(null, {
      name: 'Robot broker',
      clientId: 'dayan-console',
      brokerUrl: 'ssl://mqtt.example.com:8883',
      username: 'device',
      password: 'secret',
      tlsEnabled: true,
      cleanSession: true,
      keepAliveSeconds: 60,
      connectionTimeoutSeconds: 10,
      enabled: true,
    })
    await testMqttConnection(3)
    await saveMqttSubscription(null, {
      connectionId: 3,
      topicFilter: 'robots/+/telemetry/#',
      qos: 1,
      description: 'Robot telemetry',
      enabled: true,
    })

    expect(requests).toEqual([
      { method: 'post', url: '/basic/mqtt/connections' },
      { method: 'post', url: '/basic/mqtt/connections/3/test' },
      { method: 'post', url: '/basic/mqtt/subscriptions' },
    ])
  })
})
