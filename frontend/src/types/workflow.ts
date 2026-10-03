export type WorkflowScope = 'GLOBAL' | 'PROJECT'
export type WorkflowLogic = 'AND' | 'OR'
export type WorkflowRunStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export interface MatchCondition {
  field: string
  operator: string
  value: string
  flags: string
}

export interface MatchRulePayload {
  name: string
  description: string
  scope: WorkflowScope
  projectId: number | null
  priority: number
  enabled: boolean
  logicOperator: WorkflowLogic
  conditions: MatchCondition[]
}

export interface MatchRule extends MatchRulePayload {
  id: number
  projectName: string | null
  canEdit: boolean
  updatedAt: string
}

export interface ActionStep {
  action: string
  params: Record<string, unknown>
}

export interface ActionRulePayload {
  name: string
  description: string
  scope: WorkflowScope
  projectId: number | null
  enabled: boolean
  steps: ActionStep[]
}

export interface ActionRule extends ActionRulePayload {
  id: number
  projectName: string | null
  canEdit: boolean
  updatedAt: string
}

export interface WorkflowPayload {
  name: string
  description: string
  scope: WorkflowScope
  projectId: number | null
  priority: number
  enabled: boolean
  matchRuleId: number
  actionRuleId: number
}

export interface WorkflowDefinition extends WorkflowPayload {
  id: number
  projectName: string | null
  matchRuleName: string
  actionRuleName: string
  stepCount: number
  canEdit: boolean
  updatedAt: string
}

export interface WorkflowRun {
  id: number
  workflowId: number
  workflowName: string
  datasetId: number
  datasetName: string
  projectId: number | null
  projectName: string | null
  stage: string
  status: WorkflowRunStatus
  progress: number
  triggerType: string
  steps: ActionStep[]
  errorMessage: string | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}

export interface WorkflowProjectOption {
  id: number
  name: string
  manageable: boolean
}

export interface WorkflowDatasetOption {
  id: number
  name: string
  dataType: string
  projectId: number | null
  projectName: string | null
}

export interface WorkflowOverview {
  workflowCount: number
  enabledWorkflowCount: number
  matchRuleCount: number
  actionRuleCount: number
  queuedRunCount: number
  failedRunCount: number
  workflows: WorkflowDefinition[]
}

export interface MatchTestResult {
  scanned: number
  matched: number
  samples: Array<{
    id: number
    name: string
    dataType: string
    projectName: string | null
  }>
}
