import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  ExportDatasetOption,
  ExportQuota,
  ExportTask,
  ExportTaskDetail,
  ExportTaskPayload,
  ExportTaskQuery,
} from '@/types/data-export'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getExportDatasets(params: {
  projectId?: number
  collectorId?: number
  from?: string
  to?: string
  keyword?: string
}) {
  return data(await http.get<ApiResponse<ExportDatasetOption[]>>('/data/exports/datasets', { params }))
}

export async function getExportTasks(params: ExportTaskQuery) {
  return data(await http.get<ApiResponse<PageResponse<ExportTask>>>('/data/exports', { params }))
}

export async function getExportTask(id: number) {
  return data(await http.get<ApiResponse<ExportTaskDetail>>(`/data/exports/${id}`))
}

export async function createExportTask(payload: ExportTaskPayload) {
  return data(await http.post<ApiResponse<ExportTask>>('/data/exports', payload))
}

export async function getExportQuota() {
  return data(await http.get<ApiResponse<ExportQuota>>('/data/exports/quota/current'))
}

export async function downloadExport(id: number) {
  return http.get<Blob>(`/data/exports/${id}/download`, { responseType: 'blob' })
}
