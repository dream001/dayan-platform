import type { ApiResponse } from '@/types/api'
import type {
  MqttConnection,
  MqttConnectionPayload,
  MqttOverview,
  MqttSubscription,
  MqttSubscriptionPayload,
} from '@/types/mqtt'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getMqttOverview() {
  return data(await http.get<ApiResponse<MqttOverview>>('/basic/mqtt/overview'))
}

export async function getMqttConnections() {
  return data(await http.get<ApiResponse<MqttConnection[]>>('/basic/mqtt/connections'))
}

export async function saveMqttConnection(id: number | null, payload: MqttConnectionPayload) {
  const response = id == null
    ? await http.post<ApiResponse<MqttConnection>>('/basic/mqtt/connections', payload)
    : await http.put<ApiResponse<MqttConnection>>(`/basic/mqtt/connections/${id}`, payload)
  return data(response)
}

export async function changeMqttConnectionStatus(id: number, enabled: boolean) {
  return data(await http.patch<ApiResponse<MqttConnection>>(
    `/basic/mqtt/connections/${id}/status`,
    { enabled },
  ))
}

export async function testMqttConnection(id: number) {
  return data(await http.post<ApiResponse<MqttConnection>>(`/basic/mqtt/connections/${id}/test`))
}

export async function deleteMqttConnection(id: number) {
  await http.delete<ApiResponse<null>>(`/basic/mqtt/connections/${id}`)
}

export async function getMqttSubscriptions(connectionId?: number) {
  return data(await http.get<ApiResponse<MqttSubscription[]>>(
    '/basic/mqtt/subscriptions',
    { params: { connectionId } },
  ))
}

export async function saveMqttSubscription(id: number | null, payload: MqttSubscriptionPayload) {
  const response = id == null
    ? await http.post<ApiResponse<MqttSubscription>>('/basic/mqtt/subscriptions', payload)
    : await http.put<ApiResponse<MqttSubscription>>(`/basic/mqtt/subscriptions/${id}`, payload)
  return data(response)
}

export async function changeMqttSubscriptionStatus(id: number, enabled: boolean) {
  return data(await http.patch<ApiResponse<MqttSubscription>>(
    `/basic/mqtt/subscriptions/${id}/status`,
    { enabled },
  ))
}

export async function deleteMqttSubscription(id: number) {
  await http.delete<ApiResponse<null>>(`/basic/mqtt/subscriptions/${id}`)
}
