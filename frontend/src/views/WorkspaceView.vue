<script setup lang="ts">
import {
  FullScreen,
  Moon,
  Refresh,
  Sunny,
} from '@element-plus/icons-vue'
import type { EChartsCoreOption } from 'echarts/core'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import ChartCanvas from '@/components/ChartCanvas.vue'
import StatePanel from '@/components/StatePanel.vue'
import { getDashboardStatistics } from '@/services/admin'
import { getErrorMessage } from '@/services/feedback'
import type {
  DashboardNamedValue,
  DashboardStatistics,
  DashboardTrendPoint,
} from '@/types/admin'
import { formatDateTime } from '@/utils/format'

const THEME_STORAGE_KEY = 'dayan:dashboard-dark'
const { t } = useI18n()
const loading = ref(true)
const error = ref('')
const statistics = ref<DashboardStatistics | null>(null)
const dashboardRoot = ref<HTMLElement>()
const isDark = ref(window.localStorage.getItem(THEME_STORAGE_KEY) === 'true')
const isFullscreen = ref(false)

const chartText = computed(() => (isDark.value ? '#aab7c0' : '#667782'))
const chartLine = computed(() => (isDark.value ? '#2b3a44' : '#e7ecef'))
const palette = ['#22b8c9', '#3f93e8', '#61b96a', '#f5a623', '#ed5a5a', '#91a5af', '#8b6fc7']
const statusColors: Record<string, string> = {
  PENDING: '#3f93e8',
  WORKING: '#22b8c9',
  REVIEW_PENDING: '#f5a623',
  REJECTED: '#ed5a5a',
  APPROVED: '#61b96a',
  SUBMITTED: '#8b6fc7',
  VALID: '#61b96a',
  INVALID: '#91a5af',
  ERROR: '#ed5a5a',
  PASSED: '#61b96a',
  FAILED: '#ed5a5a',
  UNCHECKED: '#91a5af',
}

const metrics = computed(() => {
  const value = statistics.value
  if (!value) return []
  return [
    {
      label: t('workspace.datasetCount'),
      value: formatCount(value.datasetCount),
      tone: 'cyan',
    },
    {
      label: t('workspace.datasetDuration'),
      value: formatDuration(value.datasetDurationSeconds),
      tone: 'blue',
    },
    {
      label: t('workspace.annotationCount'),
      value: formatCount(value.annotationCount),
      tone: 'green',
    },
    {
      label: t('workspace.annotationDuration'),
      value: formatDuration(value.annotationDurationSeconds),
      tone: 'amber',
    },
  ]
})

const distributionPanels = computed(() => {
  const value = statistics.value
  if (!value) return []
  return [
    {
      key: 'projects',
      title: t('workspace.projectDistribution'),
      available: value.projectDistributionAvailable,
      data: localizeDistribution(value.projectDistribution, false),
      colors: palette,
      meta: '',
    },
    {
      key: 'collections',
      title: t('workspace.collectionDistribution'),
      available: value.collectionDistributionAvailable,
      data: localizeDistribution(value.collectionStatusDistribution),
      colors: ['#3f93e8', '#22b8c9', '#f5a623', '#ed5a5a', '#61b96a', '#8b6fc7'],
      meta: '',
    },
    {
      key: 'annotations',
      title: t('workspace.annotationQuality'),
      available: value.dataMetricsAvailable,
      data: localizeDistribution(value.annotationQualityDistribution),
      colors: ['#61b96a', '#ed5a5a', '#f5a623'],
      meta: t('workspace.qualityRates', {
        pass: formatRate(value.annotationPassRate),
        resolve: formatRate(value.annotationResolveRate),
      }),
    },
    {
      key: 'quality',
      title: t('workspace.dataQuality'),
      available: value.qualityDistributionAvailable,
      data: localizeDistribution(value.dataQualityDistribution),
      colors: ['#61b96a', '#ed5a5a', '#91a5af'],
      meta: '',
    },
  ].map((panel) => ({
    ...panel,
    option: pieOption(panel.data, panel.colors),
  }))
})

