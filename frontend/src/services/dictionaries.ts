import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  DictionaryBatchPayload,
  DictionaryBatchResult,
  DictionaryItem,
  DictionaryOverview,
  DictionaryPayload,
  DictionaryProjectOption,
  DictionaryQuery,
  DictionaryType,
} from '@/types/dictionary'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getDictionaries(params: DictionaryQuery) {
  return data(await http.get<ApiResponse<PageResponse<DictionaryItem>>>(
    '/data/dictionaries',
    { params },
  ))
}

export async function getDictionaryOverview() {
  return data(await http.get<ApiResponse<DictionaryOverview>>('/data/dictionaries/overview'))
}

export async function getDictionaryProjectOptions() {
  return data(await http.get<ApiResponse<DictionaryProjectOption[]>>(
    '/data/dictionaries/project-options',
  ))
}

export async function createDictionary(payload: DictionaryPayload) {
  return data(await http.post<ApiResponse<DictionaryItem>>('/data/dictionaries', payload))
}

export async function batchCreateDictionaries(payload: DictionaryBatchPayload) {
  return data(await http.post<ApiResponse<DictionaryBatchResult>>(
    '/data/dictionaries/batch',
    payload,
  ))
}

export async function updateDictionary(id: number, payload: DictionaryPayload) {
  return data(await http.put<ApiResponse<DictionaryItem>>(
    `/data/dictionaries/${id}`,
    payload,
  ))
}

export async function deleteDictionary(id: number) {
  await http.delete<ApiResponse<null>>(`/data/dictionaries/${id}`)
}

export async function batchDeleteDictionaries(ids: number[]) {
  return data(await http.post<ApiResponse<DictionaryBatchResult>>(
    '/data/dictionaries/batch-delete',
    { ids },
  ))
}

export async function exportDictionaries(type: DictionaryType) {
  const response = await http.get<Blob>('/data/dictionaries/export', {
    params: { type },
    responseType: 'blob',
  })
  return response.data
}
