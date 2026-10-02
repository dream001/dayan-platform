export type StorageProvider =
  | 'TENCENT_COS'
  | 'ALIYUN_OSS'
  | 'HUAWEI_OBS'
  | 'AWS_S3'
  | 'AZURE_BLOB'
  | 'CLOUDFLARE_R2'
  | 'MINIO'

export type StorageStatus = 'NEVER' | 'AVAILABLE' | 'UNAVAILABLE'

export interface CloudStorage {
  id: number
  storageKey: string
  name: string
  provider: StorageProvider
  endpoint: string
  region: string | null
  bucket: string
  accessKeyHint: string | null
  credentialConfigured: boolean
  defaultStorage: boolean
  enabled: boolean
  status: StorageStatus
  lastCheckMessage: string | null
  lastCheckLatencyMs: number | null
  usageBytes: number
  objectCount: number
  lastCheckedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CloudStorageOverview {
  total: number
  enabled: number
  available: number
  usageBytes: number
  objectCount: number
  providerCounts: Partial<Record<StorageProvider, number>>
}

export interface CloudStoragePayload {
  storageKey: string
  name: string
  provider: StorageProvider
  endpoint: string
  region: string
  bucket: string
  accessKey: string
  secretKey: string
  enabled: boolean
}
