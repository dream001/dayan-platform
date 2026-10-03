export type DatasetScope = 'ALL' | 'PROJECT' | 'PERSONAL'

export type DatasetAnnotationStatus = 'UNASSIGNED' | 'ASSIGNED' | 'COMPLETED'

export type DatasetMetadataStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'

export interface DatasetView {
  id: number
  name: string
  dataType: string
  sizeBytes: number
  durationSeconds: number | null
  annotationStatus: DatasetAnnotationStatus
  projectId: number | null
  projectName: string | null
  robotCode: string | null
  collectorId: number | null
  collectorName: string | null
  sourceTaskCode: string | null
  uploaderId: number
  uploaderName: string
  metadataStatus: DatasetMetadataStatus
  openShared: boolean
  tags: string[]
  preview: DatasetPreview | null
  createdAt: string
  updatedAt: string
}

export interface DatasetOption {
  id: number
  name: string
}

export interface DatasetTaskBrief {
  id: number
  name: string
  status: string
  datasetCount: number
  creatorName: string
  createdAt: string
}

export interface DatasetAnnotationSummary {
  total: number
  qualified: number
  invalid: number
  reviewed: number
  coveredDurationSeconds: number
}

export interface DatasetPreview {
  url: string
  expiresAt: string
}

export interface DatasetDetail {
  dataset: DatasetView
  task: DatasetTaskBrief | null
  annotation: DatasetAnnotationSummary
  preview: DatasetPreview | null
}

export interface DatasetStats {
  datasetTotal: number
  datasetTotalDuration: number
  annotationTotal: number
  qualifiedAnnotationTotal: number
  averageAnnotationsPerDataset: number
  annotationTotalDuration: number
  averageAnnotationDurationPerDataset: number
  invalidDatasetTotal: number
  checkedQualifiedRate: number
  invalidCollect: number
  semanticUncorrected: number
  semanticCorrected: number
}

export interface DatasetItemResult {
  id: number
  status: string
  message: string | null
}

export interface DatasetRenameItem {
  id: number
  name: string
}

export interface DatasetQuery {
  page: number
  size: number
  scope: DatasetScope
  projectId?: number
  projectIds?: string
  name?: string
  robotCode?: string
  tag?: string
  uploaderId?: number
  collectorIds?: string
  sourceTaskCode?: string
  minDuration?: number
  maxDuration?: number
  annotationText?: string
  sortDir?: 'ASC' | 'DESC'
}
