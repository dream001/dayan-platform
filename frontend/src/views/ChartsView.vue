<script setup lang="ts">
import {
  Calendar,
  Connection,
  Link,
  Refresh,
  Share,
  Timer,
} from '@element-plus/icons-vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ChartCanvas from '@/components/ChartCanvas.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { getChartData, getChartProjects } from '@/services/charts'
import { getErrorMessage } from '@/services/feedback'
import type {
  ChartData,
  ChartProject,
  ChartType,
} from '@/types/chart'
import {
  calendarOption,
  dependencyOption,
  durationOption,
  planningOption,
  relationshipOption,
} from '@/utils/chart-options'

type ChartTab = 'subtree' | 'planning' | 'duration' | 'dependency' | 'calendar'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const translate = (key: string, params?: Record<string, string | number>) =>
  String(t(key, params ?? {}))
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

const chartOption = computed(() => {
  const value = data.value
  if (!value) return {}
  switch (activeTab.value) {
    case 'subtree':
      return relationshipOption(value as import('@/types/chart').HierarchyNode[], translate)
    case 'planning':
      return planningOption(value as import('@/types/chart').GraphData)
    case 'duration':
      return durationOption(value as import('@/types/chart').DurationPoint[], translate)
    case 'dependency':
      return dependencyOption(value as import('@/types/chart').GraphData)
    case 'calendar':
      return calendarOption(value as import('@/types/chart').CalendarData, translate)
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