const trendPanels = computed(() => {
  const value = statistics.value
  if (!value) return []
  return [
    {
      key: 'data-growth',
      title: t('workspace.dataGrowth'),
      available: value.dataMetricsAvailable,
      data: value.dataGrowthTrend,
      series: [{ key: 'total', label: t('workspace.datasets'), color: '#22b8c9' }],
    },
    {
      key: 'data-quality',
      title: t('workspace.dataQualityTrend'),
      available: value.qualityDistributionAvailable,
      data: value.dataQualityTrend,
      series: [
        { key: 'passed', label: t('workspace.status.PASSED'), color: '#61b96a' },
        { key: 'failed', label: t('workspace.status.FAILED'), color: '#ed5a5a' },
        { key: 'unchecked', label: t('workspace.status.UNCHECKED'), color: '#91a5af' },
      ],
    },
    {
      key: 'annotation-growth',
      title: t('workspace.annotationGrowth'),
      available: value.dataMetricsAvailable,
      data: value.annotationGrowthTrend,
      series: [{ key: 'total', label: t('workspace.annotations'), color: '#61b96a' }],
    },
    {
      key: 'annotation-quality',
      title: t('workspace.annotationQualityTrend'),
      available: value.dataMetricsAvailable,
      data: value.annotationQualityTrend,
      series: [
        { key: 'valid', label: t('workspace.status.VALID'), color: '#61b96a' },
        { key: 'error', label: t('workspace.status.ERROR'), color: '#ed5a5a' },
        { key: 'invalid', label: t('workspace.status.INVALID'), color: '#91a5af' },
      ],
    },
  ].map((panel) => ({
    ...panel,
    option: lineOption(panel.data, panel.series),
  }))
})

function formatCount(value: number | null) {
  return value == null ? '-' : value.toLocaleString()
}

