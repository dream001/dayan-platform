<script setup lang="ts">
import {
  Calendar,
  Connection,
  Link,
  Refresh,
  Share,
  Timer,
} from '@element-plus/icons-vue'
import type { EChartsOption } from 'echarts'
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ChartCanvas from '@/components/ChartCanvas.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { getChartData, getChartProjects } from '@/services/charts'
import { getErrorMessage } from '@/services/feedback'
import type {
  CalendarData,
  ChartData,
  ChartProject,
  ChartType,
  DurationPoint,
  GraphData,
  HierarchyNode,
} from '@/types/chart'

type ChartTab = 'subtree' | 'planning' | 'duration' | 'dependency' | 'calendar'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const projects = ref<ChartProject[]>([])
const projectId = ref<number>()
const data = ref<ChartData>()
const loadingProjects = ref(true)
const loadingChart = ref(false)
const projectError = ref('')
const chartError = ref('')

const tabs: Array<{ value: ChartTab, labelKey: string, icon: typeof Connection }> = [
  { value: 'subtree', labelKey: 'charts.tabs.relationships', icon: Connection },
  { value: 'planning', labelKey: 'charts.tabs.planning', icon: Share },
  { value: 'duration', labelKey: 'charts.tabs.duration', icon: Timer },
  { value: 'dependency', labelKey: 'charts.tabs.dependencies', icon: Link },
  { value: 'calendar', labelKey: 'charts.tabs.calendar', icon: Calendar },
]

const endpointByTab: Record<ChartTab, ChartType> = {
  subtree: 'relationships',
  planning: 'planning',
  duration: 'durations',
  dependency: 'dependencies',
  calendar: 'calendar',
}

const activeTab = computed<ChartTab>(() => {
  const value = String(route.params.chart ?? 'subtree')
  return tabs.some((tab) => tab.value === value) ? value as ChartTab : 'subtree'
})

const selectedProject = computed(() =>
  projects.value.find((project) => project.id === projectId.value),
)

const hasData = computed(() => {
  if (!data.value) return false
  if (Array.isArray(data.value)) return data.value.length > 0
  if ('links' in data.value) return data.value.links.length > 0
  return data.value.points.length > 0
})

const chartOption = computed<EChartsOption>(() => {
  const value = data.value
  if (!value) return {}
  switch (activeTab.value) {
    case 'subtree':
      return relationshipOption(value as HierarchyNode[])
    case 'planning':
      return sankeyOption(value as GraphData)
    case 'duration':
      return durationOption(value as DurationPoint[])
    case 'dependency':
      return dependencyOption(value as GraphData)
    case 'calendar':
      return calendarOption(value as CalendarData)
  }
  return {}
})

async function loadProjects() {
  loadingProjects.value = true
  projectError.value = ''
  try {
    projects.value = await getChartProjects()
    const queryId = Number(route.query.project)
    projectId.value = projects.value.some((project) => project.id === queryId)
      ? queryId
      : undefined
    if (projectId.value) await loadChart()
  } catch (reason) {
    projectError.value = getErrorMessage(reason, t('charts.projectLoadFailed'))
  } finally {
    loadingProjects.value = false
  }
}

async function loadChart() {
  if (!projectId.value) {
    data.value = undefined
    chartError.value = ''
    return
  }
  loadingChart.value = true
  chartError.value = ''
  try {
    data.value = await getChartData(
      endpointByTab[activeTab.value],
      projectId.value,
      locale.value,
    )
  } catch (reason) {
    data.value = undefined
    chartError.value = getErrorMessage(reason, t('charts.loadFailed'))
  } finally {
    loadingChart.value = false
  }
}

async function selectProject(value?: number) {
  projectId.value = value
  await router.replace({
    query: {
      ...route.query,
      project: value ? String(value) : undefined,
    },
  })
}

async function selectTab(value: string | number) {
  await router.push({
    path: `/data/charts/${String(value)}`,
    query: route.query,
  })
}

