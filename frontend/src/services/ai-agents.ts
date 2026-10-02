import type { ApiResponse } from '@/types/api'
import type { AiAgent, AiAgentDebugResult, AiAgentPayload } from '@/types/ai-agent'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getAiAgents() {
  return data(await http.get<ApiResponse<AiAgent[]>>('/basic/agents'))
}

export async function createAiAgent(payload: AiAgentPayload) {
  return data(await http.post<ApiResponse<AiAgent>>('/basic/agents', payload))
}

export async function updateAiAgent(id: number, payload: AiAgentPayload) {
  return data(await http.put<ApiResponse<AiAgent>>(`/basic/agents/${id}`, payload))
}

export async function changeAiAgentStatus(id: number, enabled: boolean) {
  return data(await http.patch<ApiResponse<AiAgent>>(`/basic/agents/${id}/status`, { enabled }))
}

export async function deleteAiAgent(id: number) {
  await http.delete<ApiResponse<null>>(`/basic/agents/${id}`)
}

export async function debugAiAgent(id: number, message: string) {
  return data(await http.post<ApiResponse<AiAgentDebugResult>>(
    `/basic/agents/${id}/debug`,
    { message },
  ))
}
