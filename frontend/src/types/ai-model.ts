export type ModelType =
  | 'CHAT'
  | 'EMBEDDING'
  | 'MULTIMODAL'
  | 'RERANK'
  | 'IMAGE'
  | 'VIDEO'
  | 'AUDIO'

export type ModelTestStatus = 'NEVER' | 'SUCCESS' | 'FAILED'

export interface AiModel {
  id: number
  manufacturer: string
  name: string
  accessAddress: string
  modelUrl: string
  modelType: ModelType
  accessKeyConfigured: boolean
  secretKeyConfigured: boolean
  enabled: boolean
  lastTestStatus: ModelTestStatus
  lastTestMessage: string | null
  lastTestLatencyMs: number | null
  lastTestedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AiModelPayload {
  manufacturer: string
  name: string
  accessAddress: string
  modelUrl: string
  modelType: ModelType
  accessKey: string
  secretKey: string
  enabled: boolean
}

export interface AiModelQuery {
  page: number
  size: number
  keyword?: string
  modelType?: ModelType
  enabled?: boolean
}

export interface ModelTestResult {
  success: boolean
  message: string
  latencyMs: number
  testedAt: string
}

export interface ModelDebugResult {
  success: boolean
  statusCode: number
  latencyMs: number
  output: string
  message: string
  testedAt: string
}
