import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  AccessLog,
  AccessLogQuery,
  ExportMonitorQuery,
  LoginLog,
  LoginLogQuery,
  MetricPoint,
  MonitoredExportTask,
  MonitoringOverview,
  MonitorRange,
  OnlineUsers,
  QueueSummary,
  SystemInformation,
} from '@/types/monitoring'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getMonitoringOverview(range: MonitorRange) {
  return data(await http.get<ApiResponse<MonitoringOverview>>('/monitor/overview', {
    params: { range },
  }))
}

export async function collectMonitoringMetrics() {
  return data(await http.post<ApiResponse<MetricPoint>>('/monitor/collect'))
}

export async function getSystemInformation() {
  return data(await http.get<ApiResponse<SystemInformation>>('/monitor/system'))
}

export async function getAccessLogs(params: AccessLogQuery) {
  return data(await http.get<ApiResponse<PageResponse<AccessLog>>>('/monitor/access-logs', {
    params,
  }))
}

export async function getOnlineUsers() {
  return data(await http.get<ApiResponse<OnlineUsers>>('/monitor/online-users'))
}

export async function getLoginLogs(params: LoginLogQuery) {
  return data(await http.get<ApiResponse<PageResponse<LoginLog>>>('/monitor/login-logs', {
    params,
  }))
}

export async function getMonitoredExports(params: ExportMonitorQuery) {
  return data(await http.get<ApiResponse<PageResponse<MonitoredExportTask>>>('/monitor/exports', {
    params,
  }))
}

export async function getQueueSummary() {
  return data(await http.get<ApiResponse<QueueSummary>>('/monitor/queue'))
}

export async function setQueuePaused(paused: boolean) {
  return data(await http.post<ApiResponse<QueueSummary>>('/monitor/queue/pause', undefined, {
    params: { paused },
  }))
}

export async function retryQueueTask(id: number) {
  await http.post<ApiResponse<{ affected: number }>>(`/monitor/queue/tasks/${id}/retry`)
}

export async function cancelQueueTask(id: number) {
  await http.post<ApiResponse<{ affected: number }>>(`/monitor/queue/tasks/${id}/cancel`)
}

export async function deleteQueueTask(id: number) {
  await http.delete<ApiResponse<{ affected: number }>>(`/monitor/queue/tasks/${id}`)
}

export async function retryAllFailedTasks() {
  return data(await http.post<ApiResponse<{ affected: number }>>('/monitor/queue/retry-failed'))
}

export async function clearPendingTasks() {
  return data(await http.delete<ApiResponse<{ affected: number }>>('/monitor/queue/pending'))
}

export async function cleanQueueHistory() {
  return data(await http.delete<ApiResponse<{ affected: number }>>('/monitor/queue/history'))
}
