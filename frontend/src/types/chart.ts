export type ChartType = 'relationships' | 'planning' | 'durations' | 'dependencies' | 'calendar'

export interface ChartProject {
  id: number
  code: string
  name: string
}

export interface HierarchyNode {
  name: string
  value: number
  children: HierarchyNode[]
}

export interface GraphLink {
  source: string
  target: string
  value: number
}

export interface GraphData {
  nodes: string[]
  links: GraphLink[]
}

export interface DurationPoint {
  action: string
  averageSeconds: number
  markerCount: number
}

export interface CalendarPoint {
  date: string
  value: number
  deviation: number
}

export interface CalendarData {
  startDate: string | null
  endDate: string | null
  dailyAverage: number
  points: CalendarPoint[]
}

export type ChartData = HierarchyNode[] | GraphData | DurationPoint[] | CalendarData