function relationshipOption(nodes: HierarchyNode[]): EChartsOption {
  return {
    color: ['#6c4ba7', '#16869a', '#d09a37', '#c75a56', '#56708c'],
    tooltip: { trigger: 'item', valueFormatter: (value) => t('charts.markers', { count: value }) },
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

function sankeyOption(graph: GraphData): EChartsOption {
  return {
    color: ['#6c4ba7', '#16869a', '#d09a37', '#c75a56', '#56708c'],
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

function durationOption(points: DurationPoint[]): EChartsOption {
  return {
    color: ['#16869a'],
    grid: { left: 18, right: 34, top: 22, bottom: 18, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (value) => t('charts.seconds', { value }),
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

function dependencyOption(graph: GraphData): EChartsOption {
  return {
    color: ['#6c4ba7', '#16869a', '#d09a37', '#c75a56', '#56708c'],
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
        itemStyle: { color: ['#6c4ba7', '#16869a', '#d09a37', '#c75a56'][index % 4] },
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

function calendarOption(calendar: CalendarData): EChartsOption {
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

watch(
  () => route.params.chart,
  async (value) => {
    if (value && !tabs.some((tab) => tab.value === value)) {
      await router.replace({ path: '/data/charts/subtree', query: route.query })
      return
    }
    if (projectId.value) await loadChart()
  },
)

watch(locale, async () => {
  if (
    projectId.value
    && ['subtree', 'planning'].includes(activeTab.value)
  ) await loadChart()
})

onMounted(loadProjects)
</script>

<template>
  <section class="admin-page charts-view">
    <PageHeader
      :title="t('charts.title')"
      :eyebrow="t('charts.eyebrow')"
      :description="t('charts.description')"
    >
      <template #actions>
        <el-select
          :model-value="projectId"
          class="project-select"
          :placeholder="t('charts.selectProject')"
          :loading="loadingProjects"
          clearable
          filterable
          @update:model-value="selectProject"
        >
          <el-option
            v-for="project in projects"
            :key="project.id"
            :label="project.name"
            :value="project.id"
          >
            <span>{{ project.name }}</span>
            <small>{{ project.code }}</small>
          </el-option>
        </el-select>
        <el-tooltip :content="t('common.refresh')">
          <el-button
            :icon="Refresh"
            circle
            :aria-label="t('common.refresh')"
            :loading="loadingChart"
            :disabled="!projectId"
            @click="loadChart"
          />
        </el-tooltip>
      </template>
    </PageHeader>

    <StatePanel
      v-if="projectError"
      state="error"
      :title="t('charts.projectLoadFailed')"
      :description="projectError"
      @retry="loadProjects"
    />
    <template v-else>
      <nav
        class="chart-tabs"
        :aria-label="t('charts.chartTypes')"
      >
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          :class="{ active: activeTab === tab.value }"
          :aria-current="activeTab === tab.value ? 'page' : undefined"
          @click="selectTab(tab.value)"
        >
          <el-icon><component :is="tab.icon" /></el-icon>
          <span>{{ t(tab.labelKey) }}</span>
        </button>
      </nav>

      <div class="chart-context">
        <div>
          <span>{{ t(`charts.tabs.${activeTab === 'subtree' ? 'relationships' : activeTab === 'dependency' ? 'dependencies' : activeTab}`) }}</span>
          <strong>{{ selectedProject?.name || t('charts.noProject') }}</strong>
        </div>
        <p v-if="activeTab === 'calendar' && data && 'dailyAverage' in data">
          {{ t('charts.dailyAverage', { value: data.dailyAverage }) }}
        </p>
      </div>

      <div class="chart-stage">
        <StatePanel
          v-if="!projectId"
          state="empty"
          :title="t('charts.selectProject')"
          :description="t('charts.selectProjectDesc')"
        />
        <StatePanel
          v-else-if="loadingChart"
          state="loading"
        />
        <StatePanel
          v-else-if="chartError"
          state="error"
          :title="t('charts.loadFailed')"
          :description="chartError"
          @retry="loadChart"
        />
        <StatePanel
          v-else-if="!hasData"
          state="empty"
          :title="t('charts.noData')"
          :description="t('charts.noDataDesc')"
        />
        <ChartCanvas
          v-else
          :option="chartOption"
          :aria-label="t('charts.chartAria', { project: selectedProject?.name })"
        />
      </div>
    </template>
  </section>
</template>

<style scoped>
.project-select {
  width: min(300px, 44vw);
}

.project-select small {
  float: right;
  margin-left: 24px;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.chart-tabs {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin-top: 24px;
  border-block: 1px solid var(--color-border-strong);
}

.chart-tabs button {
  display: flex;
  min-width: 0;
  height: 54px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 12px;
  cursor: pointer;
  border: 0;
  border-right: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  background: transparent;
  transition: color 160ms ease, background 160ms ease;
}

.chart-tabs button:last-child {
  border-right: 0;
}

.chart-tabs button:hover {
  color: var(--color-text-primary);
  background: #f0f5f6;
}

.chart-tabs button.active {
  color: #fff;
  background: #25343d;
}

.chart-tabs .el-icon {
  flex: 0 0 auto;
  font-size: 16px;
}

.chart-context {
  display: flex;
  min-height: 74px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 4px;
  border-bottom: 1px solid var(--color-border);
}

.chart-context div {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 13px;
}

.chart-context span,
.chart-context p {
  color: var(--color-text-muted);
  font-size: 11px;
}

.chart-context strong {
  overflow: hidden;
  font-size: 14px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chart-context p {
  margin: 0;
  font-family: var(--font-mono);
}

.chart-stage {
  min-height: 520px;
  padding: 12px 0 0;
}

.chart-stage :deep(.state-panel) {
  min-height: 480px;
}

@media (max-width: 700px) {
  .project-select {
    width: calc(100% - 42px);
  }

  .chart-tabs {
    grid-template-columns: repeat(5, 72px);
    overflow-x: auto;
  }

  .chart-tabs button {
    height: 62px;
    flex-direction: column;
    gap: 4px;
    padding: 5px;
    font-size: 11px;
  }

  .chart-context {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .chart-stage {
    min-height: 460px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chart-tabs button {
    transition: none;
  }
}
</style>
