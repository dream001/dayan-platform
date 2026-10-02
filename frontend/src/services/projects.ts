import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  ProjectDetail,
  ProjectMember,
  ProjectMemberPayload,
  ProjectOverview,
  ProjectPayload,
  ProjectQuery,
  ProjectStatus,
  ProjectSummary,
  ProjectUserOption,
} from '@/types/project'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getProjects(params: ProjectQuery) {
  return data(await http.get<ApiResponse<PageResponse<ProjectSummary>>>('/basic/projects', { params }))
}

export async function getProjectOverview() {
  return data(await http.get<ApiResponse<ProjectOverview>>('/basic/projects/overview'))
}

export async function getProject(id: number) {
  return data(await http.get<ApiResponse<ProjectDetail>>(`/basic/projects/${id}`))
}

export async function createProject(payload: ProjectPayload) {
  return data(await http.post<ApiResponse<ProjectDetail>>('/basic/projects', payload))
}

export async function updateProject(id: number, payload: ProjectPayload) {
  return data(await http.put<ApiResponse<ProjectDetail>>(`/basic/projects/${id}`, payload))
}

export async function changeProjectStatus(id: number, status: ProjectStatus) {
  return data(await http.patch<ApiResponse<ProjectDetail>>(`/basic/projects/${id}/status`, { status }))
}

export async function deleteProject(id: number) {
  await http.delete<ApiResponse<null>>(`/basic/projects/${id}`)
}

export async function getProjectMembers(id: number) {
  return data(await http.get<ApiResponse<ProjectMember[]>>(`/basic/projects/${id}/members`))
}

export async function saveProjectMember(id: number, payload: ProjectMemberPayload) {
  return data(await http.put<ApiResponse<ProjectMember>>(`/basic/projects/${id}/members`, payload))
}

export async function removeProjectMember(id: number, userId: number) {
  await http.delete<ApiResponse<null>>(`/basic/projects/${id}/members/${userId}`)
}

export async function getProjectUserOptions(id: number, keyword?: string) {
  return data(await http.get<ApiResponse<ProjectUserOption[]>>(
    `/basic/projects/${id}/user-options`,
    { params: { keyword } },
  ))
}
