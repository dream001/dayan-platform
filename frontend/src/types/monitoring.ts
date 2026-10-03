export type MonitorRange = '1h' | '24h' | '7d'
export type ServiceState = 'UP' | 'DOWN' | 'NOT_CONFIGURED'
export type ComponentState = 'HEALTHY' | 'WARNING' | 'CRITICAL' | 'PAUSED' | 'NOT_CONFIGURED'
export type ComponentKey = 'DATABASE' | 'REDIS' | 'MINIO' | 'QUEUE' | 'CPU' | 'MEMORY'
export type ExportQueueStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELED'

export interface MetricPoint {
  databaseLatencyMs: number
  redisLatencyMs: number | null
  queueBacklog: number
  cpuUsagePercent: number
  memoryUsagePercent: number
  collectedAt: string
}

export interface MonitoringOverview {
  latest: MetricPoint
  trend: MetricPoint[]
  components: Array<{
    key: ComponentKey
    status: ComponentState
    latencyMs: number | null
  }>
  range: MonitorRange
  generatedAt: string
}

export interface ServiceStatus {
  name: string
  status: ServiceState
  latencyMs: number | null
  detail: string
}

export interface SystemInformation {
  applicationName: string
  environment: string
  version: string
  hostname: string
  operatingSystem: string
  cpuCores: number
  totalMemoryBytes: number
  usedMemoryBytes: number
  uptimeSeconds: number
  startedAt: string
  services: ServiceStatus[]
  generatedAt: string
}

export interface AccessLog {
  id: number
  userId: number | null
  username: string | null
  method: string
  requestPath: string
  statusCode: number
  durationMs: number
  ipAddress: string | null
  userAgent: string | null
  requestId: string | null
  occurredAt: string
}

export interface OnlineUser {
  sessionId: number
  userId: number
  username: string
  displayName: string
  roles: string[]
  activeAt: string
  sessionDurationSeconds: number
  currentPath: string | null
  ipAddress: string | null
}

export interface OnlineUsers {
  onlineCount: number
  todayActiveCount: number
  users: OnlineUser[]
  generatedAt: string
}

export interface LoginLog {
  id: number
  username: string | null
  result: 'SUCCESS' | 'FAILURE'
  errorSummary: string | null
  ipAddress: string | null
  occurredAt: string
}

export interface MonitoredExportTask {
  id: number
  name: string
  format: string
  status: ExportQueueStatus
  progress: number
  processedCount: number
  datasetCount: number
  fileName: string | null
  fileSize: number | null
  errorMessage: string | null
  creatorId: number
  creatorName: string
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}

export interface QueueSummary {
  pending: number
  processing: number
  completed: number
  failed: number
  canceled: number
  paused: boolean
}

export interface MonitorPageQuery {
  page: number
  size: number
  startTime?: string
  endTime?: string
}

export interface AccessLogQuery extends MonitorPageQuery {
  path?: string
  username?: string
  statusCode?: number
}

export interface LoginLogQuery extends MonitorPageQuery {
  username?: string
  ipAddress?: string
  result?: 'SUCCESS' | 'FAILURE'
}

export interface ExportMonitorQuery extends MonitorPageQuery {
  keyword?: string
  username?: string
  format?: string
  status?: ExportQueueStatus
}
