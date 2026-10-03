export type ProjectType = 'PERSONAL' | 'TEAM' | 'SHARED'
export type ProjectAccessLevel = 'PUBLIC' | 'PRIVATE' | 'RESTRICTED'
export type ProjectStatus = 'PLANNING' | 'ACTIVE' | 'SUSPENDED' | 'COMPLETED' | 'ARCHIVED'
export type ProjectRole =
  | 'PROJECT_ADMIN'
  | 'PROJECT_MANAGER'
  | 'ANNOTATOR'
  | 'REVIEWER'
  | 'OBSERVER'
export type DataAccessLevel = 'READ_ONLY' | 'READ_WRITE' | 'FULL'
export type PersonnelType =
  | 'SUPER_ADMIN'
  | 'MANAGER'
  | 'COLLECTOR'
  | 'ANNOTATOR'
  | 'AUDITOR'
  | 'GUEST'
export type ReviewMode = 'NONE' | 'SINGLE_REVIEW' | 'DOUBLE_REVIEW'

export interface ProjectSummary {
  id: number
  code: string
  name: string
  description: string | null
  projectType: ProjectType
  accessLevel: ProjectAccessLevel
  status: ProjectStatus
  storageQuotaBytes: number
  startDate: string | null
  endDate: string | null
  memberCount: number
  currentRole: ProjectRole | null
  canEdit: boolean
  canManageMembers: boolean
  canDelete: boolean
  updatedAt: string
}

export interface ProjectDetail {
  summary: ProjectSummary
  storageProvider: string
  annotationGuideline: string | null
  qualityThreshold: number
  reviewMode: ReviewMode
  notificationEnabled: boolean
  ownerId: number
  ownerName: string
  createdAt: string
  metrics: ProjectMetrics
}

export interface ProjectMetrics {
  datasetCount: number
  videoCount: number
  audioCount: number
  mcapCount: number
  storageUsedBytes: number
  annotationTaskCount: number
  collectionTaskCount: number
  completedTaskCount: number
  taskCompletionRate: number
  qualityRate: number
  activeMemberCount: number
}

export interface ProjectPayload {
  code: string
  name: string
  description: string
  projectType: ProjectType
  accessLevel: ProjectAccessLevel
  storageProvider: string
  storageQuotaBytes: number
  startDate: string | null
  endDate: string | null
  annotationGuideline: string
  qualityThreshold: number
  reviewMode: ReviewMode
  notificationEnabled: boolean
}

export interface ProjectMember {
  userId: number
  username: string
  displayName: string
  role: ProjectRole
  dataAccessLevel: DataAccessLevel
  validFrom: string | null
  validUntil: string | null
  active: boolean
}

export interface ProjectMemberPayload {
  userId: number
  role: ProjectRole
  dataAccessLevel: DataAccessLevel
  validFrom: string | null
  validUntil: string | null
}

export interface ProjectUserOption {
  id: number
  username: string
  displayName: string
  personnelType: PersonnelType
}

export interface ProjectOverview {
  total: number
  active: number
  planning: number
  archived: number
}

export interface ProjectQuery {
  page: number
  size: number
  keyword?: string
  status?: ProjectStatus
  projectType?: ProjectType
}
