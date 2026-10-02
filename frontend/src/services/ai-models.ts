import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  AiModel,
  AiModelPayload,
  AiModelQuery,
  ModelTestResult,
} from '@/types/ai-model'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getAiModels(params: AiModelQuery) {
  return data(await http.get<ApiResponse<PageResponse<AiModel>>>('/basic/models', { params }))
}

export async function createAiModel(payload: AiModelPayload) {
  return data(await http.post<ApiResponse<AiModel>>('/basic/models', payload))
}

export async function updateAiModel(id: number, payload: AiModelPayload) {
  return data(await http.put<ApiResponse<AiModel>>(`/basic/models/${id}`, payload))
}

export async function changeAiModelStatus(id: number, enabled: boolean) {
  return data(await http.patch<ApiResponse<AiModel>>(`/basic/models/${id}/status`, { enabled }))
}

export async function deleteAiModel(id: number) {
  await http.delete<ApiResponse<null>>(`/basic/models/${id}`)
}

export async function testAiModel(id: number) {
  return data(await http.post<ApiResponse<ModelTestResult>>(`/basic/models/${id}/test`))
}
