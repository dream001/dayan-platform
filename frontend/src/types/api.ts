export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  requestId: string
  timestamp: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}