function formatDuration(value: number | null) {
  if (value == null) return '-'
  const minutes = Math.floor(value / 60)
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`
}

function formatRate(value: number | null) {
  return value == null ? '-' : `${value.toFixed(1)}%`
}

function localizeDistribution(data: DashboardNamedValue[], status = true) {
  return data.map((item) => ({
    name: status ? t(`workspace.status.${item.name}`) : item.name,
    value: item.value,
    itemStyle: status ? { color: statusColors[item.name] } : undefined,
  }))
}

function pieOption(data: DashboardNamedValue[], colors: string[]): EChartsCoreOption {
  return {
    animationDuration: 500,
    color: colors,
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} ({d}%)' },
    legend: {
      type: 'scroll',
      bottom: 0,
      left: 2,
      right: 2,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: chartText.value, fontSize: 11 },
    },
    series: [{
      type: 'pie',
      radius: ['48%', '70%'],
      center: ['50%', '43%'],
      minAngle: 3,
      avoidLabelOverlap: true,
      itemStyle: {
        borderColor: isDark.value ? '#18242c' : '#ffffff',
        borderWidth: 2,
      },
      label: {
        color: chartText.value,
        fontSize: 10,
        formatter: '{b} {c}',
      },
      labelLine: { length: 8, length2: 5 },
      data,
    }],
  }
}

function lineOption(
  data: DashboardTrendPoint[],
  series: Array<{ key: string; label: string; color: string }>,
): EChartsCoreOption {
  return {
    animationDuration: 500,
    color: series.map((item) => item.color),
    tooltip: { trigger: 'axis' },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 12,
      itemHeight: 3,
      textStyle: { color: chartText.value, fontSize: 10 },
    },
    grid: { top: 34, right: 14, bottom: 22, left: 42 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.map((item) => item.date.slice(5)),
      axisLine: { lineStyle: { color: chartLine.value } },
      axisTick: { show: false },
      axisLabel: { color: chartText.value, fontSize: 10, interval: 9 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitNumber: 3,
      axisLabel: { color: chartText.value, fontSize: 10 },
      splitLine: { lineStyle: { color: chartLine.value } },
    },
    series: series.map((item) => ({
      name: item.label,
      type: 'line',
      smooth: 0.25,
      showSymbol: false,
      lineStyle: { width: 2 },
      areaStyle: series.length === 1 ? { opacity: 0.08 } : undefined,
      data: data.map((point) => point.values[item.key] ?? 0),
    })),
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    statistics.value = await getDashboardStatistics()
  } catch (reason) {
    error.value = getErrorMessage(reason, t('workspace.loadFailed'))
  } finally {
    loading.value = false
  }
}

function toggleTheme() {
  isDark.value = !isDark.value
  window.localStorage.setItem(THEME_STORAGE_KEY, String(isDark.value))
}

async function toggleFullscreen() {
  if (!document.fullscreenElement) {
    await dashboardRoot.value?.requestFullscreen()
  } else {
    await document.exitFullscreen()
  }
}

function syncFullscreen() {
  isFullscreen.value = document.fullscreenElement === dashboardRoot.value
}

onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreen)
  void load()
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', syncFullscreen)
})
</script>

<template>
  <section
    ref="dashboardRoot"
    class="dashboard"
    :class="{ 'dashboard--dark': isDark }"
  >
    <header class="dashboard__header">
      <div>
        <p>{{ t('workspace.eyebrow') }}</p>
        <h1>{{ t('workspace.title') }}</h1>
        <span>{{ t('workspace.description') }}</span>
      </div>
      <div class="dashboard__actions">
        <span v-if="statistics">{{ t('workspace.updatedAt', { time: formatDateTime(statistics.generatedAt) }) }}</span>
        <el-tooltip :content="t('workspace.refresh')">
          <el-button
            :icon="Refresh"
            circle
            :aria-label="t('workspace.refresh')"
            :loading="loading"
            @click="load"
          />
        </el-tooltip>
        <el-tooltip :content="isDark ? t('workspace.lightTheme') : t('workspace.darkTheme')">
          <el-button
            :icon="isDark ? Sunny : Moon"
            circle
            :aria-label="isDark ? t('workspace.lightTheme') : t('workspace.darkTheme')"
            @click="toggleTheme"
          />
        </el-tooltip>
        <el-tooltip :content="isFullscreen ? t('workspace.exitFullscreen') : t('workspace.fullscreen')">
          <el-button
            :icon="FullScreen"
            circle
            :aria-label="isFullscreen ? t('workspace.exitFullscreen') : t('workspace.fullscreen')"
            @click="toggleFullscreen"
          />
        </el-tooltip>
      </div>
    </header>

    <StatePanel
      v-if="loading && !statistics"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !statistics"
      state="error"
      :title="t('workspace.loadFailed')"
      :description="error"
      @retry="load"
    />
    <template v-else-if="statistics">
      <div
        v-if="error"
        class="dashboard__warning"
      >
        {{ error }}
      </div>

      <div class="metric-grid">
        <article
          v-for="metric in metrics"
          :key="metric.label"
          class="metric-card"
          :class="`metric-card--${metric.tone}`"
        >
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <i aria-hidden="true" />
        </article>
      </div>

      <div class="distribution-grid">
        <article
          v-for="panel in distributionPanels"
          :key="panel.key"
          class="chart-card chart-card--distribution"
        >
          <header>
            <h2>{{ panel.title }}</h2>
            <span v-if="panel.meta">{{ panel.meta }}</span>
          </header>
          <div
            v-if="!panel.available || panel.data.length === 0"
            class="chart-empty"
          >
            <strong>{{ t('workspace.noData') }}</strong>
            <span>{{ panel.available ? t('workspace.noDataDescription') : t('workspace.noPermission') }}</span>
          </div>
          <ChartCanvas
            v-else
            :option="panel.option"
            :height="230"
            :aria-label="panel.title"
          />
        </article>
      </div>

      <div class="trend-grid">
        <article
          v-for="panel in trendPanels"
          :key="panel.key"
          class="chart-card chart-card--trend"
        >
          <header>
            <h2>{{ panel.title }}</h2>
            <span>{{ t('workspace.last30Days') }}</span>
          </header>
          <div
            v-if="!panel.available || panel.data.length === 0"
            class="chart-empty"
          >
            <strong>{{ t('workspace.noData') }}</strong>
            <span>{{ panel.available ? t('workspace.noDataDescription') : t('workspace.noPermission') }}</span>
          </div>
          <ChartCanvas
            v-else
            :option="panel.option"
            :height="190"
            :aria-label="panel.title"
          />
        </article>
      </div>
    </template>
  </section>
</template>

<style scoped>
.dashboard {
  --dashboard-bg: #f4f7f9;
  --dashboard-surface: #ffffff;
  --dashboard-text: #22313a;
  --dashboard-muted: #70808a;
  --dashboard-border: #e0e7eb;
  --dashboard-shadow: 0 1px 2px rgb(25 45 57 / 7%), 0 10px 28px rgb(25 45 57 / 4%);
  width: 100%;
  min-height: 100%;
  padding: 2px;
  color: var(--dashboard-text);
  background: var(--dashboard-bg);
  transition: color 180ms ease, background-color 180ms ease;
}

.dashboard:fullscreen {
  overflow: auto;
  padding: 24px;
}

.dashboard--dark {
  --dashboard-bg: #10181e;
  --dashboard-surface: #18242c;
  --dashboard-text: #edf3f6;
  --dashboard-muted: #96a6b0;
  --dashboard-border: #2a3a44;
  --dashboard-shadow: 0 1px 2px rgb(0 0 0 / 24%), 0 12px 30px rgb(0 0 0 / 14%);
}

.dashboard__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
  padding: 0 0 20px;
}

.dashboard__header p {
  margin: 0 0 4px;
  color: #16889a;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.dashboard__header h1 {
  margin: 0;
  font-size: 25px;
  font-weight: 680;
  letter-spacing: 0;
}

.dashboard__header > div > span {
  display: block;
  margin-top: 6px;
  color: var(--dashboard-muted);
  font-size: 12px;
}

.dashboard__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dashboard__actions > span {
  margin-right: 4px;
  color: var(--dashboard-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.dashboard :deep(.el-button) {
  color: var(--dashboard-muted);
  border-color: var(--dashboard-border);
  background: var(--dashboard-surface);
}

.dashboard__warning {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-left: 3px solid #f5a623;
  color: var(--dashboard-muted);
  background: var(--dashboard-surface);
  font-size: 12px;
}

.metric-grid,
.distribution-grid,
.trend-grid {
  display: grid;
  gap: 14px;
}

.metric-grid,
.distribution-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.trend-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 14px;
}

.metric-card,
.chart-card {
  position: relative;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--dashboard-border);
  border-radius: 5px;
  background: var(--dashboard-surface);
  box-shadow: var(--dashboard-shadow);
}

.metric-card {
  display: flex;
  height: 104px;
  flex-direction: column;
  justify-content: center;
  padding: 18px;
}

.metric-card span {
  position: relative;
  z-index: 1;
  color: var(--dashboard-muted);
  font-size: 12px;
}

.metric-card strong {
  position: relative;
  z-index: 1;
  margin-top: 7px;
  font-family: var(--font-mono);
  font-size: 29px;
  font-weight: 650;
  letter-spacing: 0;
}

.metric-card i {
  position: absolute;
  top: 0;
  right: 0;
  width: 5px;
  height: 100%;
  opacity: 0.72;
}

.metric-card--cyan i {
  background: #22b8c9;
}

.metric-card--blue i {
  background: #3f93e8;
}

.metric-card--green i {
  background: #61b96a;
}

.metric-card--amber i {
  background: #f5a623;
}

.distribution-grid {
  margin-top: 14px;
}

.chart-card {
  padding: 17px 16px 12px;
}

.chart-card--distribution {
  min-height: 292px;
}

.chart-card--trend {
  min-height: 248px;
}

.chart-card > header {
  display: flex;
  min-height: 24px;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.chart-card h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 680;
  letter-spacing: 0;
}

.chart-card header span {
  overflow: hidden;
  color: var(--dashboard-muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chart-empty {
  display: flex;
  min-height: 210px;
  align-items: center;
  flex-direction: column;
  justify-content: center;
  color: var(--dashboard-muted);
  text-align: center;
}

.chart-card--trend .chart-empty {
  min-height: 175px;
}

.chart-empty strong {
  color: var(--dashboard-text);
  font-size: 13px;
}

.chart-empty span {
  max-width: 220px;
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.6;
}

@media (max-width: 1180px) {
  .metric-grid,
  .distribution-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .dashboard__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .dashboard__actions {
    width: 100%;
  }

  .dashboard__actions > span {
    overflow: hidden;
    margin-right: auto;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .metric-grid,
  .distribution-grid,
  .trend-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 420px) {
  .dashboard__actions > span {
    display: none;
  }

  .dashboard__actions {
    justify-content: flex-end;
  }

  .metric-card strong {
    font-size: 26px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dashboard {
    transition: none;
  }
}
</style>
