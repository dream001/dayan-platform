<script setup lang="ts">
import { ArrowRight, Refresh } from '@element-plus/icons-vue'
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
  ChartProject,
  DurationPoint,
  GraphData,
  HierarchyNode,
} from '@/types/chart'
import {
  calendarOption,
  dependencyOption,
  durationOption,
  planningOption,
  relationshipOption,
} from '@/utils/chart-options'

interface VisualizationData {
  relationships: HierarchyNode[]
  planning: GraphData
  durations: DurationPoint[]
  dependencies: GraphData
  calendar: CalendarData
}

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const translate = (key: string, params?: Record<string, string | number>) =>
  String(t(key, params ?? {}))

const projects = ref<ChartProject[]>([])
const projectId = ref<number>()
const data = ref<VisualizationData>()
const loadingProjects = ref(true)
const loading = ref(false)
const error = ref('')

const selectedProject = computed(() =>
  projects.value.find((project) => project.id === projectId.value),
)

const metrics = computed(() => {
  const current = data.value
  if (!current) return []
  const markerTotal = current.calendar.points.reduce((sum, point) => sum + point.value, 0)
  const durationWeight = current.durations.reduce(
    (sum, point) => sum + point.averageSeconds * point.markerCount,
    0,
  )
  const durationCount = current.durations.reduce((sum, point) => sum + point.markerCount, 0)
  const transitionTotal = current.planning.links.reduce((sum, link) => sum + link.value, 0)
  return [
    { label: t('visualization.metrics.markers'), value: markerTotal.toLocaleString() },
    { label: t('visualization.metrics.actions'), value: current.durations.length.toLocaleString() },
    {
      label: t('visualization.metrics.duration'),
      value: durationCount ? `${(durationWeight / durationCount).toFixed(2)}s` : '—',
    },
    { label: t('visualization.metrics.transitions'), value: transitionTotal.toLocaleString() },
    { label: t('visualization.metrics.activeDays'), value: current.calendar.points.length.toLocaleString() },
  ]
})

const panels = computed(() => {
  if (!data.value) return []
  return [
    {
      key: 'subtree',
      title: t('charts.tabs.relationships'),
      description: t('visualization.panels.relationships'),
      empty: data.value.relationships.length === 0,
      option: relationshipOption(data.value.relationships, translate),
    },
    {
      key: 'planning',
      title: t('charts.tabs.planning'),
      description: t('visualization.panels.planning'),
      empty: data.value.planning.links.length === 0,
      option: planningOption(data.value.planning),
    },
    {
      key: 'duration',
      title: t('charts.tabs.duration'),
      description: t('visualization.panels.duration'),
      empty: data.value.durations.length === 0,
      option: durationOption(data.value.durations, translate),
    },
    {
      key: 'dependency',
      title: t('charts.tabs.dependencies'),
      description: t('visualization.panels.dependencies'),
      empty: data.value.dependencies.links.length === 0,
      option: dependencyOption(data.value.dependencies),
    },
    {
      key: 'calendar',
      title: t('charts.tabs.calendar'),
      description: t('visualization.panels.calendar'),
      empty: data.value.calendar.points.length === 0,
      option: calendarOption(data.value.calendar, translate),
    },
  ]
})

async function loadProjects() {
  loadingProjects.value = true
  error.value = ''
  try {
    projects.value = await getChartProjects()
    const queryId = Number(route.query.project)
    projectId.value = projects.value.some((project) => project.id === queryId)
      ? queryId
      : projects.value[0]?.id
    if (projectId.value) {
      await router.replace({ query: { ...route.query, project: String(projectId.value) } })
      await loadAll()
    }
  } catch (reason) {
    error.value = getErrorMessage(reason, t('visualization.loadFailed'))
  } finally {
    loadingProjects.value = false
  }
}

