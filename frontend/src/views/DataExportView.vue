<script setup lang="ts">
import { Download, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  createExportTask,
  downloadExport,
  getExportDatasets,
  getExportQuota,
  getExportTask,
  getExportTasks,
} from '@/services/data-export'
import { getUserOptions } from '@/services/dataset'
import { getErrorMessage, notifyError } from '@/services/feedback'
import { getProjects } from '@/services/projects'
import type {
  ExportDatasetOption,
  ExportFormat,
  ExportQuota,
  ExportStatus,
  ExportTask,
  ExportTaskDetail,
  ExportTaskPayload,
} from '@/types/data-export'
import type { DatasetOption } from '@/types/dataset'
import type { ProjectSummary } from '@/types/project'
import { fileNameFromDisposition, formatBytes, formatDateTime } from '@/utils/format'

const { t } = useI18n()

const formats: ExportFormat[] = [
  'LEROBOT',
  'HDF5',
  'MCAP',
  'JSON',
  'CSV',
  'YOLO',
  'COCO',
  'VOC',
  'TIME_ALIGNMENT',
  'DROPPED_FRAME',
  'MCAP_CHUNK',
]

const activeFormat = ref<ExportFormat>('LEROBOT')
const quota = ref<ExportQuota>({ used: 0, limit: 0, remaining: 0 })
const projectOptions = ref<ProjectSummary[]>([])
const collectorOptions = ref<DatasetOption[]>([])
const datasets = ref<ExportDatasetOption[]>([])
const selected = ref<ExportDatasetOption[]>([])
const datasetLoading = ref(false)
const datasetError = ref('')

const filters = reactive({
  projectId: null as number | null,
  collectorId: null as number | null,
  dates: [] as string[],
  keyword: '',
})

const config = reactive({
  name: '',
  mediaMode: 'VIDEO' as 'IMAGE' | 'VIDEO',
  sampleRate: 30,
  chunkSize: 1,
  version: 'LATEST' as 'LATEST' | 'V2_1',
  strictMatch: false,
  blurFaces: false,
  annotationType: '',
})

const creating = ref(false)
const tasks = ref<ExportTask[]>([])
const historyLoading = ref(false)
const historyError = ref('')
const historyTotal = ref(0)
const history = reactive({
  page: 1,
  size: 20,
  status: '' as '' | ExportStatus,
  format: '' as '' | ExportFormat,
  keyword: '',
})

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<ExportTaskDetail | null>(null)
let pollTimer: number | undefined

const quotaPercent = computed(() => {
  if (quota.value.limit <= 0) return 100
  return Math.min(100, Math.round((quota.value.used / quota.value.limit) * 100))
})
const selectedSize = computed(() => selected.value.reduce((sum, item) => sum + item.sizeBytes, 0))
const hasActiveTasks = computed(() =>
  tasks.value.some((task) => task.status === 'PENDING' || task.status === 'PROCESSING'),
)
const needsSampling = computed(() =>
  ['LEROBOT', 'HDF5', 'TIME_ALIGNMENT', 'DROPPED_FRAME'].includes(activeFormat.value),
)
const isArchiveFormat = computed(() =>
  !['JSON', 'CSV', 'TIME_ALIGNMENT', 'DROPPED_FRAME'].includes(activeFormat.value),
)

function formatLabel(format: ExportFormat) {
  return t(`dataExport.formats.${format}`)
}

function statusType(status: ExportStatus) {
  return {
    PENDING: 'info',
    PROCESSING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger',
  }[status] as 'info' | 'warning' | 'success' | 'danger'
}

async function loadOptions() {
  try {
    const [projects, users] = await Promise.all([
      getProjects({ page: 1, size: 100 }),
      getUserOptions(),
    ])
    projectOptions.value = projects.items
    collectorOptions.value = users
  } catch (reason) {
    notifyError(reason, t('dataExport.loadOptionsFailed'))
  }
}

async function loadDatasets() {
  datasetLoading.value = true
  datasetError.value = ''
  try {
    datasets.value = await getExportDatasets({
      projectId: filters.projectId ?? undefined,
      collectorId: filters.collectorId ?? undefined,
      from: filters.dates[0] || undefined,
      to: filters.dates[1] || undefined,
      keyword: filters.keyword.trim() || undefined,
    })
    selected.value = []
  } catch (reason) {
    datasetError.value = getErrorMessage(reason, t('dataExport.loadDatasetsFailed'))
  } finally {
    datasetLoading.value = false
  }
}

