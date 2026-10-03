import type { ApiResponse } from '@/types/api'
import type {
  BatchResult,
  Device,
  DeviceDetail,
  DeviceOptions,
  DevicePayload,
  DeviceRegistration,
  InstallOptions,
} from '@/types/device'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getDevices(params?: { keyword?: string; projectId?: number }) {
  return data(await http.get<ApiResponse<Device[]>>('/basic/devices', { params }))
}

export async function getDevice(agentId: string, hours = 4) {
  return data(await http.get<ApiResponse<DeviceDetail>>(
    `/basic/devices/${agentId}`,
    { params: { hours } },
  ))
}

export async function getDeviceOptions() {
  return data(await http.get<ApiResponse<DeviceOptions>>('/basic/devices/options'))
}

export async function createDevice(payload: DevicePayload) {
  return data(await http.post<ApiResponse<DeviceRegistration>>('/basic/devices', payload))
}

export async function updateDevice(id: number, payload: DevicePayload) {
  return data(await http.put<ApiResponse<Device>>(`/basic/devices/${id}`, payload))
}

export async function deleteDevices(deviceIds: number[]) {
  return data(await http.delete<ApiResponse<BatchResult>>('/basic/devices', {
    data: { deviceIds },
  }))
}

export async function assignDeviceTask(deviceIds: number[], collectionTaskId: number) {
  return data(await http.patch<ApiResponse<BatchResult>>('/basic/devices/collection-task', {
    deviceIds,
    collectionTaskId,
  }))
}

export async function getInstallCommand(id: number, payload: InstallOptions) {
  return data(await http.post<ApiResponse<{ command: string }>>(
    `/basic/devices/${id}/install-command`,
    payload,
  ))
}
