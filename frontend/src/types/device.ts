export interface Device {
  id: number
  agentId: string
  deviceCode: string
  remark: string | null
  robotId: number | null
  robotName: string | null
  projectId: number | null
  projectName: string | null
  collectionTaskId: number | null
  collectionTaskName: string | null
  activated: boolean
  online: boolean
  lastReportAt: string | null
  hostname: string | null
  operatingSystem: string | null
  platform: string | null
  kernelVersion: string | null
  ipAddresses: string[]
  cpuUsage: number | null
  memoryUsage: number | null
  diskUsage: number | null
  cpuTemperature: number | null
  memoryUsedBytes: number | null
  diskAvailableBytes: number | null
  activeTcpConnections: number
  uptimeSeconds: number
  createdAt: string
  updatedAt: string
}

export interface DeviceMetric {
  cpuUsage: number
  memoryUsage: number
  diskUsage: number
  cpuTemperature: number | null
  activeTcpConnections: number
  reportedAt: string
}

export interface DeviceDetail {
  device: Device
  metrics: DeviceMetric[]
}

export interface DevicePayload {
  deviceCode: string
  remark: string
  robotId: number | null
  projectId: number | null
}

export interface DeviceRegistration {
  device: Device
  agentToken: string
}

export interface DeviceOption {
  id: number
  name: string
}

export interface DeviceOptions {
  robots: DeviceOption[]
  projects: DeviceOption[]
  collectionTasks: DeviceOption[]
}

export interface InstallOptions {
  pollIntervalSeconds: number
  videoFps: number
  rosDomainId: number | null
  imageTopic: string
  maxVideoWidth: number | null
  remoteControlEnabled: boolean
  connectionPassword: string
  verbose: boolean
}

export interface BatchResult {
  succeeded: number
  failed: number
}
