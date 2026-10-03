import type { EChartsOption } from 'echarts'
import type {
  CalendarData,
  DurationPoint,
  GraphData,
  HierarchyNode,
} from '@/types/chart'

export type ChartTranslate = (
  key: string,
  params?: Record<string, string | number>,
) => string

const CHART_COLORS = ['#6c4ba7', '#16869a', '#d09a37', '#c75a56', '#56708c']

export function relationshipOption(
  nodes: HierarchyNode[],
  t: ChartTranslate,
): EChartsOption {
  return {
    color: CHART_COLORS,
    tooltip: {
      trigger: 'item',
      valueFormatter: (value) => t('charts.markers', { count: String(value) }),
    },
    series: [{
      type: 'sunburst',
      data: nodes,
      radius: ['12%', '88%'],
      sort: undefined,
      emphasis: { focus: 'ancestor' },
      label: { color: '#17232b', fontSize: 11, rotate: 'radial' },
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      levels: [
        {},
        { r0: '12%', r: '38%', label: { rotate: 0, fontWeight: 600 } },
        { r0: '38%', r: '66%' },
        { r0: '66%', r: '88%', label: { position: 'outside', rotate: 0 } },
      ],
    }],
  }
}

export function planningOption(graph: GraphData): EChartsOption {
  return {
    color: CHART_COLORS,
    tooltip: { trigger: 'item' },
    series: [{
      type: 'sankey',
      data: graph.nodes.map((name) => ({ name })),
      links: graph.links,
      left: 28,
      right: 80,
      top: 24,
      bottom: 24,
      nodeWidth: 14,
      nodeGap: 14,
      draggable: false,
      emphasis: { focus: 'adjacency' },
      lineStyle: { color: 'gradient', curveness: 0.5, opacity: 0.42 },
      label: { color: '#25343d', fontSize: 11 },
      itemStyle: { borderColor: '#fff', borderWidth: 1 },
    }],
  }
}

export function durationOption(
  points: DurationPoint[],
  t: ChartTranslate,
): EChartsOption {
  return {
    color: ['#16869a'],
    grid: { left: 18, right: 34, top: 22, bottom: 18, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (value) => t('charts.seconds', { value: String(value) }),
    },
    xAxis: {
      type: 'value',
      name: t('charts.secondsAxis'),
      nameTextStyle: { color: '#64727c' },
      axisLabel: { color: '#64727c' },
      splitLine: { lineStyle: { color: '#e7ecef' } },
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: points.map((point) => point.action),
      axisLabel: { color: '#25343d', width: 150, overflow: 'truncate' },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series: [{
      type: 'bar',
      data: points.map((point) => ({
        value: point.averageSeconds,
        markerCount: point.markerCount,
      })),
      barMaxWidth: 22,
      itemStyle: { borderRadius: [0, 3, 3, 0] },
      label: {
        show: true,
        position: 'right',
        color: '#64727c',
        formatter: ({ value }: { value: unknown }) => `${value}s`,
      },
    }],
  }
}

export function dependencyOption(graph: GraphData): EChartsOption {
  return {
    color: CHART_COLORS,
    tooltip: { trigger: 'item' },
    series: [{
      type: 'graph',
      layout: 'circular',
      circular: { rotateLabel: true },
      data: graph.nodes.map((name, index) => ({
        name,
        symbolSize: 18 + Math.min(22, graph.links
          .filter((link) => link.source === name || link.target === name)
          .reduce((sum, link) => sum + link.value, 0) * 2),
        itemStyle: { color: CHART_COLORS[index % CHART_COLORS.length] },
      })),
      links: graph.links.map((link) => ({
        ...link,
        lineStyle: { width: Math.min(7, 1 + link.value), curveness: 0.18 },
      })),
      roam: true,
      label: { show: true, color: '#25343d', fontSize: 11 },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 7,
      lineStyle: { color: '#9aaab3', opacity: 0.58 },
      emphasis: { focus: 'adjacency', lineStyle: { opacity: 0.9 } },
    }],
  }
}

export function calendarOption(
  calendar: CalendarData,
  t: ChartTranslate,
): EChartsOption {
  const deviations = calendar.points.map((point) => Math.abs(point.deviation))
  const extent = Math.max(1, ...deviations)
  return {
    tooltip: {
      formatter: (params: unknown) => {
        const item = params as { data?: [string, number, number] }
        const date = item.data?.[0] ?? ''
        const count = item.data?.[1] ?? 0
        const deviation = item.data?.[2] ?? 0
        const sign = deviation > 0 ? '+' : ''
        return `${date}<br/>${t('charts.markers', { count })}<br/>${t('charts.deviation', { value: `${sign}${deviation}` })}`
      },
    },
    visualMap: {
      min: -extent,
      max: extent,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 8,
      text: [t('charts.aboveAverage'), t('charts.belowAverage')],
      inRange: { color: ['#c75a56', '#eef2f3', '#16869a'] },
    },
    calendar: {
      top: 55,
      left: 45,
      right: 24,
      bottom: 70,
      range: [calendar.startDate ?? '', calendar.endDate ?? ''],
      cellSize: ['auto', 18],
      splitLine: { show: true, lineStyle: { color: '#fff', width: 3 } },
      itemStyle: { color: '#f2f5f6', borderColor: '#fff', borderWidth: 2 },
      dayLabel: { color: '#64727c', firstDay: 1 },
      monthLabel: { color: '#25343d' },
      yearLabel: { color: '#17232b', fontWeight: 600 },
    },
    series: [{
      type: 'heatmap',
      coordinateSystem: 'calendar',
      data: calendar.points.map((point) => [point.date, point.value, point.deviation]),
      encode: { value: 2 },
    }],
  }
}
