export type AnnotationTaskStatus =
  | 'PENDING'
  | 'WORKING'
  | 'REVIEW_PENDING'
  | 'REJECTED'
  | 'APPROVED'
  | 'SUBMITTED'

export interface AnnotationTaskSummary {
  id: number
  name: string
  projectId: number
  projectName: string
  annotatorId: number
  annotatorName: string
  reviewerId: number | null
  reviewerName: string | null
  creatorId: number
  creatorName: string
  status: AnnotationTaskStatus
  rejectionReason: string | null
  datasetCount: number
  reviewedCount: number
  progress: number
  createdAt: string
  updatedAt: string
}

export interface AnnotationDataset {
  relationId: number
  datasetId: number
  name: string
  dataType: string
  sizeBytes: number
  annotationDescription: string | null
  checkResult: 'VALID' | 'INVALID' | null
  rejectionReason: string | null
  createdAt: string
}

export interface AnnotationTaskDetail {
  summary: AnnotationTaskSummary
  datasets: AnnotationDataset[]
  allowedTransitions: AnnotationTaskStatus[]
  canEdit: boolean
  canDelete: boolean
  canExecute: boolean
  canReview: boolean
}

export interface AnnotationPersonOption {
  id: number
  username: string
  displayName: string
}

export interface AnnotationProjectOption {
  id: number
  name: string
}

export interface AnnotationDatasetOption {
  id: number
  projectId: number
  name: string
  dataType: string
  sizeBytes: number
  createdAt: string
}

export interface AnnotationTaskOptions {
  projects: AnnotationProjectOption[]
  annotators: AnnotationPersonOption[]
  reviewers: AnnotationPersonOption[]
  datasets: AnnotationDatasetOption[]
}

export interface AnnotationTaskCounts {
  total: number
  statuses: Record<AnnotationTaskStatus, number>
}

export interface AnnotationTaskQuery {
  page: number
  size: number
  keyword?: string
  status?: AnnotationTaskStatus
  projectId?: number
  annotatorId?: number
  reviewerId?: number
  createdDate?: string
}

export interface AnnotationTaskCreatePayload {
  name: string
  projectId: number
  annotatorIds: number[]
  reviewerIds: number[]
  datasetIds: number[]
  randomOrder: boolean
}

export interface AnnotationTaskUpdatePayload {
  name: string
  projectId: number
  annotatorId: number
  reviewerId: number | null
}
