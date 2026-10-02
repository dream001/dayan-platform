export interface AiAgent {
  id: number
  name: string
  description: string | null
  systemPrompt: string
  modelId: number
  modelName: string
  modelManufacturer: string
  modelType: string
  temperature: number
  maxTokens: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface AiAgentPayload {
  name: string
  description: string
  systemPrompt: string
  modelId: number | null
  temperature: number
  maxTokens: number
  enabled: boolean
}

export interface AiAgentDebugResult {
  content: string
  modelName: string
  latencyMs: number
  completedAt: string
}
