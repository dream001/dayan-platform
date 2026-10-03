<script setup lang="ts">
import {
  Close,
  Delete,
  Refresh,
  RefreshLeft,
  VideoPause,
  VideoPlay,
} from '@element-plus/icons-vue'
import type { EChartsOption } from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ChartCanvas from '@/components/ChartCanvas.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { getAuditLogs } from '@/services/admin'
import { getErrorMessage, notifyError } from '@/services/feedback'
import {
  cancelQueueTask,
  cleanQueueHistory,
  clearPendingTasks,
  collectMonitoringMetrics,
  deleteQueueTask,
  getAccessLogs,
  getLoginLogs,
  getMonitoredExports,
  getMonitoringOverview,
  getOnlineUsers,
  getQueueSummary,
  getSystemInformation,
  retryAllFailedTasks,
  retryQueueTask,
  setQueuePaused,
} from '@/services/monitoring'
import type { OperationLogSummary } from '@/types/admin'
import type {
  AccessLog,
  ComponentKey,
  ComponentState,
  ExportQueueStatus,
  LoginLog,
  MonitoredExportTask,
  MonitoringOverview,
  MonitorRange,
  OnlineUsers,
  QueueSummary,
  SystemInformation,
} from '@/types/monitoring'
import { formatBytes, formatDateTime } from '@/utils/format'

const { t } = useI18n()

const activeTab = ref('overview')
const logTab = ref('access')
const loading = ref(false)
const error = ref('')
const overview = ref<MonitoringOverview | null>(null)
const range = ref<MonitorRange>('1h')
const systemInfo = ref<SystemInformation | null>(null)
const onlineUsers = ref<OnlineUsers | null>(null)
const accessLogs = ref<AccessLog[]>([])
const loginLogs = ref<LoginLog[]>([])
const operationLogs = ref<OperationLogSummary[]>([])
const exports = ref<MonitoredExportTask[]>([])
const queue = ref<QueueSummary | null>(null)
const total = ref(0)
const collecting = ref(false)
const actionLoading = ref(false)
const dateRange = ref<[Date, Date] | null>(null)

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  username: '',
  statusCode: undefined as number | undefined,
  result: undefined as 'SUCCESS' | 'FAILURE' | undefined,
  format: '',
  status: undefined as ExportQueueStatus | undefined,
})

const chartOption = computed<EChartsOption>(() => {
  const points = overview.value?.trend ?? []
  const labels = points.map((point) => formatDateTime(point.collectedAt))
  const axis = {
    type: 'value' as const,
    axisLabel: { color: '#71808a' },
    splitLine: { lineStyle: { color: '#e8edef' } },
  }
  return {
    animationDuration: 350,
    color: ['#12748a', '#6c4ba7', '#d09a37', '#b34242'],
    grid: { left: 18, right: 18, top: 18, bottom: 22, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: labels,
      axisLabel: { color: '#71808a', hideOverlap: true },
      axisLine: { lineStyle: { color: '#dce4e8' } },
      axisTick: { show: false },
    },
    yAxis: [axis, { ...axis, position: 'right' }],
    series: [
      {
        name: t('operations.databaseLatency'),
        type: 'line',
        data: points.map((point) => point.databaseLatencyMs),
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 2 },
      },
      {
        name: t('operations.queueBacklog'),
        type: 'line',
        yAxisIndex: 1,
        data: points.map((point) => point.queueBacklog),
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 2 },
      },
      {
        name: t('operations.cpuUsage'),
        type: 'line',
        yAxisIndex: 1,
        data: points.map((point) => point.cpuUsagePercent),
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.5 },
      },
      {
        name: t('operations.memoryUsage'),
        type: 'line',
        yAxisIndex: 1,
        data: points.map((point) => point.memoryUsagePercent),
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.5 },
      },
    ],
  }
})

const pageCount = computed(() => Math.ceil(total.value / query.size))

function componentState(key: ComponentKey): ComponentState {
  return overview.value?.components.find((component) => component.key === key)?.status
    ?? 'NOT_CONFIGURED'
}

function componentLatency(key: ComponentKey) {
  return overview.value?.components.find((component) => component.key === key)?.latencyMs ?? null
}

function timeParams() {
  return {
    startTime: dateRange.value?.[0].toISOString(),
    endTime: dateRange.value?.[1].toISOString(),
  }
}

