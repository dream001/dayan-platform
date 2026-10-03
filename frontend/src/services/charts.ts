import type { ApiResponse } from '@/types/api'
import type {
  CalendarData,
  ChartData,
  ChartProject,
  ChartType,
  DurationPoint,
  GraphData,
  HierarchyNode,
} from '@/types/chart'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getChartProjects() {
  return data(await http.get<ApiResponse<ChartProject[]>>('/data/charts/projects'))
}

export async function getChartData(
  type: ChartType,
  projectId: number,
  locale: string,
): Promise<ChartData> {
  const response = await http.get<ApiResponse<ChartData>>(`/data/charts/${type}`, {
    params: {
      projectId,
      ...(['relationships', 'planning'].includes(type) ? { locale } : {}),
    },
  })
  return data(response)
}

export type {
  CalendarData,
  DurationPoint,
  GraphData,
  HierarchyNode,
}
