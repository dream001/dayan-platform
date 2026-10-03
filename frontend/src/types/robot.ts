export type RobotType =
  | 'HUMANOID'
  | 'MOBILE_MANIPULATOR'
  | 'DESKTOP_ARM'
  | 'MOBILE_BASE'
  | 'QUADRUPED'
  | 'INDUSTRIAL_ARM'
  | 'OTHER'

export type ActionMappingSupport = 'SUPPORTED' | 'COMING_SOON' | 'UNSUPPORTED'

export interface Robot {
  id: number
  name: string
  iconUrl: string
  iconFileId: number | null
  titleZh: string | null
  titleEn: string | null
  robotType: RobotType
  actionMappingSupport: ActionMappingSupport
  description: string | null
  company: string | null
  introductionUrl: string | null
  builtIn: boolean
  datasetCount: number
  createdAt: string
  updatedAt: string
}

export interface RobotPayload {
  name: string
  iconUrl: string
  iconFileId: number | null
  titleZh: string
  titleEn: string
  robotType: RobotType
  actionMappingSupport: ActionMappingSupport
  description: string
  company: string
  introductionUrl: string
}

export interface RobotDataset {
  id: number
  name: string
  dataType: string
  sizeBytes: number
  durationSeconds: number | null
  uploaderName: string
  uploadedAt: string
  annotationCount: number
}