function statusType(status: string) {
  if (['UP', 'SUCCESS', 'COMPLETED'].includes(status)) return 'success'
  if (['DOWN', 'FAILURE', 'FAILED'].includes(status)) return 'danger'
  if (['PROCESSING', 'PENDING'].includes(status)) return 'warning'
  return 'info'
}

function duration(seconds: number) {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return hours ? `${hours}h ${minutes}m` : `${minutes}m`
}

async function loadOverview() {
  loading.value = true
  error.value = ''
  try {
    overview.value = await getMonitoringOverview(range.value)
  } catch (reason) {
    error.value = getErrorMessage(reason, t('operations.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function collectNow() {
  collecting.value = true
  try {
    await collectMonitoringMetrics()
    await loadOverview()
    ElMessage.success(t('operations.collected'))
  } catch (reason) {
    notifyError(reason, t('operations.collectFailed'))
  } finally {
    collecting.value = false
  }
}

async function loadSystem() {
  loading.value = true
  error.value = ''
  try {
    systemInfo.value = await getSystemInformation()
  } catch (reason) {
    error.value = getErrorMessage(reason, t('operations.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function loadLogs() {
  loading.value = true
  error.value = ''
  try {
    if (logTab.value === 'online') {
      onlineUsers.value = await getOnlineUsers()
      total.value = onlineUsers.value.users.length
    } else if (logTab.value === 'access') {
      const page = await getAccessLogs({
        page: query.page,
        size: query.size,
        path: query.keyword.trim() || undefined,
        username: query.username.trim() || undefined,
        statusCode: query.statusCode,
        ...timeParams(),
      })
      accessLogs.value = page.items
      total.value = page.total
    } else if (logTab.value === 'login') {
      const page = await getLoginLogs({
        page: query.page,
        size: query.size,
        username: query.username.trim() || undefined,
        ipAddress: query.keyword.trim() || undefined,
        result: query.result,
        ...timeParams(),
      })
      loginLogs.value = page.items
      total.value = page.total
    } else {
      const page = await getAuditLogs({
        page: query.page,
        size: query.size,
        user: query.username.trim() || undefined,
        module: logTab.value === 'workflow' ? 'WORKFLOW' : undefined,
        result: query.result,
        ...timeParams(),
      })
      operationLogs.value = page.items
      total.value = page.total
    }
  } catch (reason) {
    error.value = getErrorMessage(reason, t('operations.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function loadTasks() {
  loading.value = true
  error.value = ''
  try {
    const [summary, page] = await Promise.all([
      getQueueSummary(),
      getMonitoredExports({
        page: query.page,
        size: query.size,
        keyword: query.keyword.trim() || undefined,
        username: query.username.trim() || undefined,
        format: query.format || undefined,
        status: query.status,
        ...timeParams(),
      }),
    ])
    queue.value = summary
    exports.value = page.items
    total.value = page.total
  } catch (reason) {
    error.value = getErrorMessage(reason, t('operations.loadFailed'))
  } finally {
    loading.value = false
  }
}

function loadActive() {
  if (activeTab.value === 'overview') return loadOverview()
  if (activeTab.value === 'system') return loadSystem()
  if (activeTab.value === 'logs') return loadLogs()
  return loadTasks()
}

function search() {
  query.page = 1
  void loadActive()
}

function resetQuery() {
  query.keyword = ''
  query.username = ''
  query.statusCode = undefined
  query.result = undefined
  query.format = ''
  query.status = undefined
  dateRange.value = null
  search()
}

async function confirmAction(message: string, action: () => Promise<unknown>) {
  await ElMessageBox.confirm(message, t('operations.confirmTitle'), {
    type: 'warning',
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
  })
  actionLoading.value = true
  try {
    await action()
    ElMessage.success(t('operations.actionComplete'))
    await loadTasks()
  } catch (reason) {
    notifyError(reason, t('operations.actionFailed'))
  } finally {
    actionLoading.value = false
  }
}

async function toggleQueue() {
  if (!queue.value) return
  actionLoading.value = true
  try {
    queue.value = await setQueuePaused(!queue.value.paused)
    ElMessage.success(queue.value.paused ? t('operations.queuePaused') : t('operations.queueResumed'))
  } catch (reason) {
    notifyError(reason, t('operations.actionFailed'))
  } finally {
    actionLoading.value = false
  }
}

function runTaskAction(task: MonitoredExportTask, action: 'retry' | 'cancel' | 'delete') {
  const actions = {
    retry: () => retryQueueTask(task.id),
    cancel: () => cancelQueueTask(task.id),
    delete: () => deleteQueueTask(task.id),
  }
  void confirmAction(t(`operations.confirm.${action}`, { name: task.name }), actions[action])
}

watch(activeTab, () => {
  query.page = 1
  resetQuery()
})
watch(logTab, () => {
  query.page = 1
  void loadLogs()
})
watch(range, loadOverview)

onMounted(loadOverview)
</script>

<template>
  <section class="operations-page">
    <PageHeader
      :title="t('operations.title')"
      :eyebrow="t('operations.eyebrow')"
      :description="t('operations.description')"
    >
      <template #actions>
        <el-button
          :icon="Refresh"
          :loading="loading"
          @click="loadActive"
        >
          {{ t('common.refresh') }}
        </el-button>
      </template>
    </PageHeader>

    <el-tabs
      v-model="activeTab"
      class="operations-tabs"
    >
      <el-tab-pane
        :label="t('operations.tabs.overview')"
        name="overview"
      />
      <el-tab-pane
        :label="t('operations.tabs.system')"
        name="system"
      />
      <el-tab-pane
        :label="t('operations.tabs.logs')"
        name="logs"
      />
      <el-tab-pane
        :label="t('operations.tabs.queue')"
        name="queue"
      />
      <el-tab-pane
        :label="t('operations.tabs.exports')"
        name="exports"
      />
    </el-tabs>

    <StatePanel
      v-if="loading && !overview && activeTab === 'overview'"
      state="loading"
    />
    <StatePanel
      v-else-if="error && activeTab === 'overview'"
      state="error"
      :title="t('operations.loadFailed')"
      :description="error"
      @retry="loadOverview"
    />

    <div
      v-else-if="activeTab === 'overview' && overview"
      class="overview-layout"
    >
      <div class="metric-strip">
        <article>
          <div class="metric-heading">
            <span>{{ t('operations.databaseLatency') }}</span>
            <span :class="['metric-state', `metric-state--${componentState('DATABASE').toLowerCase()}`]">
              <i />{{ t(`operations.componentStates.${componentState('DATABASE')}`) }}
            </span>
          </div>
          <strong>{{ overview.latest.databaseLatencyMs }}<small>ms</small></strong>
        </article>
        <article>
          <div class="metric-heading">
            <span>{{ t('operations.redisLatency') }}</span>
            <span :class="['metric-state', `metric-state--${componentState('REDIS').toLowerCase()}`]">
              <i />{{ t(`operations.componentStates.${componentState('REDIS')}`) }}
            </span>
          </div>
          <strong v-if="overview.latest.redisLatencyMs != null">
            {{ overview.latest.redisLatencyMs }}<small>ms</small>
          </strong>
          <strong
            v-else
            class="not-configured"
          >{{ t('operations.notConfigured') }}</strong>
        </article>
        <article>
          <div class="metric-heading">
            <span>{{ t('operations.minioLatency') }}</span>
            <span :class="['metric-state', `metric-state--${componentState('MINIO').toLowerCase()}`]">
              <i />{{ t(`operations.componentStates.${componentState('MINIO')}`) }}
            </span>
          </div>
          <strong v-if="componentLatency('MINIO') != null">
            {{ componentLatency('MINIO') }}<small>ms</small>
          </strong>
          <strong
            v-else
            class="not-configured"
          >{{ t('operations.unavailable') }}</strong>
        </article>
        <article>
          <div class="metric-heading">
            <span>{{ t('operations.queueBacklog') }}</span>
            <span :class="['metric-state', `metric-state--${componentState('QUEUE').toLowerCase()}`]">
              <i />{{ t(`operations.componentStates.${componentState('QUEUE')}`) }}
            </span>
          </div>
          <strong>{{ overview.latest.queueBacklog }}<small>{{ t('operations.tasks') }}</small></strong>
        </article>
        <article>
          <div class="metric-heading">
            <span>{{ t('operations.cpuUsage') }}</span>
            <span :class="['metric-state', `metric-state--${componentState('CPU').toLowerCase()}`]">
              <i />{{ t(`operations.componentStates.${componentState('CPU')}`) }}
            </span>
          </div>
          <strong>{{ overview.latest.cpuUsagePercent }}<small>% CPU</small></strong>
        </article>
        <article>
          <div class="metric-heading">
            <span>{{ t('operations.memoryUsage') }}</span>
            <span :class="['metric-state', `metric-state--${componentState('MEMORY').toLowerCase()}`]">
              <i />{{ t(`operations.componentStates.${componentState('MEMORY')}`) }}
            </span>
          </div>
          <strong>{{ overview.latest.memoryUsagePercent }}<small>% MEM</small></strong>
        </article>
      </div>

      <section class="trend-panel">
        <header>
          <div>
            <h2>{{ t('operations.metricTrend') }}</h2>
            <span>{{ formatDateTime(overview.generatedAt) }}</span>
          </div>
          <div class="trend-actions">
            <el-radio-group
              v-model="range"
              size="small"
            >
              <el-radio-button value="1h">
                1H
              </el-radio-button>
              <el-radio-button value="24h">
                24H
              </el-radio-button>
              <el-radio-button value="7d">
                7D
              </el-radio-button>
            </el-radio-group>
            <el-button
              v-permission="'basic:operations:collect'"
              type="primary"
              :icon="Refresh"
              :loading="collecting"
              @click="collectNow"
            >
              {{ t('operations.collectNow') }}
            </el-button>
          </div>
        </header>
        <div class="chart-key">
          <span><i class="key-db" />{{ t('operations.databaseLatency') }}</span>
          <span><i class="key-queue" />{{ t('operations.queueBacklog') }}</span>
          <span><i class="key-cpu" />{{ t('operations.cpuUsage') }}</span>
          <span><i class="key-memory" />{{ t('operations.memoryUsage') }}</span>
        </div>
        <StatePanel
          v-if="!overview.trend.length"
          state="empty"
          :title="t('operations.noMetrics')"
          :description="t('operations.noMetricsDescription')"
        />
        <ChartCanvas
          v-else
          :option="chartOption"
          :height="320"
        />
      </section>
    </div>

    <StatePanel
      v-else-if="loading && activeTab === 'system' && !systemInfo"
      state="loading"
    />
    <div
      v-else-if="activeTab === 'system' && systemInfo"
      class="system-layout"
    >
      <section class="system-facts">
        <header>
          <h2>{{ t('operations.systemInformation') }}</h2>
          <el-tag
            effect="plain"
            type="success"
          >
            {{ systemInfo.environment }}
          </el-tag>
        </header>
        <dl>
          <div><dt>{{ t('operations.version') }}</dt><dd>{{ systemInfo.version }}</dd></div>
          <div><dt>{{ t('operations.uptime') }}</dt><dd>{{ duration(systemInfo.uptimeSeconds) }}</dd></div>
          <div><dt>{{ t('operations.startedAt') }}</dt><dd>{{ formatDateTime(systemInfo.startedAt) }}</dd></div>
          <div>
            <dt>{{ t('operations.hostname') }}</dt><dd class="mono">
              {{ systemInfo.hostname }}
            </dd>
          </div>
          <div><dt>{{ t('operations.os') }}</dt><dd>{{ systemInfo.operatingSystem }}</dd></div>
          <div><dt>{{ t('operations.cpuCores') }}</dt><dd>{{ systemInfo.cpuCores }}</dd></div>
          <div><dt>{{ t('operations.totalMemory') }}</dt><dd>{{ formatBytes(systemInfo.totalMemoryBytes) }}</dd></div>
          <div><dt>{{ t('operations.usedMemory') }}</dt><dd>{{ formatBytes(systemInfo.usedMemoryBytes) }}</dd></div>
        </dl>
      </section>
      <section class="service-list">
        <header>
          <h2>{{ t('operations.serviceStatus') }}</h2>
          <span>{{ systemInfo.applicationName }}</span>
        </header>
        <article
          v-for="service in systemInfo.services"
          :key="service.name"
        >
          <i :class="['service-indicator', `service-indicator--${service.status.toLowerCase()}`]" />
          <div>
            <strong>{{ service.name }}</strong>
            <span>{{ service.detail }}</span>
          </div>
          <div class="service-result">
            <el-tag
              :type="statusType(service.status)"
              effect="plain"
              size="small"
            >
              {{ t(`operations.serviceStates.${service.status}`) }}
            </el-tag>
            <small v-if="service.latencyMs != null">{{ service.latencyMs }} ms</small>
          </div>
        </article>
      </section>
    </div>

    <div
      v-else-if="activeTab === 'logs'"
      class="logs-layout"
    >
      <el-tabs
        v-model="logTab"
        type="border-card"
        class="log-tabs"
      >
        <el-tab-pane
          v-for="tab in ['access', 'online', 'login', 'operation', 'workflow']"
          :key="tab"
          :label="t(`operations.logTabs.${tab}`)"
          :name="tab"
        />
      </el-tabs>
      <form
        v-if="logTab !== 'online'"
        class="filter-bar"
        @submit.prevent="search"
      >
        <el-input
          v-model="query.keyword"
          clearable
          :placeholder="logTab === 'access' ? t('operations.pathOrIp') : t('common.keyword')"
        />
        <el-input
          v-model="query.username"
          clearable
          :placeholder="t('operations.username')"
        />
        <el-select
          v-if="logTab === 'access'"
          v-model="query.statusCode"
          clearable
          :placeholder="t('operations.statusCode')"
        >
          <el-option
            v-for="code in [200, 201, 204, 400, 401, 403, 404, 500]"
            :key="code"
            :label="code"
            :value="code"
          />
        </el-select>
        <el-select
          v-else
          v-model="query.result"
          clearable
          :placeholder="t('operations.result')"
        >
          <el-option
            :label="t('operations.success')"
            value="SUCCESS"
          />
          <el-option
            :label="t('operations.failure')"
            value="FAILURE"
          />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          :start-placeholder="t('operations.startTime')"
          :end-placeholder="t('operations.endTime')"
        />
        <el-button
          type="primary"
          native-type="submit"
        >
          {{ t('common.search') }}
        </el-button>
        <el-button @click="resetQuery">
          {{ t('common.reset') }}
        </el-button>
      </form>

      <div
        v-if="logTab === 'online' && onlineUsers"
        class="online-summary"
      >
        <div><span>{{ t('operations.onlineNow') }}</span><strong>{{ onlineUsers.onlineCount }}</strong></div>
        <div><span>{{ t('operations.activeToday') }}</span><strong>{{ onlineUsers.todayActiveCount }}</strong></div>
      </div>

      <div class="table-shell">
        <el-table
          v-if="logTab === 'access'"
          v-loading="loading"
          :data="accessLogs"
        >
          <el-table-column
            :label="t('operations.time')"
            min-width="165"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.occurredAt) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="method"
            :label="t('operations.method')"
            width="82"
          />
          <el-table-column
            prop="requestPath"
            :label="t('operations.path')"
            min-width="260"
            show-overflow-tooltip
          />
          <el-table-column
            :label="t('operations.statusCode')"
            width="95"
          >
            <template #default="{ row }">
              <el-tag
                :type="row.statusCode >= 400 ? 'danger' : 'success'"
                effect="plain"
                size="small"
              >
                {{ row.statusCode }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="durationMs"
            :label="t('operations.responseTime')"
            width="110"
          >
            <template #default="{ row }">
              {{ row.durationMs }} ms
            </template>
          </el-table-column>
          <el-table-column
            prop="username"
            :label="t('operations.username')"
            min-width="120"
          >
            <template #default="{ row }">
              {{ row.username || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="ipAddress"
            label="IP"
            min-width="135"
          />
        </el-table>
        <el-table
          v-else-if="logTab === 'online'"
          v-loading="loading"
          :data="onlineUsers?.users ?? []"
        >
          <el-table-column
            prop="displayName"
            :label="t('operations.user')"
            min-width="140"
          />
          <el-table-column
            :label="t('operations.roles')"
            min-width="180"
          >
            <template #default="{ row }">
              <el-tag
                v-for="role in row.roles"
                :key="role"
                size="small"
                effect="plain"
              >
                {{ role }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('operations.lastActive')"
            min-width="165"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.activeAt) }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('operations.sessionDuration')"
            width="130"
          >
            <template #default="{ row }">
              {{ duration(row.sessionDurationSeconds) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="currentPath"
            :label="t('operations.currentPath')"
            min-width="240"
            show-overflow-tooltip
          />
          <el-table-column
            prop="ipAddress"
            label="IP"
            min-width="135"
          />
        </el-table>
        <el-table
          v-else-if="logTab === 'login'"
          v-loading="loading"
          :data="loginLogs"
        >
          <el-table-column
            :label="t('operations.time')"
            min-width="165"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.occurredAt) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="username"
            :label="t('operations.username')"
            min-width="140"
          />
          <el-table-column
            prop="ipAddress"
            label="IP"
            min-width="135"
          />
          <el-table-column
            :label="t('operations.result')"
            width="100"
          >
            <template #default="{ row }">
              <el-tag
                :type="statusType(row.result)"
                effect="plain"
                size="small"
              >
                {{ row.result === 'SUCCESS' ? t('operations.success') : t('operations.failure') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="errorSummary"
            :label="t('operations.failureReason')"
            min-width="260"
            show-overflow-tooltip
          />
        </el-table>
        <el-table
          v-else
          v-loading="loading"
          :data="operationLogs"
        >
          <el-table-column
            :label="t('operations.time')"
            min-width="165"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.occurredAt) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="operatorName"
            :label="t('operations.user')"
            min-width="130"
          />
          <el-table-column
            prop="module"
            :label="t('operations.module')"
            min-width="110"
          />
          <el-table-column
            prop="action"
            :label="t('operations.action')"
            min-width="160"
          />
          <el-table-column
            :label="t('operations.target')"
            min-width="180"
          >
            <template #default="{ row }">
              {{ row.targetType || '—' }} <span
                v-if="row.targetId"
                class="mono"
              >#{{ row.targetId }}</span>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('operations.result')"
            width="100"
          >
            <template #default="{ row }">
              <el-tag
                :type="statusType(row.result)"
                effect="plain"
                size="small"
              >
                {{ row.result }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('operations.responseTime')"
            width="110"
          >
            <template #default="{ row }">
              {{ row.durationMs == null ? '—' : `${row.durationMs} ms` }}
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div
        v-if="logTab !== 'online' && pageCount > 1"
        class="pagination-row"
      >
        <el-pagination
          v-model:current-page="query.page"
          :page-size="query.size"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="loadLogs"
        />
      </div>
    </div>

    <div
      v-else-if="activeTab === 'queue' || activeTab === 'exports'"
      class="queue-layout"
    >
      <div
        v-if="activeTab === 'queue' && queue"
        class="queue-status"
      >
        <div
          v-for="status in ['pending', 'processing', 'completed', 'failed']"
          :key="status"
        >
          <span>{{ t(`operations.queueStates.${status}`) }}</span>
          <strong>{{ queue[status as keyof QueueSummary] }}</strong>
        </div>
        <el-tag
          :type="queue.paused ? 'warning' : 'success'"
          effect="plain"
        >
          {{ queue.paused ? t('operations.paused') : t('operations.running') }}
        </el-tag>
      </div>

      <div class="queue-toolbar">
        <form
          class="filter-bar"
          @submit.prevent="search"
        >
          <el-input
            v-model="query.keyword"
            clearable
            :placeholder="t('common.keyword')"
          />
          <el-input
            v-model="query.username"
            clearable
            :placeholder="t('operations.username')"
          />
          <el-select
            v-model="query.status"
            clearable
            :placeholder="t('operations.taskStatus')"
          >
            <el-option
              v-for="status in ['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELED']"
              :key="status"
              :label="t(`operations.exportStates.${status}`)"
              :value="status"
            />
          </el-select>
          <el-button
            type="primary"
            native-type="submit"
          >
            {{ t('common.search') }}
          </el-button>
          <el-button @click="resetQuery">
            {{ t('common.reset') }}
          </el-button>
        </form>
        <div
          v-if="activeTab === 'queue'"
          v-permission="'basic:operations:queue'"
          class="queue-actions"
        >
          <el-button
            :icon="queue?.paused ? VideoPlay : VideoPause"
            :loading="actionLoading"
            @click="toggleQueue"
          >
            {{ queue?.paused ? t('operations.resumeQueue') : t('operations.pauseQueue') }}
          </el-button>
          <el-button
            :icon="RefreshLeft"
            @click="confirmAction(t('operations.confirm.retryAll'), retryAllFailedTasks)"
          >
            {{ t('operations.retryFailed') }}
          </el-button>
          <el-button
            :icon="Delete"
            @click="confirmAction(t('operations.confirm.cleanHistory'), cleanQueueHistory)"
          >
            {{ t('operations.cleanHistory') }}
          </el-button>
          <el-button
            type="danger"
            plain
            :icon="Close"
            @click="confirmAction(t('operations.confirm.clearPending'), clearPendingTasks)"
          >
            {{ t('operations.clearPending') }}
          </el-button>
        </div>
      </div>

      <div class="table-shell">
        <el-table
          v-loading="loading"
          :data="exports"
        >
          <el-table-column
            prop="name"
            :label="t('operations.taskName')"
            min-width="190"
            fixed="left"
          />
          <el-table-column
            prop="format"
            :label="t('operations.format')"
            width="120"
          />
          <el-table-column
            :label="t('operations.taskStatus')"
            width="115"
          >
            <template #default="{ row }">
              <el-tag
                :type="statusType(row.status)"
                effect="plain"
                size="small"
              >
                {{ t(`operations.exportStates.${row.status}`) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('operations.progress')"
            min-width="160"
          >
            <template #default="{ row }">
              <el-progress
                :percentage="row.progress"
                :stroke-width="6"
              />
            </template>
          </el-table-column>
          <el-table-column
            :label="t('operations.datasets')"
            width="95"
          >
            <template #default="{ row }">
              {{ row.processedCount }} / {{ row.datasetCount }}
            </template>
          </el-table-column>
          <el-table-column
            prop="creatorName"
            :label="t('operations.operator')"
            min-width="130"
          />
          <el-table-column
            :label="t('operations.createdAt')"
            min-width="165"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('operations.fileSize')"
            width="110"
          >
            <template #default="{ row }">
              {{ row.fileSize == null ? '—' : formatBytes(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="errorMessage"
            :label="t('operations.errorMessage')"
            min-width="220"
            show-overflow-tooltip
          />
          <el-table-column
            v-if="activeTab === 'queue'"
            :label="t('common.operation')"
            width="148"
            fixed="right"
          >
            <template #default="{ row }">
              <div
                v-permission="'basic:operations:queue'"
                class="table-actions"
              >
                <el-button
                  v-if="row.status === 'FAILED'"
                  link
                  type="primary"
                  @click="runTaskAction(row, 'retry')"
                >
                  {{ t('operations.retry') }}
                </el-button>
                <el-button
                  v-if="row.status === 'PENDING'"
                  link
                  type="warning"
                  @click="runTaskAction(row, 'cancel')"
                >
                  {{ t('operations.cancelTask') }}
                </el-button>
                <el-button
                  v-if="['COMPLETED', 'FAILED', 'CANCELED'].includes(row.status)"
                  link
                  type="danger"
                  @click="runTaskAction(row, 'delete')"
                >
                  {{ t('common.delete') }}
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div
        v-if="pageCount > 1"
        class="pagination-row"
      >
        <el-pagination
          v-model:current-page="query.page"
          :page-size="query.size"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="loadTasks"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.operations-page {
  width: min(1380px, 100%);
  margin: 0 auto;
}

.operations-tabs {
  margin-top: 12px;
}

.operations-tabs :deep(.el-tabs__header) {
  margin-bottom: 22px;
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  border-block: 1px solid var(--color-border);
}

.metric-strip article {
  position: relative;
  min-height: 126px;
  padding: 20px 22px;
  border-right: 1px solid var(--color-border);
}

.metric-strip article:last-child {
  border-right: 0;
}

.metric-strip span {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.metric-strip strong {
  display: block;
  margin-top: 16px;
  color: var(--color-ink);
  font-family: var(--font-mono);
  font-size: 30px;
  font-weight: 520;
  line-height: 1;
}

.metric-strip strong small {
  margin-left: 6px;
  color: var(--color-text-muted);
  font-size: 11px;
  font-weight: 500;
}

.metric-strip .not-configured {
  font-family: var(--font-sans);
  font-size: 15px;
}

.metric-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.metric-strip .metric-state {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  font-size: 11px;
}

.metric-state i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.metric-state--healthy i { background: #2e8b62; box-shadow: 0 0 0 4px #e5f3ec; }
.metric-state--warning i,
.metric-state--paused i { background: #c4871e; box-shadow: 0 0 0 4px #fbf0dc; }
.metric-state--critical i { background: #b34242; box-shadow: 0 0 0 4px #f7e4e4; }
.metric-state--not_configured i { background: #98a5ad; box-shadow: 0 0 0 4px #edf0f2; }

.trend-panel {
  padding-top: 26px;
}

.trend-panel > header,
.system-facts > header,
.service-list > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.trend-panel h2,
.system-layout h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
}

.trend-panel header span,
.service-list header span {
  color: var(--color-text-muted);
  font-size: 11px;
}

.trend-actions,
.queue-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.chart-key {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  padding: 18px 0 2px;
  color: var(--color-text-secondary);
  font-size: 11px;
}

.chart-key span {
  display: flex;
  align-items: center;
  gap: 6px;
}

.chart-key i {
  width: 14px;
  height: 2px;
}

.key-db { background: #12748a; }
.key-queue { background: #6c4ba7; }
.key-cpu { background: #d09a37; }
.key-memory { background: #b34242; }

.system-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(340px, .85fr);
  gap: 36px;
}

.system-facts,
.service-list {
  padding-top: 4px;
}

.system-facts dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 20px 0 0;
  border-top: 1px solid var(--color-border);
}

.system-facts dl div {
  min-height: 78px;
  padding: 16px 0;
  border-bottom: 1px solid var(--color-border);
}

.system-facts dl div:nth-child(odd) {
  padding-right: 28px;
}

.system-facts dt {
  color: var(--color-text-muted);
  font-size: 11px;
}

.system-facts dd {
  margin: 8px 0 0;
  font-size: 14px;
}

.service-list article {
  display: grid;
  grid-template-columns: 10px 1fr auto;
  gap: 12px;
  align-items: center;
  min-height: 82px;
  border-bottom: 1px solid var(--color-border);
}

.service-list header {
  padding-bottom: 7px;
  border-bottom: 1px solid var(--color-border);
}

.service-list article strong,
.service-list article span {
  display: block;
}

.service-list article span {
  margin-top: 5px;
  color: var(--color-text-muted);
  font-size: 11px;
}

.service-indicator {
  width: 7px;
  height: 32px;
  border-radius: 2px;
  background: #98a5ad;
}

.service-indicator--up { background: #2e8b62; }
.service-indicator--down { background: #b34242; }
.service-result {
  text-align: right;
}
.service-result small {
  display: block;
  margin-top: 5px;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
}

.log-tabs {
  border: 0;
  box-shadow: none;
}

.log-tabs :deep(.el-tabs__content) {
  display: none;
}

.online-summary,
.queue-status {
  display: flex;
  align-items: center;
  gap: 0;
  margin: 10px 0 20px;
  border-block: 1px solid var(--color-border);
}

.online-summary div,
.queue-status div {
  min-width: 150px;
  padding: 15px 22px;
  border-right: 1px solid var(--color-border);
}

.online-summary span,
.queue-status span {
  display: block;
  color: var(--color-text-muted);
  font-size: 11px;
}

.online-summary strong,
.queue-status strong {
  display: block;
  margin-top: 5px;
  font-family: var(--font-mono);
  font-size: 20px;
}

.queue-status > .el-tag {
  margin-left: auto;
}

.queue-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.queue-toolbar .filter-bar {
  flex: 1;
}

.table-shell :deep(.el-tag + .el-tag) {
  margin-left: 4px;
}

@media (max-width: 1000px) {
  .metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-strip article:nth-child(2) {
    border-right: 0;
  }

  .metric-strip article {
    border-bottom: 1px solid var(--color-border);
  }

  .metric-strip article:nth-child(even) {
    border-right: 0;
  }

  .metric-strip article:last-child {
    grid-column: 1 / -1;
    border-right: 0;
    border-bottom: 0;
  }

  .system-layout {
    grid-template-columns: 1fr;
  }

  .queue-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 680px) {
  .metric-strip,
  .system-facts dl {
    grid-template-columns: 1fr;
  }

  .metric-strip article {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .metric-strip article:last-child {
    border-bottom: 0;
  }

  .trend-panel > header {
    align-items: stretch;
    flex-direction: column;
  }

  .trend-actions {
    justify-content: space-between;
  }

  .online-summary,
  .queue-status {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .online-summary div,
  .queue-status div {
    min-width: 50%;
  }

  .queue-status > .el-tag {
    margin: 12px;
  }
}
</style>
