<script setup lang="ts">
import {
  ArrowLeft,
  Connection,
  CopyDocument,
  Delete,
  Edit,
  Grid,
  List,
  Monitor,
  Plus,
  Refresh,
  Search,
  SetUp,
  VideoCamera,
} from '@element-plus/icons-vue'
import type { EChartsOption } from 'echarts'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ChartCanvas from '@/components/ChartCanvas.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  assignDeviceTask,
  createDevice,
  deleteDevices,
  getDevice,
  getDeviceOptions,
  getDevices,
  getInstallCommand,
  updateDevice,
} from '@/services/devices'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'
import type {
  Device,
  DeviceDetail,
  DeviceOptions,
  DevicePayload,
  InstallOptions,
} from '@/types/device'
import { formatBytes, formatDateTime } from '@/utils/format'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const devices = ref<Device[]>([])
const options = ref<DeviceOptions>({ robots: [], projects: [], collectionTasks: [] })
const keyword = ref('')
const projectId = ref<number>()
const selected = ref<Device[]>([])
const viewMode = ref<'table' | 'grid'>('table')
const editorOpen = ref(false)
const editing = ref<Device>()
const saving = ref(false)
const taskOpen = ref(false)
const taskId = ref<number>()
const installOpen = ref(false)
const installDevice = ref<Device>()
const installCommand = ref('')
const installLoading = ref(false)
const detail = ref<DeviceDetail>()
const hours = ref(4)
const activeDetailTab = ref('monitor')
let refreshTimer: number | undefined
let installTimer: number | undefined

const form = reactive<DevicePayload>({
  deviceCode: '',
  remark: '',
  robotId: null,
  projectId: null,
})
const installForm = reactive<InstallOptions>({
  pollIntervalSeconds: 300,
  videoFps: 10,
  rosDomainId: null,
  imageTopic: '',
  maxVideoWidth: null,
  remoteControlEnabled: false,
  connectionPassword: '',
  verbose: false,
})

const agentId = computed(() =>
  typeof route.params.agentId === 'string' ? route.params.agentId : undefined,
)
const canManage = computed(() => auth.hasPermission('basic:device:manage'))
const canRemoteManage = computed(() => auth.hasPermission('basic:device:remote:manage'))
const onlineCount = computed(() => devices.value.filter((item) => item.online).length)
const activatedCount = computed(() => devices.value.filter((item) => item.activated).length)
const trendOption = computed<EChartsOption>(() => {
  const metrics = detail.value?.metrics ?? []
  return {
    color: ['#12748a', '#d09a37', '#6c4ba7'],
    grid: { left: 52, right: 24, top: 28, bottom: 34 },
    tooltip: { trigger: 'axis' },
    legend: {
      top: 0,
      right: 0,
      data: [t('devices.cpu'), t('devices.memory'), t('devices.disk')],
      textStyle: { color: '#64727c' },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: metrics.map((item) => new Date(item.reportedAt).toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
      })),
      axisLabel: { color: '#8a98a2' },
      axisLine: { lineStyle: { color: '#dfe6ea' } },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { formatter: '{value}%', color: '#8a98a2' },
      splitLine: { lineStyle: { color: '#edf1f3' } },
    },
    series: [
      { name: t('devices.cpu'), type: 'line', smooth: true, showSymbol: false, data: metrics.map((item) => item.cpuUsage) },
      { name: t('devices.memory'), type: 'line', smooth: true, showSymbol: false, data: metrics.map((item) => item.memoryUsage) },
      { name: t('devices.disk'), type: 'line', smooth: true, showSymbol: false, data: metrics.map((item) => item.diskUsage) },
    ],
  }
})

function percent(value: number | null) {
  return value == null ? 0 : Number(value.toFixed(1))
}

function uptime(seconds: number) {
  if (!seconds) return '—'
  const days = Math.floor(seconds / 86400)
  const hoursValue = Math.floor((seconds % 86400) / 3600)
  return days ? `${days}d ${hoursValue}h` : `${hoursValue}h`
}

async function loadOptions() {
  try {
    options.value = await getDeviceOptions()
  } catch (reason) {
    notifyError(reason, t('devices.optionsFailed'))
  }
}