async function loadQuota() {
  try {
    quota.value = await getExportQuota()
  } catch (reason) {
    notifyError(reason, t('dataExport.loadQuotaFailed'))
  }
}

function refreshAll() {
  void Promise.all([loadDatasets(), loadTasks(), loadQuota()])
}

async function loadTasks(silent = false) {
  if (!silent) historyLoading.value = true
  historyError.value = ''
  try {
    const page = await getExportTasks({
      page: history.page,
      size: history.size,
      format: history.format || undefined,
      status: history.status || undefined,
      keyword: history.keyword.trim() || undefined,
    })
    tasks.value = page.items
    historyTotal.value = page.total
    schedulePolling()
  } catch (reason) {
    historyError.value = getErrorMessage(reason, t('dataExport.loadHistoryFailed'))
  } finally {
    historyLoading.value = false
  }
}

function schedulePolling() {
  if (pollTimer) window.clearTimeout(pollTimer)
  pollTimer = undefined
  if (hasActiveTasks.value) {
    pollTimer = window.setTimeout(async () => {
      await Promise.all([loadTasks(true), loadQuota()])
    }, 2500)
  }
}

function handleSelectionChange(rows: ExportDatasetOption[]) {
  selected.value = rows
}

function resetFilters() {
  filters.projectId = null
  filters.collectorId = null
  filters.dates = []
  filters.keyword = ''
  void loadDatasets()
}

function payload(): ExportTaskPayload {
  const value: ExportTaskPayload = {
    name: config.name.trim(),
    format: activeFormat.value,
    datasetIds: selected.value.map((item) => item.id),
  }
  if (activeFormat.value === 'LEROBOT') {
    value.mediaMode = config.mediaMode
    value.version = config.version
    value.strictMatch = config.strictMatch
    value.blurFaces = config.blurFaces
  }
  if (needsSampling.value) value.sampleRate = config.sampleRate
  if (activeFormat.value === 'HDF5' || activeFormat.value === 'MCAP_CHUNK') {
    value.chunkSize = config.chunkSize
  }
  if (['YOLO', 'COCO', 'VOC'].includes(activeFormat.value) && config.annotationType) {
    value.annotationType = config.annotationType
  }
  return value
}

async function createTask() {
  if (!config.name.trim()) {
    ElMessage.warning(t('dataExport.nameRequired'))
    return
  }
  if (!selected.value.length) {
    ElMessage.warning(t('dataExport.datasetRequired'))
    return
  }
  if (selected.value.length > quota.value.remaining) {
    ElMessage.warning(t('dataExport.quotaInsufficient'))
    return
  }
  creating.value = true
  try {
    await createExportTask(payload())
    ElMessage.success(t('dataExport.created'))
    config.name = ''
    history.page = 1
    await Promise.all([loadTasks(), loadQuota()])
  } catch (reason) {
    notifyError(reason, t('dataExport.createFailed'))
  } finally {
    creating.value = false
  }
}

async function showDetail(task: ExportTask) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getExportTask(task.id)
  } catch (reason) {
    notifyError(reason, t('dataExport.loadDetailFailed'))
  } finally {
    detailLoading.value = false
  }
}

async function download(task: ExportTask) {
  try {
    const response = await downloadExport(task.id)
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = fileNameFromDisposition(response.headers['content-disposition'])
      || task.fileName
      || `export-${task.id}`
    link.click()
    URL.revokeObjectURL(url)
  } catch (reason) {
    notifyError(reason, t('dataExport.downloadFailed'))
  }
}

onMounted(async () => {
  await Promise.all([loadOptions(), loadDatasets(), loadQuota(), loadTasks()])
})

onBeforeUnmount(() => {
  if (pollTimer) window.clearTimeout(pollTimer)
})
</script>

