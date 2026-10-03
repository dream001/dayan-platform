import type { ApiResponse } from '@/types/api'
import type {
  SkillAssetPayload,
  SkillLibrary,
  SkillMediaType,
  SkillProject,
  SkillSamplePage,
} from '@/types/skill-library'
import { http } from './http'

function data<T>(response: { data: ApiResponse<T> }) {
  return response.data.data
}

export async function getSkillProjects() {
  return data(await http.get<ApiResponse<SkillProject[]>>('/data/skills/projects'))
}

export async function getSkillLibrary(projectId: number | undefined, locale: string) {
  return data(await http.get<ApiResponse<SkillLibrary>>('/data/skills', {
    params: { projectId, locale },
  }))
}

export async function getSkillSamples(
  skillKey: string,
  params: {
    projectId?: number
    mediaType: SkillMediaType
    page: number
    size?: number
  },
) {
  return data(await http.get<ApiResponse<SkillSamplePage>>(
    `/data/skills/${encodeURIComponent(skillKey)}/samples`,
    { params: { ...params, size: params.size ?? 30 } },
  ))
}

export async function saveSkillAsset(skillKey: string, payload: SkillAssetPayload) {
  await http.put<ApiResponse<null>>(
    `/data/skills/catalog/${encodeURIComponent(skillKey)}`,
    payload,
  )
}
