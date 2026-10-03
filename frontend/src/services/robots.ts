import type { ApiResponse } from '@/types/api'
import type { Robot, RobotDataset, RobotPayload, RobotType } from '@/types/robot'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getRobots(robotType?: RobotType) {
  return data(await http.get<ApiResponse<Robot[]>>('/basic/robots', {
    params: robotType ? { robotType } : undefined,
  }))
}

export async function createRobot(payload: RobotPayload) {
  return data(await http.post<ApiResponse<Robot>>('/basic/robots', payload))
}

export async function updateRobot(id: number, payload: RobotPayload) {
  return data(await http.put<ApiResponse<Robot>>(`/basic/robots/${id}`, payload))
}

export async function deleteRobot(id: number) {
  await http.delete<ApiResponse<null>>(`/basic/robots/${id}`)
}

export async function getRobotDatasets(id: number) {
  return data(await http.get<ApiResponse<RobotDataset[]>>(`/basic/robots/${id}/datasets`))
}
