export type DictionaryType =
  | 'SKILL'
  | 'OBJECT'
  | 'TARGET'
  | 'ADVERBIAL'
  | 'INVALID'
  | 'TAG'
  | 'TAG_CATEGORY'

export type DictionaryScope = 'GLOBAL' | 'SHARED' | 'PROJECT'

export type DictionarySort =
  | 'USE_COUNT'
  | 'ENGLISH'
  | 'CHINESE'
  | 'JAPANESE'
  | 'CREATED_AT'

export type SortDirection = 'ASC' | 'DESC'

export interface DictionaryItem {
  id: number
  dictionaryType: DictionaryType
  scope: DictionaryScope
  projectId: number | null
  projectName: string | null
  englishText: string
  chineseText: string
  japaneseText: string | null
  useCount: number
  creatorId: number
  creatorName: string
  canEdit: boolean
  canDelete: boolean
  createdAt: string
  updatedAt: string
}

export interface DictionaryPayload {
  dictionaryType: DictionaryType
  scope: DictionaryScope
  projectId: number | null
  englishText: string
  chineseText: string
  japaneseText: string
}

export interface DictionaryBatchPayload {
  dictionaryType: DictionaryType
  scope: DictionaryScope
  projectId: number | null
  content: string
}

export interface DictionaryQuery {
  page: number
  size: number
  type: DictionaryType
  keyword?: string
  scope?: DictionaryScope
  projectId?: number
  sort?: DictionarySort
  direction?: SortDirection
}

export interface DictionaryOverview {
  counts: Partial<Record<DictionaryType, number>>
  canManageGlobal: boolean
}

export interface DictionaryProjectOption {
  id: number
  name: string
}

export interface DictionaryBatchResult {
  created: number
  deleted: number
  skipped: number
}
