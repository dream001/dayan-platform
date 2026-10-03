import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  AuditLogQuery,
  CollectionOptions,
  CollectionStatusCounts,
  CollectionTaskDetail,
  CollectionTaskPayload,
  CollectionTaskQuery,
  CreateDataUploadSessionPayload,
  DashboardStatistics,
  DataUploadOptions,
  DataUploadSession,
  DataUploadType,
  DepartmentNode,
  DepartmentPayload,
  FilePreview,
  MenuNode,
  MenuOrderPayload,
  MenuPayload,
  OperationLogDetail,
  OperationLogSummary,
  RoleDetail,
  RolePayload,
  RoleSummary,
  StoredFile,
  BatchUserCreatePayload,
  UploadedDataset,
  UserCreatePayload,
  UserFilterOptions,
  UserQuery,
  UserSummary,
  UserUpdatePayload,
} from '@/types/admin'
import type { AuthUser } from '@/types/auth'
import { http } from './http'

interface RoleQuery {
  page: number
  size: number
  keyword?: string
  enabled?: boolean
}

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getDashboardStatistics() {
  return data(await http.get<ApiResponse<DashboardStatistics>>('/dashboard/statistics'))
}

export async function updateProfile(payload: Pick<AuthUser, 'displayName' | 'email' | 'phone'>) {
  return data(await http.patch<ApiResponse<AuthUser>>('/auth/me/profile', payload))
}

export async function changePassword(payload: { currentPassword: string; newPassword: string }) {
  await http.put<ApiResponse<null>>('/auth/me/password', payload)
}

export async function getUsers(params: UserQuery) {
  return data(await http.get<ApiResponse<PageResponse<UserSummary>>>('/system/users', { params }))
}

export async function getUserFilterOptions() {
  return data(await http.get<ApiResponse<UserFilterOptions>>('/system/users/filter-options'))
}

export async function getUser(id: number) {
  return data(await http.get<ApiResponse<UserSummary>>(`/system/users/${id}`))
}

export async function createUser(payload: UserCreatePayload) {
  return data(await http.post<ApiResponse<UserSummary>>('/system/users', payload))
}

export async function createUsersBatch(payload: BatchUserCreatePayload) {
  return data(await http.post<ApiResponse<UserSummary[]>>('/system/users/batch', payload))
}

export async function updateUser(id: number, payload: UserUpdatePayload) {
  return data(await http.put<ApiResponse<UserSummary>>(`/system/users/${id}`, payload))
}

export async function changeUserStatus(id: number, enabled: boolean) {
  await http.patch<ApiResponse<null>>(`/system/users/${id}/status`, { enabled })
}

export async function resetUserPassword(id: number, newPassword: string) {
  await http.put<ApiResponse<null>>(`/system/users/${id}/password`, { newPassword })
}

export async function assignUserRoles(id: number, ids: number[]) {
  await http.put<ApiResponse<null>>(`/system/users/${id}/roles`, { ids })
}

export async function deleteUser(id: number) {
  await http.delete<ApiResponse<null>>(`/system/users/${id}`)
}

export async function getRoles(params: RoleQuery) {
  return data(await http.get<ApiResponse<PageResponse<RoleSummary>>>('/system/roles', { params }))
}

export async function getRole(id: number) {
  return data(await http.get<ApiResponse<RoleDetail>>(`/system/roles/${id}`))
}

export async function createRole(payload: RolePayload) {
  return data(await http.post<ApiResponse<RoleDetail>>('/system/roles', payload))
}

export async function updateRole(id: number, payload: RolePayload) {
  return data(await http.put<ApiResponse<RoleDetail>>(`/system/roles/${id}`, payload))
}

export async function deleteRole(id: number) {
  await http.delete<ApiResponse<null>>(`/system/roles/${id}`)
}

export async function grantRolePermissions(id: number, ids: number[]) {
  await http.put<ApiResponse<null>>(`/system/roles/${id}/permissions`, { ids })
}

export async function getDepartments() {
  return data(await http.get<ApiResponse<DepartmentNode[]>>('/system/departments/tree'))
}

export async function createDepartment(payload: DepartmentPayload) {
  return data(await http.post<ApiResponse<DepartmentNode>>('/system/departments', payload))
}

export async function updateDepartment(id: number, payload: DepartmentPayload) {
  return data(await http.put<ApiResponse<DepartmentNode>>(`/system/departments/${id}`, payload))
}

export async function deleteDepartment(id: number) {
  await http.delete<ApiResponse<null>>(`/system/departments/${id}`)
}

export async function getMenus() {
  return data(await http.get<ApiResponse<MenuNode[]>>('/system/menus/tree'))
}

export async function getMenu(id: number) {
  return data(await http.get<ApiResponse<MenuNode>>(`/system/menus/${id}`))
}

export async function createMenu(payload: MenuPayload) {
  return data(await http.post<ApiResponse<MenuNode>>('/system/menus', payload))
}

export async function updateMenu(id: number, payload: MenuPayload) {
  return data(await http.put<ApiResponse<MenuNode>>(`/system/menus/${id}`, payload))
}

export async function reorderMenus(payload: MenuOrderPayload) {
  return data(await http.put<ApiResponse<MenuNode[]>>('/system/menus/order', payload))
}

