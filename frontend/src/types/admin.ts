import type { BackendMenu } from './auth'

export interface DashboardStatistics {
  totalUsers: number
  enabledUsers: number
  totalFiles: number
  totalFileSizeBytes: number
  recentOperationCount: number
  recentOperations: OperationLogSummary[]
  generatedAt: string
}

export interface DepartmentNode {
  id: number
  parentId: number | null
  name: string
  code: string
  sortOrder: number
  enabled: boolean
  children: DepartmentNode[]
}

export interface RoleBrief {
  id: number
  name: string
  code: string
  enabled: boolean
}

export interface UserSummary {
  id: number
  departmentId: number | null
  departmentName: string | null
  username: string
  displayName: string
  email: string | null
  phone: string | null
  enabled: boolean
  lastLoginAt: string | null
  createdAt: string
  roles: RoleBrief[]
}

export interface UserQuery {
  page: number
  size: number
  keyword?: string
  departmentId?: number
  enabled?: boolean
  roleCode?: string
  projectId?: number
}

export interface UserCreatePayload {
  departmentId: number | null
  username: string
  password: string
  displayName: string
  email: string
  phone: string
  enabled: boolean
  roleIds: number[]
}

export type UserUpdatePayload = Pick<
  UserCreatePayload,
  'departmentId' | 'displayName' | 'email' | 'phone'
>

export interface BatchUserEntry {
  username: string
  displayName: string
  email: string
  phone: string
}

export interface BatchUserCreatePayload {
  departmentId: number | null
  password: string
  enabled: boolean
  roleIds: number[]
  users: BatchUserEntry[]
}

export interface UserProjectOption {
  id: number
  name: string
}

export interface UserFilterOptions {
  projects: UserProjectOption[]
}

export interface RoleSummary {
  id: number
  name: string
  code: string
  description: string | null
  enabled: boolean
  builtIn: boolean
  userCount: number
  createdAt: string
}

export interface RoleDetail {
  role: RoleSummary
  userIds: number[]
  permissionIds: number[]
}

export interface RolePayload {
  name: string
  code: string
  description: string
  enabled: boolean
}

export interface DepartmentPayload {
  parentId: number | null
  name: string
  code: string
  sortOrder: number
  enabled: boolean
}

export type MenuNode = BackendMenu

export interface MenuPayload {
  parentId: number | null
  type: 'MENU' | 'BUTTON'
  name: string
  code: string
  path: string
  component: string
  icon: string
  sortOrder: number
  visible: boolean
  enabled: boolean
}

export interface MenuOrderPayload {
  parentId: number | null
  ids: number[]
}

export interface StoredFile {
  id: number
  originalName: string
  contentType: string
  sizeBytes: number
  etag: string
  uploaderId: number | null
  status: string
  createdAt: string
  updatedAt: string
}

export interface FilePreview {
  url: string
  expiresAt: string
}

export interface OperationLogSummary {
  id: number
  operatorId: number | null
  operatorName: string | null
  module: string
  action: string
  targetType: string | null
  targetId: string | null
  result: 'SUCCESS' | 'FAILURE'
  errorSummary: string | null
  requestId: string
  occurredAt: string
  durationMs: number | null
}

export interface OperationLogDetail extends OperationLogSummary {
  ipAddress: string | null
  userAgent: string | null
  details: string | null
}

export interface AuditLogQuery {
  page: number
  size: number
  user?: string
  module?: string
  result?: 'SUCCESS' | 'FAILURE'
  startTime?: string
  endTime?: string
}

export type CollectionTaskStatus =
  | 'PENDING'
  | 'WORKING'
  | 'REVIEW_PENDING'
  | 'REJECTED'
  | 'APPROVED'
  | 'SUBMITTED'

export interface CollectionAssignee {
  id: number
  username: string
  displayName: string
}

export interface CollectionStep {
  id?: number
  sequenceNo?: number
  actionName: string
  objectName: string
  targetName: string
  notes: string
}

export interface CollectionDataset {
  id: number
  name: string
  sizeBytes: number
  status: string
  uploadedAt: string
}

export interface CollectionTaskSummary {
  id: number
  name: string
  projectId: number
  projectName: string
  targetCount: number
  averageDurationSeconds: number
  collectedCount: number
  latestFileName: string | null
  latestFileAt: string | null
  assignees: string[]
  actions: string[]
  status: CollectionTaskStatus
  createdBy: number
  createdAt: string
  updatedAt: string
}

export interface CollectionTaskDetail {
  summary: CollectionTaskSummary
  notes: string | null
  initialScene: string | null
  remoteOperationEnabled: boolean
  assignees: CollectionAssignee[]
  steps: CollectionStep[]
  datasets: CollectionDataset[]
  allowedTransitions: CollectionTaskStatus[]
  canEdit: boolean
  canDelete: boolean
  canUnlinkData: boolean
}

export interface CollectionTaskPayload {
  name: string
  projectId: number | null
  assigneeIds: number[]
  targetCount: number
  averageDurationSeconds: number
  notes: string
  initialScene: string
  remoteOperationEnabled: boolean
  steps: CollectionStep[]
}

export interface CollectionProjectOption {
  id: number
  name: string
}

export interface CollectionOptions {
  projects: CollectionProjectOption[]
  collectors: CollectionAssignee[]
}

export interface CollectionStatusCounts {
  total: number
  statuses: Record<CollectionTaskStatus, number>
}

export interface CollectionTaskQuery {
  page: number
  size: number
  keyword?: string
  collectorId?: number
  status?: CollectionTaskStatus
}

export type DataUploadType =
  | 'MCAP'
  | 'BAG'
  | 'VIDEO'
  | 'AUDIO'
  | 'IMAGE'
  | 'HDF5'
  | 'LEROBOT'
  | 'MEITUAN'
  | 'LUMOS'
  | 'ZC0TOUCH'
  | 'SENSEXPERIENCE'
  | 'BVH'

export interface DataUploadProjectOption {
  id: number
  code: string
  name: string
}

export interface DataUploadStorageOption {
  key: string
  name: string
  provider: string
  bucket: string
}

export interface DataUploadOptions {
  projects: DataUploadProjectOption[]
  storages: DataUploadStorageOption[]
  multipartThreshold: number
  chunkSize: number
  videoTimeoutSeconds: number
}

export interface UploadedDataset {
  id: number
  projectId: number
  name: string
  dataType: DataUploadType
  originalName: string
  contentType: string
  sizeBytes: number
  status: 'READY' | 'PROCESSING' | 'FAILED'
  createdAt: string
}

export interface DataUploadSession {
  id: string | null
  projectId: number
  dataType: DataUploadType
  fileName: string
  totalSize: number
  chunkSize: number
  totalChunks: number
  uploadedParts: number[]
  status: string
  existingDataset: UploadedDataset | null
}

export interface CreateDataUploadSessionPayload {
  projectId: number
  storageKey: string
  dataType: DataUploadType
  fileName: string
  contentType: string
  totalSize: number
  sourceFingerprint: string
  robotType?: string
}