<template>
  <section class="export-page">
    <PageHeader
      :title="t('dataExport.title')"
      :eyebrow="t('dataExport.eyebrow')"
      :description="t('dataExport.description')"
    >
      <template #actions>
        <el-button
          :icon="Refresh"
          @click="refreshAll"
        >
          {{ t('common.refresh') }}
        </el-button>
      </template>
    </PageHeader>

    <div class="quota-band">
      <div>
        <span>{{ t('dataExport.monthlyQuota') }}</span>
        <strong>{{ quota.used }} / {{ quota.limit }}</strong>
      </div>
      <el-progress
        :percentage="quotaPercent"
        :stroke-width="8"
        :show-text="false"
      />
      <small>{{ t('dataExport.quotaRemaining', { count: quota.remaining }) }}</small>
    </div>

    <el-tabs
      v-model="activeFormat"
      class="format-tabs"
    >
      <el-tab-pane
        v-for="format in formats"
        :key="format"
        :name="format"
        :label="formatLabel(format)"
      />
    </el-tabs>

    <div class="export-workspace">
      <section class="configuration">
        <div class="section-heading">
          <div>
            <span>01</span>
            <h2>{{ t('dataExport.configuration') }}</h2>
          </div>
          <el-tag effect="plain">
            {{ formatLabel(activeFormat) }}
          </el-tag>
        </div>

        <el-form label-position="top">
          <el-form-item :label="t('dataExport.taskName')">
            <el-input
              v-model="config.name"
              :maxlength="255"
              :placeholder="t('dataExport.taskNamePlaceholder')"
            />
          </el-form-item>

          <div class="config-grid">
            <el-form-item
              v-if="activeFormat === 'LEROBOT'"
              :label="t('dataExport.mediaMode')"
            >
              <el-segmented
                v-model="config.mediaMode"
                :options="[
                  { label: t('dataExport.video'), value: 'VIDEO' },
                  { label: t('dataExport.image'), value: 'IMAGE' },
                ]"
              />
            </el-form-item>
            <el-form-item
              v-if="needsSampling"
              :label="t('dataExport.sampleRate')"
            >
              <el-input-number
                v-model="config.sampleRate"
                :min="1"
                :max="240"
                controls-position="right"
              />
            </el-form-item>
            <el-form-item
              v-if="activeFormat === 'HDF5' || activeFormat === 'MCAP_CHUNK'"
              :label="t('dataExport.chunkSize')"
            >
              <el-input-number
                v-model="config.chunkSize"
                :min="1"
                :max="100"
                controls-position="right"
              />
            </el-form-item>
            <el-form-item
              v-if="activeFormat === 'LEROBOT'"
              :label="t('dataExport.version')"
            >
              <el-select v-model="config.version">
                <el-option
                  label="latest"
                  value="LATEST"
                />
                <el-option
                  label="v2.1"
                  value="V2_1"
                />
              </el-select>
            </el-form-item>
            <el-form-item
              v-if="['YOLO', 'COCO', 'VOC'].includes(activeFormat)"
              :label="t('dataExport.annotationType')"
            >
              <el-select
                v-model="config.annotationType"
                clearable
                :placeholder="t('dataExport.allAnnotationTypes')"
              >
                <el-option
                  v-for="type in ['BBOX', 'POINT', 'POLYGON', 'POLYLINE', 'KEYPOINT', 'SEGMENTATION']"
                  :key="type"
                  :label="type"
                  :value="type"
                />
              </el-select>
            </el-form-item>
          </div>

          <div
            v-if="activeFormat === 'LEROBOT'"
            class="switch-row"
          >
            <el-checkbox v-model="config.strictMatch">
              {{ t('dataExport.strictMatch') }}
            </el-checkbox>
            <el-checkbox v-model="config.blurFaces">
              {{ t('dataExport.blurFaces') }}
            </el-checkbox>
          </div>
        </el-form>
      </section>

      <section class="dataset-panel">
        <div class="section-heading">
          <div>
            <span>02</span>
            <h2>{{ t('dataExport.selectDatasets') }}</h2>
          </div>
          <strong>{{ t('dataExport.selectedSummary', {
            count: selected.length,
            size: formatBytes(selectedSize),
          }) }}</strong>
        </div>

        <div class="filter-bar">
          <el-select
            v-model="filters.projectId"
            clearable
            filterable
            :placeholder="t('dataExport.allProjects')"
          >
            <el-option
              v-for="project in projectOptions"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
          <el-select
            v-model="filters.collectorId"
            clearable
            filterable
            :placeholder="t('dataExport.allCollectors')"
          >
            <el-option
              v-for="collector in collectorOptions"
              :key="collector.id"
              :label="collector.name"
              :value="collector.id"
            />
          </el-select>
          <el-date-picker
            v-model="filters.dates"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            :start-placeholder="t('dataExport.startTime')"
            :end-placeholder="t('dataExport.endTime')"
          />
          <el-input
            v-model="filters.keyword"
            clearable
            :prefix-icon="Search"
            :placeholder="t('dataExport.searchDataset')"
            @keyup.enter="loadDatasets"
          />
          <el-button
            type="primary"
            @click="loadDatasets"
          >
            {{ t('common.search') }}
          </el-button>
          <el-button @click="resetFilters">
            {{ t('common.reset') }}
          </el-button>
        </div>

        <StatePanel
          v-if="datasetError"
          state="error"
          :title="datasetError"
          @retry="loadDatasets"
        />
        <el-table
          v-else
          v-loading="datasetLoading"
          :data="datasets"
          height="350"
          row-key="id"
          @selection-change="handleSelectionChange"
        >
          <el-table-column
            type="selection"
            width="44"
          />
          <el-table-column
            prop="name"
            :label="t('dataExport.dataset')"
            min-width="210"
            show-overflow-tooltip
          />
          <el-table-column
            prop="projectName"
            :label="t('dataExport.project')"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            prop="dataType"
            :label="t('dataExport.dataType')"
            width="95"
          />
          <el-table-column
            :label="t('dataExport.size')"
            width="100"
          >
            <template #default="{ row }">
              {{ formatBytes(row.sizeBytes) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="collectorName"
            :label="t('dataExport.collector')"
            width="130"
          />
          <el-table-column
            :label="t('dataExport.createdAt')"
            width="170"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <template #empty>
            <span>{{ t('dataExport.noExportableDatasets') }}</span>
          </template>
        </el-table>

        <div class="create-bar">
          <span>{{ isArchiveFormat ? t('dataExport.archiveOutput') : t('dataExport.directOutput') }}</span>
          <el-button
            type="primary"
            :loading="creating"
            :disabled="!selected.length || quota.remaining <= 0"
            @click="createTask"
          >
            {{ t('dataExport.startExport') }}
          </el-button>
        </div>
      </section>
    </div>

    <section class="history-section">
      <div class="section-heading">
        <div>
          <span>03</span>
          <h2>{{ t('dataExport.history') }}</h2>
        </div>
      </div>

      <div class="history-filters">
        <el-input
          v-model="history.keyword"
          clearable
          :prefix-icon="Search"
          :placeholder="t('dataExport.searchTask')"
          @keyup.enter="loadTasks"
        />
        <el-select
          v-model="history.format"
          clearable
          :placeholder="t('dataExport.allFormats')"
        >
          <el-option
            v-for="format in formats"
            :key="format"
            :label="formatLabel(format)"
            :value="format"
          />
        </el-select>
        <el-select
          v-model="history.status"
          clearable
          :placeholder="t('dataExport.allStatuses')"
        >
          <el-option
            v-for="status in ['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED']"
            :key="status"
            :label="t(`dataExport.statuses.${status}`)"
            :value="status"
          />
        </el-select>
        <el-button
          type="primary"
          @click="history.page = 1; loadTasks()"
        >
          {{ t('common.search') }}
        </el-button>
      </div>

      <StatePanel
        v-if="historyError"
        state="error"
        :title="historyError"
        @retry="loadTasks"
      />
      <el-table
        v-else
        v-loading="historyLoading"
        :data="tasks"
      >
        <el-table-column
          prop="name"
          :label="t('dataExport.task')"
          min-width="190"
          show-overflow-tooltip
        />
        <el-table-column
          :label="t('dataExport.format')"
          width="120"
        >
          <template #default="{ row }">
            {{ formatLabel(row.format) }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.status')"
          width="120"
        >
          <template #default="{ row }">
            <el-tag
              :type="statusType(row.status)"
              effect="plain"
            >
              {{ t(`dataExport.statuses.${row.status}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('dataExport.progress')"
          min-width="170"
        >
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress"
              :status="row.status === 'FAILED' ? 'exception' : row.status === 'COMPLETED' ? 'success' : undefined"
            />
          </template>
        </el-table-column>
        <el-table-column
          :label="t('dataExport.datasets')"
          width="100"
        >
          <template #default="{ row }">
            {{ row.processedCount }} / {{ row.datasetCount }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('dataExport.fileSize')"
          width="110"
        >
          <template #default="{ row }">
            {{ row.fileSize == null ? '—' : formatBytes(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="creatorName"
          :label="t('dataExport.operator')"
          width="120"
        />
        <el-table-column
          :label="t('dataExport.createdAt')"
          width="170"
        >
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column
          fixed="right"
          :label="t('common.operation')"
          width="110"
        >
          <template #default="{ row }">
            <el-button
              link
              :icon="View"
              @click="showDetail(row)"
            />
            <el-button
              v-if="row.status === 'COMPLETED'"
              link
              type="primary"
              :icon="Download"
              @click="download(row)"
            />
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="historyTotal > history.size"
        v-model:current-page="history.page"
        v-model:page-size="history.size"
        layout="total, sizes, prev, pager, next"
        :total="historyTotal"
        :page-sizes="[10, 20, 50]"
        @change="loadTasks"
      />
    </section>

    <el-drawer
      v-model="detailOpen"
      :title="t('dataExport.taskDetail')"
      size="min(560px, 94vw)"
    >
      <div
        v-loading="detailLoading"
        class="detail-content"
      >
        <template v-if="detail">
          <dl class="detail-list">
            <div>
              <dt>{{ t('dataExport.task') }}</dt>
              <dd>{{ detail.task.name }}</dd>
            </div>
            <div>
              <dt>{{ t('dataExport.format') }}</dt>
              <dd>{{ formatLabel(detail.task.format) }}</dd>
            </div>
            <div>
              <dt>{{ t('common.status') }}</dt>
              <dd>{{ t(`dataExport.statuses.${detail.task.status}`) }}</dd>
            </div>
            <div>
              <dt>{{ t('dataExport.configuration') }}</dt>
              <dd><code>{{ detail.task.configJson }}</code></dd>
            </div>
          </dl>
          <el-alert
            v-if="detail.task.errorMessage"
            type="error"
            :closable="false"
            :title="detail.task.errorMessage"
          />
          <h3>{{ t('dataExport.includedDatasets') }}</h3>
          <ul class="dataset-list">
            <li
              v-for="item in detail.datasets"
              :key="item.id"
            >
              <span>{{ item.name }}</span>
              <small>{{ item.projectName || '—' }} · {{ formatBytes(item.sizeBytes) }}</small>
            </li>
          </ul>
        </template>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.export-page {
  width: min(1440px, 100%);
  min-width: 0;
  margin: 0 auto;
  overflow-x: clip;
}

.quota-band {
  display: grid;
  grid-template-columns: minmax(180px, 240px) minmax(180px, 1fr) auto;
  align-items: center;
  gap: 18px;
  padding: 18px 0;
  border-bottom: 1px solid var(--color-border);
}

.quota-band > div:first-child {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.quota-band span,
.quota-band small {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.quota-band strong {
  font-family: var(--font-mono);
  font-size: 16px;
}

.format-tabs {
  margin-top: 18px;
}

.format-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.export-workspace {
  display: grid;
  grid-template-columns: minmax(250px, 320px) minmax(0, 1fr);
  min-width: 0;
  border-bottom: 1px solid var(--color-border-strong);
}

.configuration {
  padding: 24px 24px 24px 0;
  border-right: 1px solid var(--color-border);
}

.dataset-panel {
  min-width: 0;
  padding: 24px 0 24px 24px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.section-heading > div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.section-heading span {
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 11px;
}

.section-heading h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 650;
  letter-spacing: 0;
}

.section-heading strong {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 500;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
}

.config-grid :deep(.el-input-number),
.config-grid :deep(.el-select),
.configuration :deep(.el-segmented) {
  width: 100%;
}

.switch-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.filter-bar,
.history-filters {
  display: grid;
  grid-template-columns:
    repeat(2, minmax(100px, 0.8fr))
    minmax(230px, 1.5fr)
    minmax(130px, 1fr)
    auto
    auto;
  gap: 8px;
  margin-bottom: 14px;
}

.filter-bar :deep(.el-date-editor) {
  width: 100%;
}

.create-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 16px;
}

.create-bar span {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.history-section {
  min-width: 0;
  padding-top: 28px;
}

.history-filters {
  grid-template-columns: minmax(200px, 1fr) 180px 160px auto;
  justify-content: start;
  max-width: 850px;
}

.history-section :deep(.el-pagination) {
  justify-content: flex-end;
  margin-top: 18px;
}

.detail-list {
  margin: 0;
}

.detail-list > div {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);
}

.detail-list dt {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.detail-list dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}

.detail-list code {
  font-size: 11px;
}

.detail-content h3 {
  margin: 24px 0 10px;
  font-size: 14px;
  letter-spacing: 0;
}

.dataset-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.dataset-list li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);
}

.dataset-list small {
  flex: 0 0 auto;
  color: var(--color-text-secondary);
}

@media (max-width: 1100px) {
  .export-workspace {
    grid-template-columns: 1fr;
  }

  .configuration {
    padding-right: 0;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .dataset-panel {
    padding-left: 0;
  }

  .filter-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .quota-band {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .config-grid,
  .filter-bar,
  .history-filters {
    grid-template-columns: 1fr;
  }

  .section-heading,
  .create-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .create-bar :deep(.el-button) {
    width: 100%;
  }
}
</style>