async function loadAll() {
  if (!projectId.value) {
    data.value = undefined
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [relationships, planning, durations, dependencies, calendar] = await Promise.all([
      getChartData('relationships', projectId.value, locale.value) as Promise<HierarchyNode[]>,
      getChartData('planning', projectId.value, locale.value) as Promise<GraphData>,
      getChartData('durations', projectId.value, locale.value) as Promise<DurationPoint[]>,
      getChartData('dependencies', projectId.value, locale.value) as Promise<GraphData>,
      getChartData('calendar', projectId.value, locale.value) as Promise<CalendarData>,
    ])
    data.value = { relationships, planning, durations, dependencies, calendar }
  } catch (reason) {
    data.value = undefined
    error.value = getErrorMessage(reason, t('visualization.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function selectProject(value?: number) {
  projectId.value = value
  await router.replace({
    query: { ...route.query, project: value ? String(value) : undefined },
  })
  await loadAll()
}

watch(locale, loadAll)
onMounted(loadProjects)
</script>

<template>
  <section class="admin-page visualization-view">
    <PageHeader
      :title="t('visualization.title')"
      :eyebrow="t('visualization.eyebrow')"
      :description="t('visualization.description')"
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
            :loading="loading"
            :disabled="!projectId"
            @click="loadAll"
          />
        </el-tooltip>
      </template>
    </PageHeader>

    <StatePanel
      v-if="error"
      state="error"
      :title="t('visualization.loadFailed')"
      :description="error"
      @retry="projectId ? loadAll() : loadProjects()"
    />
    <StatePanel
      v-else-if="loadingProjects || loading"
      state="loading"
    />
    <StatePanel
      v-else-if="!projectId"
      state="empty"
      :title="t('charts.selectProject')"
      :description="t('charts.selectProjectDesc')"
    />
    <template v-else-if="data">
      <div class="project-context">
        <div>
          <span>{{ t('visualization.currentProject') }}</span>
          <strong>{{ selectedProject?.name }}</strong>
        </div>
        <span class="project-code">{{ selectedProject?.code }}</span>
      </div>

      <dl class="metric-strip">
        <div
          v-for="metric in metrics"
          :key="String(metric.label)"
        >
          <dt>{{ metric.label }}</dt>
          <dd>{{ metric.value }}</dd>
        </div>
      </dl>

      <div class="visual-grid">
        <article
          v-for="panel in panels"
          :key="panel.key"
          class="visual-panel"
          :class="`visual-panel--${panel.key}`"
        >
          <header>
            <div>
              <h2>{{ panel.title }}</h2>
              <p>{{ panel.description }}</p>
            </div>
            <RouterLink
              :to="{ path: `/data/charts/${panel.key}`, query: { project: projectId } }"
              :aria-label="t('visualization.openDetail', { name: panel.title })"
            >
              <el-icon><ArrowRight /></el-icon>
            </RouterLink>
          </header>
          <div class="panel-stage">
            <StatePanel
              v-if="panel.empty"
              state="empty"
              :title="t('charts.noData')"
              :description="t('charts.noDataDesc')"
            />
            <ChartCanvas
              v-else
              :option="panel.option"
              :height="panel.key === 'calendar' ? 300 : 340"
              :aria-label="String(panel.title)"
            />
          </div>
        </article>
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

.project-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 2px 13px;
  border-bottom: 1px solid var(--color-border-strong);
}

.project-context div {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 12px;
}

.project-context span {
  color: var(--color-text-muted);
  font-size: 11px;
}

.project-context strong {
  overflow: hidden;
  font-size: 15px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-code {
  font-family: var(--font-mono);
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin: 0;
  border-bottom: 1px solid var(--color-border-strong);
}

.metric-strip div {
  min-width: 0;
  padding: 18px 18px 17px;
  border-right: 1px solid var(--color-border);
}

.metric-strip div:first-child {
  padding-left: 2px;
}

.metric-strip div:last-child {
  border-right: 0;
}

.metric-strip dt {
  overflow: hidden;
  color: var(--color-text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-strip dd {
  margin: 7px 0 0;
  color: var(--color-text-primary);
  font-family: var(--font-mono);
  font-size: 24px;
  font-weight: 650;
  line-height: 1;
}

.visual-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding-top: 18px;
}

.visual-panel {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
}

.visual-panel--subtree,
.visual-panel--calendar {
  grid-column: 1 / -1;
}

.visual-panel header {
  display: flex;
  min-height: 72px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-border);
}

.visual-panel h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 650;
}

.visual-panel p {
  margin: 5px 0 0;
  color: var(--color-text-muted);
  font-size: 11px;
}

.visual-panel a {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 4px;
  color: var(--color-text-secondary);
  transition: color 150ms ease, background-color 150ms ease;
}

.visual-panel a:hover {
  color: #fff;
  background: var(--color-ink);
}

.panel-stage {
  min-height: 300px;
  padding: 6px;
}

.panel-stage :deep(.state-panel) {
  min-height: 300px;
}

@media (max-width: 900px) {
  .metric-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .metric-strip div:nth-child(3) {
    border-right: 0;
  }

  .metric-strip div:nth-child(n + 4) {
    border-top: 1px solid var(--color-border);
  }
}

@media (max-width: 700px) {
  .project-select {
    width: calc(100% - 42px);
  }

  .metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-strip div:nth-child(3) {
    border-right: 1px solid var(--color-border);
  }

  .metric-strip div:nth-child(even) {
    border-right: 0;
  }

  .metric-strip div:nth-child(n + 3) {
    border-top: 1px solid var(--color-border);
  }

  .visual-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .visual-panel--subtree,
  .visual-panel--calendar {
    grid-column: auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .visual-panel a {
    transition: none;
  }
}
</style>
