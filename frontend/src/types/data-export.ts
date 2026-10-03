export type ExportFormat =
  | 'LEROBOT'
  | 'HDF5'
  | 'MCAP'
  | 'JSON'
  | 'CSV'
  | 'YOLO'
  | 'COCO'
  | 'VOC'
  | 'TIME_ALIGNMENT'
  | 'DROPPED_FRAME'
  | 'MCAP_CHUNK'

export type ExportStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface ExportDatasetOption {
  id: number
  name: string
  dataType: string
  sizeBytes: number
  durationSeconds: number | null
  projectId: number | null
  projectName: string | null
  collectorName: string | null
  createdAt: string
}

export interface ExportTask {
  id: number
  name: string
  format: ExportFormat
  status: ExportStatus
  progress: number
  processedCount: number
  datasetCount: number
  configJson: string
  fileName: string | null
  fileSize: number | null
  errorMessage: string | null
  creatorId: number
  creatorName: string
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}

export interface ExportTaskDetail {
  task: ExportTask
  datasets: ExportDatasetOption[]
}

export interface ExportQuota {
  used: number
  limit: number
  remaining: number
}

export interface ExportTaskPayload {
  name: string
  format: ExportFormat
  datasetIds: number[]
  mediaMode?: 'IMAGE' | 'VIDEO'
  sampleRate?: number
  chunkSize?: number
  version?: 'LATEST' | 'V2_1'
  strictMatch?: boolean
  blurFaces?: boolean
  annotationType?: string
}

export interface ExportTaskQuery {
  page: number
  size: number
  format?: ExportFormat
  status?: ExportStatus
  keyword?: string
}
