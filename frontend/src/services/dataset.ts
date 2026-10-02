import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  DatasetDetail,
  DatasetItemResult,
  DatasetOption,
  DatasetQuery,
  DatasetRenameItem,
  DatasetStats,
  DatasetView,
} from '@/types/dataset'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getDatasets(params: DatasetQuery) {
  return data(await http.get<ApiResponse<PageResponse<DatasetView>>>('/datasets', { params }))
}

export async function getDatasetStorageTotal(params: DatasetQuery) {
  return data(await http.get<ApiResponse<number>>('/datasets/storage-total', { params }))
}

export async function getDataset(id: number) {
  return data(await http.get<ApiResponse<DatasetDetail>>(`/datasets/${id}`))
}

export async function renameDatasets(items: DatasetRenameItem[]) {
  return data(await http.post<ApiResponse<DatasetItemResult[]>>('/datasets/batch/rename', { items }))
}

export async function createAnnotationTask(ids: number[], name: string) {
  return data(await http.post<ApiResponse<unknown>>('/datasets/batch/annotate', { ids, name }))
}

export async function addDatasetTags(ids: number[], tags: string[]) {
  await http.post<ApiResponse<null>>('/datasets/batch/tags/add', { ids, tags })
}

export async function removeDatasetTags(ids: number[], tags: string[]) {
  await http.post<ApiResponse<null>>('/datasets/batch/tags/remove', { ids, tags })
}

export async function refreshDatasets(ids: number[]) {
  return data(await http.post<ApiResponse<DatasetItemResult[]>>('/datasets/batch/refresh', { ids }))
}

export async function deleteDatasets(ids: number[]) {
  await http.post<ApiResponse<null>>('/datasets/batch/delete', { ids })
}

export async function importDatasets(ids: number[], projectId: number) {
  await http.post<ApiResponse<null>>('/datasets/batch/import', { ids, projectId })
}

export async function assignDatasetRobot(ids: number[], robotCode: string | null) {
  await http.post<ApiResponse<null>>('/datasets/batch/robot', { ids, robotCode })
}

export async function getDatasetStats(ids: number[]) {
  return data(await http.post<ApiResponse<DatasetStats>>('/datasets/batch/stats', { ids }))
}

export async function getTrashDatasets() {
  return data(await http.get<ApiResponse<DatasetView[]>>('/datasets/trash'))
}

export async function restoreDataset(id: number) {
  await http.post<ApiResponse<null>>(`/datasets/${id}/restore`)
}

export async function getRobotOptions() {
  return data(await http.get<ApiResponse<string[]>>('/datasets/options/robots'))
}

export async function getTagOptions() {
  return data(await http.get<ApiResponse<string[]>>('/datasets/options/tags'))
}

export async function getUserOptions() {
  return data(await http.get<ApiResponse<DatasetOption[]>>('/datasets/options/users'))
}
