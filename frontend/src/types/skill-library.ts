export type SkillMediaType = 'COLOR' | 'DEPTH'
export type SkillCategory = 'BASIC' | 'COMPOSITE' | 'PROFESSIONAL'
export type SkillDifficulty = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT'
export type SkillStatus = 'DRAFT' | 'PUBLISHED' | 'DEPRECATED'

export interface SkillProject {
  id: number
  code: string
  name: string
}

export interface SkillSample {
  annotationId: number
  datasetId: number
  description: string
  projectName: string
  dataType: string
  contentType: string
  mediaType: SkillMediaType
  previewUrl: string | null
  previewExpiresAt: string | null
  createdAt: string
}

export interface SkillSummary {
  key: string
  name: string
  description: string | null
  category: SkillCategory
  difficulty: SkillDifficulty
  status: SkillStatus
  currentVersion: string
  usageScene: string | null
  tags: string[]
  annotationCount: number
  projectCount: number
  recentUsageCount: number
  versionCount: number
  dependencyCount: number
  qualityRate: number
  samples: SkillSample[]
}

export interface SkillLibrary {
  skillCount: number
  annotationCount: number
  skills: SkillSummary[]
}

export interface SkillAssetPayload {
  nameZh: string
  nameEn: string
  description: string
  category: SkillCategory
  difficulty: SkillDifficulty
  status: SkillStatus
  currentVersion: string
  usageScene: string
  tags: string[]
  template: boolean
}

export interface SkillSamplePage {
  page: number
  size: number
  total: number
  totalPages: number
  items: SkillSample[]
}
