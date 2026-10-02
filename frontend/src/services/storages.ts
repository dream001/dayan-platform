import type { ApiResponse } from '@/types/api'
import type {
  CloudStorage,
  CloudStorageOverview,
  CloudStoragePayload,
} from '@/types/storage'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getStorages() {
  return data(await http.get<ApiResponse<CloudStorage[]>>('/basic/storages'))
}

export async function getStorageOverview() {
  return data(await http.get<ApiResponse<CloudStorageOverview>>('/basic/storages/overview'))
}

export async function createStorage(payload: CloudStoragePayload) {
  return data(await http.post<ApiResponse<CloudStorage>>('/basic/storages', payload))
}

export async function updateStorage(id: number, payload: CloudStoragePayload) {
  return data(await http.put<ApiResponse<CloudStorage>>(`/basic/storages/${id}`, payload))
}

export async function testStorage(id: number) {
  return data(await http.post<ApiResponse<CloudStorage>>(`/basic/storages/${id}/test`))
}

export async function setDefaultStorage(id: number) {
  return data(await http.patch<ApiResponse<CloudStorage>>(`/basic/storages/${id}/default`))
}

export async function deleteStorage(id: number) {
  await http.delete<ApiResponse<null>>(`/basic/storages/${id}`)
}