async function loadList(silent = false) {
  if (!silent) loading.value = true
  error.value = ''
  try {
    devices.value = await getDevices({
      keyword: keyword.value.trim() || undefined,
      projectId: projectId.value,
    })
  } catch (reason) {
    error.value = getErrorMessage(reason, t('devices.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function loadDetail(silent = false) {
  if (!agentId.value) return
  if (!silent) loading.value = true
  error.value = ''
  try {
    detail.value = await getDevice(agentId.value, hours.value)
  } catch (reason) {
    error.value = getErrorMessage(reason, t('devices.loadFailed'))
  } finally {
    loading.value = false
  }
}

function configureRefresh() {
  if (refreshTimer) window.clearInterval(refreshTimer)
  refreshTimer = window.setInterval(() => {
    if (agentId.value) void loadDetail(true)
    else void loadList(true)
  }, agentId.value ? 10000 : 5000)
}

function openCreate() {
  editing.value = undefined
  Object.assign(form, { deviceCode: '', remark: '', robotId: null, projectId: null })
  editorOpen.value = true
}

function openEdit(device: Device) {
  editing.value = device
  Object.assign(form, {
    deviceCode: device.deviceCode,
    remark: device.remark ?? '',
    robotId: device.robotId,
    projectId: device.projectId,
  })
  editorOpen.value = true
}

async function save() {
  if (!/^[A-Za-z0-9_-]{3,128}$/.test(form.deviceCode.trim())) {
    ElMessage.warning(t('devices.codeValidation'))
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateDevice(editing.value.id, form)
      ElMessage.success(t('devices.updated'))
    } else {
      const registration = await createDevice(form)
      ElMessage.success(t('devices.created'))
      editorOpen.value = false
      await loadList()
      await openInstall(registration.device)
      return
    }
    editorOpen.value = false
    if (agentId.value) await loadDetail()
    else await loadList()
  } catch (reason) {
    notifyError(reason, t('devices.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function removeSelected() {
  if (!selected.value.length) return
  const confirmed = await confirmAction(
    t('devices.deleteConfirm', { count: selected.value.length }),
    t('devices.deleteTitle'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    const result = await deleteDevices(selected.value.map((item) => item.id))
    ElMessage.success(t('devices.batchResult', {
      succeeded: result.succeeded,
      failed: result.failed,
    }))
    selected.value = []
    await loadList()
  } catch (reason) {
    notifyError(reason, t('devices.deleteFailed'))
  }
}

async function submitTask() {
  if (!taskId.value) return
  try {
    const result = await assignDeviceTask(
      selected.value.map((item) => item.id),
      taskId.value,
    )
    ElMessage.success(t('devices.batchResult', {
      succeeded: result.succeeded,
      failed: result.failed,
    }))
    taskOpen.value = false
    await loadList()
  } catch (reason) {
    notifyError(reason, t('devices.assignFailed'))
  }
}

async function openInstall(device: Device) {
  installDevice.value = device
  installCommand.value = ''
  installOpen.value = true
  await generateInstallCommand()
  if (installTimer) window.clearInterval(installTimer)
  installTimer = window.setInterval(async () => {
    const current = devices.value.find((item) => item.id === device.id)
    if (current?.activated) {
      ElMessage.success(t('devices.installed'))
      window.clearInterval(installTimer)
    } else {
      await loadList(true)
    }
  }, 3000)
}

async function generateInstallCommand() {
  if (!installDevice.value) return
  if (installForm.remoteControlEnabled && installForm.connectionPassword.length < 8) {
    ElMessage.warning(t('devices.passwordValidation'))
    return
  }
  installLoading.value = true
  try {
    installCommand.value = (await getInstallCommand(installDevice.value.id, installForm)).command
  } catch (reason) {
    notifyError(reason, t('devices.installFailed'))
  } finally {
    installLoading.value = false
  }
}

async function copyCommand() {
  await navigator.clipboard.writeText(installCommand.value)
  ElMessage.success(t('devices.copied'))
}

function openDetail(device: Device) {
  void router.push(`/basic/devices/${device.agentId}`)
}

watch([agentId, hours], async () => {
  configureRefresh()
  if (agentId.value) await loadDetail()
  else await loadList()
})

watch(installOpen, (open) => {
  if (!open && installTimer) window.clearInterval(installTimer)
})

onMounted(async () => {
  await Promise.all([loadOptions(), agentId.value ? loadDetail() : loadList()])
  configureRefresh()
})

onBeforeUnmount(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
  if (installTimer) window.clearInterval(installTimer)
})
</script>

<template>
  <section class="admin-page devices-page">
    <template v-if="!agentId">
      <PageHeader
        :title="t('devices.title')"
        :eyebrow="t('devices.eyebrow')"
        :description="t('devices.description')"
      >
        <template #actions>
          <el-tooltip :content="t('common.refresh')">
            <el-button
              :icon="Refresh"
              circle
              :loading="loading"
              @click="loadList()"
            />
          </el-tooltip>
          <el-button
            v-if="canRemoteManage"
            type="primary"
            :icon="Plus"
            @click="openCreate"
          >
            {{ t('devices.register') }}
          </el-button>
        </template>
      </PageHeader>

      <div class="device-summary-strip">
        <div><strong>{{ devices.length }}</strong><span>{{ t('devices.registered') }}</span></div>
        <div><strong>{{ activatedCount }}</strong><span>{{ t('devices.activated') }}</span></div>
        <div><strong>{{ onlineCount }}</strong><span>{{ t('devices.online') }}</span></div>
      </div>

      <div class="filter-bar device-toolbar">
        <el-input
          v-model="keyword"
          clearable
          :prefix-icon="Search"
          :placeholder="t('devices.search')"
          @keyup.enter="loadList()"
          @clear="loadList()"
        />
        <el-select
          v-model="projectId"
          clearable
          :placeholder="t('devices.allProjects')"
          @change="loadList()"
        >
          <el-option
            v-for="item in options.projects"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-button
          :icon="Search"
          @click="loadList()"
        >
          {{ t('common.search') }}
        </el-button>
        <div class="toolbar-spacer" />
        <el-button
          v-if="canManage"
          :disabled="!selected.length"
          :icon="SetUp"
          @click="taskOpen = true"
        >
          {{ t('devices.assignTask') }}
        </el-button>
        <el-button
          v-if="canRemoteManage"
          type="danger"
          plain
          :disabled="!selected.length"
          :icon="Delete"
          @click="removeSelected"
        >
          {{ t('common.delete') }}
        </el-button>
        <el-button-group>
          <el-button
            :type="viewMode === 'table' ? 'primary' : 'default'"
            :icon="List"
            @click="viewMode = 'table'"
          />
          <el-button
            :type="viewMode === 'grid' ? 'primary' : 'default'"
            :icon="Grid"
            @click="viewMode = 'grid'"
          />
        </el-button-group>
      </div>

      <StatePanel
        v-if="loading && !devices.length"
        state="loading"
      />
      <StatePanel
        v-else-if="error && !devices.length"
        state="error"
        :title="t('devices.loadFailed')"
        :description="error"
        @retry="loadList()"
      />
      <StatePanel
        v-else-if="!devices.length"
        state="empty"
        :title="t('devices.empty')"
        :description="t('devices.emptyDesc')"
      />

      <div
        v-else
        v-loading="loading"
      >
        <div
          v-if="viewMode === 'table'"
          class="table-shell"
        >
          <el-table
            :data="devices"
            row-key="id"
            @selection-change="selected = $event"
          >
            <el-table-column
              type="selection"
              width="44"
            />
            <el-table-column
              :label="t('devices.device')"
              min-width="180"
            >
              <template #default="{ row }: { row: Device }">
                <button
                  class="device-link"
                  type="button"
                  @click="openDetail(row)"
                >
                  <span
                    class="presence"
                    :class="{ online: row.online }"
                  />
                  <span><strong>{{ row.deviceCode }}</strong><small v-if="!row.activated">{{ t('devices.notActivated') }}</small></span>
                </button>
              </template>
            </el-table-column>
            <el-table-column
              prop="projectName"
              :label="t('devices.project')"
              min-width="130"
            >
              <template #default="{ row }: { row: Device }">
                {{ row.projectName || '—' }}
              </template>
            </el-table-column>
            <el-table-column
              :label="t('devices.system')"
              min-width="180"
            >
              <template #default="{ row }: { row: Device }">
                <div class="system-cell">
                  <strong>{{ row.hostname || '—' }}</strong><span>{{ row.operatingSystem || row.platform || '—' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              :label="t('devices.ip')"
              min-width="140"
            >
              <template #default="{ row }: { row: Device }">
                {{ row.ipAddresses.join(', ') || '—' }}
              </template>
            </el-table-column>
            <el-table-column
              :label="t('devices.cpu')"
              min-width="130"
            >
              <template #default="{ row }: { row: Device }">
                <el-progress
                  :percentage="percent(row.cpuUsage)"
                  :stroke-width="7"
                />
              </template>
            </el-table-column>
            <el-table-column
              :label="t('devices.memory')"
              min-width="130"
            >
              <template #default="{ row }: { row: Device }">
                <el-progress
                  :percentage="percent(row.memoryUsage)"
                  :stroke-width="7"
                />
              </template>
            </el-table-column>
            <el-table-column
              :label="t('devices.lastSeen')"
              min-width="150"
            >
              <template #default="{ row }: { row: Device }">
                {{ formatDateTime(row.lastReportAt) }}
              </template>
            </el-table-column>
            <el-table-column
              :label="t('common.operation')"
              width="132"
              fixed="right"
            >
              <template #default="{ row }: { row: Device }">
                <div class="table-actions">
                  <el-tooltip
                    v-if="!row.activated"
                    :content="t('devices.installAgent')"
                  >
                    <el-button
                      :icon="Connection"
                      link
                      type="primary"
                      @click="openInstall(row)"
                    />
                  </el-tooltip>
                  <el-tooltip
                    v-if="canManage"
                    :content="t('common.edit')"
                  >
                    <el-button
                      :icon="Edit"
                      link
                      @click="openEdit(row)"
                    />
                  </el-tooltip>
                  <el-tooltip :content="t('devices.details')">
                    <el-button
                      :icon="Monitor"
                      link
                      @click="openDetail(row)"
                    />
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div
          v-else
          class="device-grid"
        >
          <article
            v-for="device in devices"
            :key="device.id"
            class="device-card"
            @click="openDetail(device)"
          >
            <div class="device-card__head">
              <Monitor />
              <span
                class="presence"
                :class="{ online: device.online }"
              />
            </div>
            <h2>{{ device.deviceCode }}</h2>
            <p>{{ device.hostname || t('devices.awaitingAgent') }}</p>
            <div class="device-card__metrics">
              <span>CPU <strong>{{ percent(device.cpuUsage) }}%</strong></span>
              <span>MEM <strong>{{ percent(device.memoryUsage) }}%</strong></span>
            </div>
          </article>
        </div>
      </div>
    </template>

    <template v-else>
      <button
        class="back-link"
        type="button"
        @click="router.push('/basic/devices')"
      >
        <el-icon><ArrowLeft /></el-icon>{{ t('devices.back') }}
      </button>
      <StatePanel
        v-if="loading && !detail"
        state="loading"
      />
      <StatePanel
        v-else-if="error && !detail"
        state="error"
        :title="t('devices.loadFailed')"
        :description="error"
        @retry="loadDetail()"
      />
      <template v-else-if="detail">
        <PageHeader
          :title="detail.device.deviceCode"
          :eyebrow="detail.device.online ? t('devices.online') : t('devices.offline')"
          :description="detail.device.remark || detail.device.agentId"
        >
          <template #actions>
            <el-button
              v-if="canManage"
              :icon="Edit"
              @click="openEdit(detail.device)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              type="primary"
              :icon="Connection"
              @click="openInstall(detail.device)"
            >
              {{ t('devices.installAgent') }}
            </el-button>
          </template>
        </PageHeader>

        <div class="device-detail-layout">
          <aside class="device-sidebar">
            <div class="video-stage">
              <VideoCamera />
              <strong>{{ t('devices.video') }}</strong>
              <span>{{ detail.device.online ? t('devices.streamWaiting') : t('devices.deviceOffline') }}</span>
            </div>
            <dl class="system-list">
              <div><dt>{{ t('devices.operatingSystem') }}</dt><dd>{{ detail.device.operatingSystem || '—' }}</dd></div>
              <div><dt>{{ t('devices.hostname') }}</dt><dd>{{ detail.device.hostname || '—' }}</dd></div>
              <div><dt>{{ t('devices.kernel') }}</dt><dd>{{ detail.device.kernelVersion || '—' }}</dd></div>
              <div><dt>{{ t('devices.uptime') }}</dt><dd>{{ uptime(detail.device.uptimeSeconds) }}</dd></div>
            </dl>
          </aside>

          <main class="device-detail-main">
            <el-tabs v-model="activeDetailTab">
              <el-tab-pane
                :label="t('devices.monitoring')"
                name="monitor"
              >
                <div class="metric-grid">
                  <article><span>{{ t('devices.cpu') }}</span><strong>{{ percent(detail.device.cpuUsage) }}%</strong><small>{{ detail.device.cpuTemperature ?? '—' }} °C</small></article>
                  <article><span>{{ t('devices.memory') }}</span><strong>{{ percent(detail.device.memoryUsage) }}%</strong><small>{{ formatBytes(detail.device.memoryUsedBytes ?? 0) }}</small></article>
                  <article><span>{{ t('devices.disk') }}</span><strong>{{ percent(detail.device.diskUsage) }}%</strong><small>{{ formatBytes(detail.device.diskAvailableBytes ?? 0) }} {{ t('devices.available') }}</small></article>
                  <article><span>{{ t('devices.network') }}</span><strong>{{ detail.device.activeTcpConnections }}</strong><small>TCP</small></article>
                </div>
                <section class="trend-panel">
                  <header>
                    <div><h2>{{ t('devices.resourceTrend') }}</h2><span>{{ t('devices.realReports') }}</span></div>
                    <el-segmented
                      v-model="hours"
                      :options="[{ label: '4h', value: 4 }, { label: '1d', value: 24 }, { label: '7d', value: 168 }]"
                    />
                  </header>
                  <ChartCanvas
                    v-if="detail.metrics.length"
                    :option="trendOption"
                    :height="310"
                  />
                  <StatePanel
                    v-else
                    state="empty"
                    :title="t('devices.noMetrics')"
                    :description="t('devices.noMetricsDesc')"
                  />
                </section>
              </el-tab-pane>
              <el-tab-pane
                :label="t('devices.jointState')"
                name="states"
              >
                <StatePanel
                  state="empty"
                  :title="t('devices.noJointState')"
                  :description="t('devices.noJointStateDesc')"
                />
              </el-tab-pane>
              <el-tab-pane
                :label="t('devices.remoteControl')"
                name="control"
              >
                <div class="terminal-state">
                  <Connection />
                  <strong>{{ detail.device.online ? t('devices.channelPending') : t('devices.deviceOffline') }}</strong>
                  <span>{{ t('devices.remoteControlDesc') }}</span>
                </div>
              </el-tab-pane>
            </el-tabs>
          </main>
        </div>
      </template>
    </template>

    <el-dialog
      v-model="editorOpen"
      :title="editing ? t('devices.edit') : t('devices.register')"
      width="560px"
    >
      <el-form
        label-position="top"
        class="dialog-form"
        @submit.prevent="save"
      >
        <el-form-item
          :label="t('devices.deviceCode')"
          required
        >
          <el-input
            v-model="form.deviceCode"
            maxlength="128"
            :disabled="Boolean(editing)"
          />
        </el-form-item>
        <el-form-item :label="t('devices.remark')">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <div class="two-column-form">
          <el-form-item :label="t('devices.robot')">
            <el-select
              v-model="form.robotId"
              clearable
            >
              <el-option
                v-for="item in options.robots"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('devices.project')">
            <el-select
              v-model="form.projectId"
              clearable
            >
              <el-option
                v-for="item in options.projects"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editorOpen = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="save"
        >
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="installOpen"
      :title="t('devices.installAgent')"
      width="720px"
    >
      <div class="install-status">
        <span
          class="presence"
          :class="{ online: installDevice?.activated }"
        />
        <div><strong>{{ installDevice?.activated ? t('devices.installed') : t('devices.waitingInstall') }}</strong><span>{{ installDevice?.deviceCode }}</span></div>
      </div>
      <el-form
        label-position="top"
        class="install-form"
      >
        <div class="install-grid">
          <el-form-item :label="t('devices.pollInterval')">
            <el-input-number
              v-model="installForm.pollIntervalSeconds"
              :min="1"
            />
          </el-form-item>
          <el-form-item :label="t('devices.videoFps')">
            <el-input-number
              v-model="installForm.videoFps"
              :min="1"
            />
          </el-form-item>
          <el-form-item label="ROS_DOMAIN_ID">
            <el-input-number
              v-model="installForm.rosDomainId"
              :min="0"
              :max="232"
            />
          </el-form-item>
          <el-form-item :label="t('devices.maxVideoWidth')">
            <el-input-number
              v-model="installForm.maxVideoWidth"
              :min="1"
            />
          </el-form-item>
        </div>
        <el-form-item :label="t('devices.imageTopic')">
          <el-input
            v-model="installForm.imageTopic"
            placeholder="/camera/image_raw"
          />
        </el-form-item>
        <el-form-item>
          <el-switch
            v-model="installForm.remoteControlEnabled"
            :active-text="t('devices.enableRemote')"
          />
        </el-form-item>
        <el-form-item
          v-if="installForm.remoteControlEnabled"
          :label="t('devices.connectionPassword')"
          required
        >
          <el-input
            v-model="installForm.connectionPassword"
            type="password"
            show-password
            maxlength="128"
          />
        </el-form-item>
        <el-form-item>
          <el-switch
            v-model="installForm.verbose"
            :active-text="t('devices.verbose')"
          />
        </el-form-item>
      </el-form>
      <div class="command-box">
        <code>{{ installCommand || t('devices.generatingCommand') }}</code>
        <el-tooltip :content="t('devices.copyCommand')">
          <el-button
            :icon="CopyDocument"
            circle
            :disabled="!installCommand"
            @click="copyCommand"
          />
        </el-tooltip>
      </div>
      <template #footer>
        <el-button @click="installOpen = false">
          {{ t('common.close') }}
        </el-button>
        <el-button
          type="primary"
          :loading="installLoading"
          @click="generateInstallCommand"
        >
          {{ t('devices.generateCommand') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="taskOpen"
      :title="t('devices.assignTask')"
      width="460px"
    >
      <el-select
        v-model="taskId"
        class="full-width"
        :placeholder="t('devices.selectTask')"
      >
        <el-option
          v-for="item in options.collectionTasks"
          :key="item.id"
          :label="item.name"
          :value="item.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="taskOpen = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :disabled="!taskId"
          @click="submitTask"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.devices-page { max-width: 1440px; }
.device-summary-strip { display: flex; gap: 32px; padding: 18px 0 4px; }
.device-summary-strip div { display: flex; align-items: baseline; gap: 8px; }
.device-summary-strip strong { font-size: 24px; font-weight: 650; }
.device-summary-strip span { color: var(--color-text-muted); font-size: 12px; }
.device-toolbar { border-bottom: 1px solid var(--color-border); }
.device-toolbar .toolbar-spacer { flex: 1; }
.device-link { display: flex; align-items: center; gap: 9px; padding: 0; cursor: pointer; border: 0; color: inherit; text-align: left; background: none; }
.device-link span:last-child, .system-cell { display: flex; flex-direction: column; gap: 2px; }
.device-link strong, .system-cell strong { font-size: 13px; font-weight: 600; }
.device-link small { width: fit-content; padding: 1px 5px; border: 1px solid #e0b1a7; border-radius: 3px; color: #a44b3c; font-size: 10px; }
.system-cell span { color: var(--color-text-muted); font-size: 11px; }
.presence { width: 8px; height: 8px; flex: 0 0 auto; border-radius: 50%; background: #bdc6cc; }
.presence.online { background: #30a46c; box-shadow: 0 0 0 3px rgb(48 164 108 / 12%); }
.device-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 12px; padding-top: 16px; }
.device-card { min-height: 170px; padding: 18px; cursor: pointer; border: 1px solid var(--color-border); border-radius: 6px; background: #fff; transition: border-color 140ms ease, transform 140ms ease; }
.device-card:hover { border-color: var(--color-accent-soft); transform: translateY(-2px); }
.device-card__head { display: flex; align-items: center; justify-content: space-between; }
.device-card__head svg { width: 28px; color: var(--color-accent); }
.device-card h2 { margin: 24px 0 5px; font-size: 16px; }
.device-card p { min-height: 20px; margin: 0; color: var(--color-text-muted); font-size: 12px; }
.device-card__metrics { display: flex; gap: 20px; margin-top: 18px; color: var(--color-text-muted); font: 11px var(--font-mono); }
.device-card__metrics strong { color: var(--color-text-primary); }
.back-link { display: inline-flex; align-items: center; gap: 5px; margin-bottom: 16px; padding: 0; cursor: pointer; border: 0; color: var(--color-text-secondary); background: none; }
.device-detail-layout { display: grid; grid-template-columns: minmax(260px, 330px) minmax(0, 1fr); gap: 24px; padding-top: 22px; }
.device-sidebar { border-right: 1px solid var(--color-border); padding-right: 24px; }
.video-stage { display: grid; min-height: 230px; place-content: center; place-items: center; gap: 8px; border-radius: 6px; color: #b8c4cb; background: #10191f; text-align: center; }
.video-stage svg { width: 42px; }
.video-stage strong { color: #dce5ea; font-size: 13px; }
.video-stage span { max-width: 220px; font-size: 11px; }
.system-list { margin: 18px 0 0; }
.system-list div { display: grid; grid-template-columns: 110px 1fr; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--color-border); }
.system-list dt { color: var(--color-text-muted); font-size: 11px; }
.system-list dd { overflow-wrap: anywhere; margin: 0; font-size: 12px; }
.device-detail-main { min-width: 0; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; padding-top: 4px; }
.metric-grid article { display: flex; min-height: 116px; flex-direction: column; padding: 16px; border: 1px solid var(--color-border); border-radius: 6px; background: #fff; }
.metric-grid span { color: var(--color-text-secondary); font-size: 11px; }
.metric-grid strong { margin-top: 13px; font: 650 25px var(--font-mono); }
.metric-grid small { margin-top: auto; color: var(--color-text-muted); }
.trend-panel { margin-top: 14px; border-top: 1px solid var(--color-border); padding-top: 16px; }
.trend-panel > header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.trend-panel h2 { margin: 0; font-size: 15px; }
.trend-panel header span { color: var(--color-text-muted); font-size: 11px; }
.terminal-state { display: grid; min-height: 340px; place-content: center; justify-items: center; gap: 10px; border: 1px dashed var(--color-border-strong); color: var(--color-text-muted); background: #f9fbfc; text-align: center; }
.terminal-state svg { width: 36px; color: var(--color-accent); }
.terminal-state strong { color: var(--color-text-primary); }
.install-status { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; padding: 12px; background: #f3f7f8; }
.install-status div { display: flex; flex-direction: column; gap: 2px; }
.install-status span { color: var(--color-text-muted); font-size: 11px; }
.install-grid, .two-column-form { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.command-box { display: flex; align-items: center; gap: 10px; padding: 13px; border: 1px solid var(--color-border); border-radius: 5px; background: #152027; }
.command-box code { flex: 1; overflow-wrap: anywhere; color: #c9e2d3; font-size: 11px; line-height: 1.6; }
.full-width { width: 100%; }
@media (max-width: 980px) {
  .device-detail-layout { grid-template-columns: 1fr; }
  .device-sidebar { display: grid; grid-template-columns: minmax(240px, 1fr) minmax(220px, .8fr); gap: 20px; border-right: 0; padding-right: 0; }
  .metric-grid { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 700px) {
  .device-summary-strip { justify-content: space-between; gap: 8px; }
  .device-toolbar .toolbar-spacer { display: none; }
  .device-sidebar, .metric-grid, .install-grid, .two-column-form { grid-template-columns: 1fr; }
  .device-toolbar .el-button-group { width: 100%; }
  .device-toolbar .el-button-group .el-button { width: 50%; }
}
</style>
