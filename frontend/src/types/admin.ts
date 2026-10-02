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
