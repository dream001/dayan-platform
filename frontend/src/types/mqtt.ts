export type MqttConnectionStatus = 'NEVER' | 'AVAILABLE' | 'UNAVAILABLE'

export interface MqttConnection {
  id: number
  name: string
  clientId: string
  brokerUrl: string
  usernameHint: string | null
  credentialConfigured: boolean
  tlsEnabled: boolean
  cleanSession: boolean
  keepAliveSeconds: number
  connectionTimeoutSeconds: number
  enabled: boolean
  status: MqttConnectionStatus
  lastCheckMessage: string | null
  lastCheckLatencyMs: number | null
  lastCheckedAt: string | null
  subscriptionCount: number
  createdAt: string
  updatedAt: string
}

export interface MqttConnectionPayload {
  name: string
  clientId: string
  brokerUrl: string
  username: string
  password: string
  tlsEnabled: boolean
  cleanSession: boolean
  keepAliveSeconds: number
  connectionTimeoutSeconds: number
  enabled: boolean
}

export interface MqttSubscription {
  id: number
  connectionId: number
  connectionName: string
  topicFilter: string
  qos: number
  description: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface MqttSubscriptionPayload {
  connectionId: number | null
  topicFilter: string
  qos: number
  description: string
  enabled: boolean
}

export interface MqttOverview {
  connectionCount: number
  enabledConnectionCount: number
  availableConnectionCount: number
  subscriptionCount: number
  enabledSubscriptionCount: number
}
