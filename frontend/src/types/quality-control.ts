export type QualityRuleScope = 'GLOBAL' | 'PROJECT'
export type QualityDataType =
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
export type QualityAssertionType = 'NUMERIC' | 'REQUIRED_TOPIC' | 'FORBIDDEN_TOPIC'
export type QualitySeverity = 'ERROR' | 'WARNING'
export type QualityMetricScope = 'ALL' | 'TOPIC' | 'SCHEMA'
export type QualityExecutionStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'PASSED'
  | 'FAILED'
  | 'ERROR'
  | 'CANCELLED'

export interface QualityAssertion {
  type: QualityAssertionType
  metric: string
  operator: string
  threshold: number | null
  severity: QualitySeverity
  metricScope: QualityMetricScope
  matchPattern: string
}

export interface QualityRulePayload {
  name: string
  description: string
  scope: QualityRuleScope
  projectId: number | null
  dataType: QualityDataType
  datasetPattern: string
  enabled: boolean
  priority: number
  assertions: QualityAssertion[]
}

export interface QualityRule extends QualityRulePayload {
  id: number
  projectName: string | null
  algorithmCode: string
  creatorId: number
  creatorName: string
  canEdit: boolean
  canDelete: boolean
  createdAt: string
  updatedAt: string
}

export interface QualityAssertionResult {
  assertion: QualityAssertion
  passed: boolean
  actualValue: string | null
  message: string
}

export interface QualityExecution {
  id: number
  ruleId: number
  ruleName: string
  ruleScope: QualityRuleScope
  datasetId: number
  datasetName: string
  projectId: number | null
  projectName: string | null
  dataType: string
  status: QualityExecutionStatus
  progress: number
  passed: boolean | null
  effectivePass: boolean | null
  results: QualityAssertionResult[]
  errorMessage: string | null
  triggerType: 'AUTO' | 'MANUAL' | 'BACKFILL'
  overridePass: boolean | null
  overrideReason: string | null
  overrideByName: string | null
  overrideAt: string | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}

export interface QualityOverview {
  totalRules: number
  enabledRules: number
  queued: number
  passed: number
  failed: number
  overridden: number
  canManageGlobal: boolean
}

export interface QualityProjectOption {
  id: number
  name: string
}

export interface QualityDatasetOption {
  id: number
  name: string
  projectName: string | null
  dataType: QualityDataType
}

export interface QualityRunResult {
  queued: number
  skipped: number
}
