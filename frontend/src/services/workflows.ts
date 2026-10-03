import type { ApiResponse } from '@/types/api'
import type {
  ActionRule,
  ActionRulePayload,
  MatchRule,
  MatchRulePayload,
  MatchTestResult,
  WorkflowDatasetOption,
  WorkflowDefinition,
  WorkflowOverview,
  WorkflowPayload,
  WorkflowProjectOption,
  WorkflowRun,
  WorkflowRunStatus,
} from '@/types/workflow'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getWorkflowOverview() {
  return data(await http.get<ApiResponse<WorkflowOverview>>('/workflows/overview'))
}

export async function getMatchRules(params: {
  projectId?: number
  enabled?: boolean
  keyword?: string
} = {}) {
  return data(await http.get<ApiResponse<MatchRule[]>>('/workflows/match-rules', { params }))
}

export async function saveMatchRule(id: number | null, payload: MatchRulePayload) {
  const response = id
    ? await http.put<ApiResponse<MatchRule>>(`/workflows/match-rules/${id}`, payload)
    : await http.post<ApiResponse<MatchRule>>('/workflows/match-rules', payload)
  return data(response)
}

export async function testMatchRule(
  id: number,
  projectId: number | null,
  datasetIds: number[] = [],
) {
  return data(await http.post<ApiResponse<MatchTestResult>>(
    `/workflows/match-rules/${id}/test`,
    { projectId, datasetIds },
  ))
}

export async function getActionRules(params: {
  projectId?: number
  enabled?: boolean
  keyword?: string
} = {}) {
  return data(await http.get<ApiResponse<ActionRule[]>>('/workflows/action-rules', { params }))
}

export async function saveActionRule(id: number | null, payload: ActionRulePayload) {
  const response = id
    ? await http.put<ApiResponse<ActionRule>>(`/workflows/action-rules/${id}`, payload)
    : await http.post<ApiResponse<ActionRule>>('/workflows/action-rules', payload)
  return data(response)
}

export async function getWorkflowDefinitions(params: {
  projectId?: number
  enabled?: boolean
  keyword?: string
} = {}) {
  return data(await http.get<ApiResponse<WorkflowDefinition[]>>(
    '/workflows/definitions',
    { params },
  ))
}

export async function saveWorkflowDefinition(id: number | null, payload: WorkflowPayload) {
  const response = id
    ? await http.put<ApiResponse<WorkflowDefinition>>(`/workflows/definitions/${id}`, payload)
    : await http.post<ApiResponse<WorkflowDefinition>>('/workflows/definitions', payload)
  return data(response)
}

export async function deleteWorkflowResource(
  resource: 'definitions' | 'match-rules' | 'action-rules',
  id: number,
) {
  await http.delete<ApiResponse<null>>(`/workflows/${resource}/${id}`)
}

export async function getWorkflowProjects() {
  return data(await http.get<ApiResponse<WorkflowProjectOption[]>>('/workflows/projects'))
}

export async function getWorkflowDatasets(projectId?: number) {
  return data(await http.get<ApiResponse<WorkflowDatasetOption[]>>(
    '/workflows/datasets',
    { params: { projectId } },
  ))
}

export async function getWorkflowMatchingDatasets(workflowId: number) {
  return data(await http.get<ApiResponse<WorkflowDatasetOption[]>>(
    `/workflows/definitions/${workflowId}/datasets`,
  ))
}

export async function getWorkflowRuns(params: {
  projectId?: number
  status?: WorkflowRunStatus
} = {}) {
  return data(await http.get<ApiResponse<WorkflowRun[]>>('/workflows/runs', { params }))
}

export async function startWorkflowRun(workflowId: number, datasetId: number) {
  return data(await http.post<ApiResponse<WorkflowRun>>(
    '/workflows/runs',
    { workflowId, datasetId },
  ))
}
