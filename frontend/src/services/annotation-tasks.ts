import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  AnnotationTaskCounts,
  AnnotationTaskCreatePayload,
  AnnotationTaskDetail,
  AnnotationTaskQuery,
  AnnotationTaskSummary,
  AnnotationTaskUpdatePayload,
} from '@/types/annotation-task'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getAnnotationTasks(params: AnnotationTaskQuery) {
  return data(await http.get<ApiResponse<PageResponse<AnnotationTaskSummary>>>(
    '/data/annotation-tasks',
    { params },
  ))
}

export async function getAnnotationTaskCounts() {
  return data(await http.get<ApiResponse<AnnotationTaskCounts>>('/data/annotation-tasks/counts'))
}

export async function getAnnotationTaskOptions(projectId?: number) {
  return data(await http.get<ApiResponse<import('@/types/annotation-task').AnnotationTaskOptions>>(
    '/data/annotation-tasks/options',
    { params: { projectId } },
  ))
}

export async function getAnnotationTask(id: number) {
  return data(await http.get<ApiResponse<AnnotationTaskDetail>>(`/data/annotation-tasks/${id}`))
}

export async function createAnnotationTasks(payload: AnnotationTaskCreatePayload) {
  return data(await http.post<ApiResponse<AnnotationTaskDetail[]>>('/data/annotation-tasks', payload))
}

export async function updateAnnotationTask(id: number, payload: AnnotationTaskUpdatePayload) {
  return data(await http.put<ApiResponse<AnnotationTaskDetail>>(
    `/data/annotation-tasks/${id}`,
    payload,
  ))
}

export async function changeAnnotationTaskStatus(
  id: number,
  status: string,
  rejectionReason?: string,
) {
  return data(await http.patch<ApiResponse<AnnotationTaskDetail>>(
    `/data/annotation-tasks/${id}/status`,
    { status, rejectionReason },
  ))
}

export async function reviewAnnotationDataset(
  taskId: number,
  relationId: number,
  result: 'VALID' | 'INVALID' | null,
  rejectionReason?: string,
) {
  return data(await http.patch<ApiResponse<AnnotationTaskDetail>>(
    `/data/annotation-tasks/${taskId}/datasets/${relationId}/review`,
    { result, rejectionReason },
  ))
}

export async function batchAnnotateTask(
  taskId: number,
  payload: {
    mode: 'QUICK' | 'COPY' | 'REPLACE'
    description?: string
    sourceRelationId?: number
    findText?: string
    replaceText?: string
  },
) {
  return data(await http.patch<ApiResponse<AnnotationTaskDetail>>(
    `/data/annotation-tasks/${taskId}/batch-annotation`,
    payload,
  ))
}

export async function deleteAnnotationTask(id: number) {
  await http.delete<ApiResponse<null>>(`/data/annotation-tasks/${id}`)
}
