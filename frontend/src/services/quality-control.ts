import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  QualityDatasetOption,
  QualityExecution,
  QualityExecutionStatus,
  QualityOverview,
  QualityProjectOption,
  QualityRule,
  QualityRulePayload,
  QualityRunResult,
} from '@/types/quality-control'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getQualityOverview() {
  return data(await http.get<ApiResponse<QualityOverview>>('/data/quality-check/overview'))
}

export async function getQualityRules(params: {
  page: number
  size: number
  projectId?: number
  enabled?: boolean
  keyword?: string
}) {
  return data(await http.get<ApiResponse<PageResponse<QualityRule>>>(
    '/data/quality-check/rules',
    { params },
  ))
}

export async function createQualityRule(payload: QualityRulePayload) {
  return data(await http.post<ApiResponse<QualityRule>>('/data/quality-check/rules', payload))
}

export async function updateQualityRule(id: number, payload: QualityRulePayload) {
  return data(await http.put<ApiResponse<QualityRule>>(
    `/data/quality-check/rules/${id}`,
    payload,
  ))
}

export async function deleteQualityRule(id: number) {
  await http.delete<ApiResponse<null>>(`/data/quality-check/rules/${id}`)
}

export async function getQualityProjects() {
  return data(await http.get<ApiResponse<QualityProjectOption[]>>('/data/quality-check/projects'))
}

export async function getQualityDatasets() {
  return data(await http.get<ApiResponse<QualityDatasetOption[]>>('/data/quality-check/datasets'))
}

export async function runQualityCheck(datasetId: number, ruleIds: number[] = []) {
  return data(await http.post<ApiResponse<QualityRunResult>>(
    `/data/quality-check/datasets/${datasetId}/run`,
    { ruleIds },
  ))
}

export async function getQualityLogs(params: {
  page: number
  size: number
  projectId?: number
  datasetId?: number
  ruleId?: number
  status?: QualityExecutionStatus
  effectivePass?: boolean
  overridden?: boolean
  keyword?: string
}) {
  return data(await http.get<ApiResponse<PageResponse<QualityExecution>>>(
    '/data/quality-check/logs',
    { params },
  ))
}

export async function overrideQualityExecution(
  id: number,
  passed: boolean | null,
  reason: string | null,
) {
  return data(await http.put<ApiResponse<QualityExecution>>(
    `/data/quality-check/executions/${id}/override`,
    { passed, reason },
  ))
}