export async function deleteMenu(id: number) {
  await http.delete<ApiResponse<null>>(`/system/menus/${id}`)
}

export async function getFiles(params: { page: number; size: number; keyword?: string }) {
  return data(await http.get<ApiResponse<PageResponse<StoredFile>>>('/files', { params }))
}

export async function uploadFile(file: File, onProgress?: (percentage: number) => void) {
  const form = new FormData()
  form.append('file', file)
  return data(await http.post<ApiResponse<StoredFile>>('/files', form, {
    onUploadProgress: (event) => {
      if (event.total) onProgress?.(Math.round((event.loaded / event.total) * 100))
    },
  }))
}

export async function getFilePreview(id: number) {
  return data(await http.get<ApiResponse<FilePreview>>(`/files/${id}/preview`))
}

export async function downloadFile(id: number) {
  const response = await http.get<Blob>(`/files/${id}/download`, { responseType: 'blob' })
  return response.data
}

export async function deleteFile(id: number) {
  await http.delete<ApiResponse<null>>(`/files/${id}`)
}

export async function getAuditLogs(params: AuditLogQuery) {
  return data(await http.get<ApiResponse<PageResponse<OperationLogSummary>>>('/audit/logs', { params }))
}

export async function getAuditLog(id: number) {
  return data(await http.get<ApiResponse<OperationLogDetail>>(`/audit/logs/${id}`))
}

export async function getCollectionTasks(params: CollectionTaskQuery) {
  return data(await http.get<ApiResponse<PageResponse<CollectionTaskDetail['summary']>>>(
    '/collections',
    { params },
  ))
}

export async function getCollectionStatusCounts() {
  return data(await http.get<ApiResponse<CollectionStatusCounts>>('/collections/status-counts'))
}

export async function getCollectionTask(id: number) {
  return data(await http.get<ApiResponse<CollectionTaskDetail>>(`/collections/${id}`))
}

export async function getCollectionOptions(projectId?: number) {
  return data(await http.get<ApiResponse<CollectionOptions>>('/collections/options', {
    params: { projectId },
  }))
}

export async function createCollectionTask(payload: CollectionTaskPayload) {
  return data(await http.post<ApiResponse<CollectionTaskDetail>>('/collections', payload))
}

export async function updateCollectionTask(id: number, payload: CollectionTaskPayload) {
  return data(await http.put<ApiResponse<CollectionTaskDetail>>(`/collections/${id}`, payload))
}

export async function changeCollectionTaskStatus(id: number, status: string) {
  return data(await http.patch<ApiResponse<CollectionTaskDetail>>(
    `/collections/${id}/status`,
    { status },
  ))
}

export async function deleteCollectionTask(id: number) {
  await http.delete<ApiResponse<null>>(`/collections/${id}`)
}

export async function unlinkCollectionDataset(taskId: number, fileId: number) {
  await http.delete<ApiResponse<null>>(`/collections/${taskId}/datasets/${fileId}`)
}

export async function getDataUploadOptions() {
  return data(await http.get<ApiResponse<DataUploadOptions>>('/data/uploads/options'))
}

export async function uploadDataDirect(
  payload: {
    projectId: number
    storageKey: string
    dataType: DataUploadType
    sourceFingerprint: string
    robotType?: string
  },
  file: File,
  onProgress: (loaded: number) => void,
  signal: AbortSignal,
) {
  const form = new FormData()
  form.append('projectId', String(payload.projectId))
  form.append('storageKey', payload.storageKey)
  form.append('dataType', payload.dataType)
  form.append('sourceFingerprint', payload.sourceFingerprint)
  if (payload.robotType) form.append('robotType', payload.robotType)
  form.append('file', file)
  return data(await http.post<ApiResponse<UploadedDataset>>('/data/uploads/direct', form, {
    signal,
    onUploadProgress: (event) => onProgress(event.loaded),
  }))
}

export async function createDataUploadSession(payload: CreateDataUploadSessionPayload) {
  return data(await http.post<ApiResponse<DataUploadSession>>('/data/uploads/sessions', payload))
}

export async function uploadDataPart(
  sessionId: string,
  partNumber: number,
  chunk: Blob,
  onProgress: (loaded: number) => void,
  signal: AbortSignal,
) {
  const form = new FormData()
  form.append('chunk', chunk, `part-${partNumber}`)
  await http.put<ApiResponse<null>>(
    `/data/uploads/sessions/${sessionId}/parts/${partNumber}`,
    form,
    {
      signal,
      onUploadProgress: (event) => onProgress(event.loaded),
    },
  )
}

export async function pauseDataUpload(sessionId: string) {
  await http.post<ApiResponse<null>>(`/data/uploads/sessions/${sessionId}/pause`)
}

export async function resumeDataUpload(sessionId: string) {
  return data(await http.post<ApiResponse<DataUploadSession>>(
    `/data/uploads/sessions/${sessionId}/resume`,
  ))
}

export async function completeDataUpload(sessionId: string) {
  return data(await http.post<ApiResponse<UploadedDataset>>(
    `/data/uploads/sessions/${sessionId}/complete`,
  ))
}

export async function cancelDataUpload(sessionId: string) {
  await http.delete<ApiResponse<null>>(`/data/uploads/sessions/${sessionId}`)
}
