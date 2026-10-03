<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import {
  Delete,
  EditPen,
  Files,
  Histogram,
  Operation,
  PriceTag,
  Refresh,
  RefreshRight,
  Search,
  Switch,
  VideoPlay,
} from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { getProjects } from '@/services/projects'
import {
  addDatasetTags,
  assignDatasetRobot,
  createAnnotationTask,
  deleteDatasets,
  getDataset,
  getDatasetStats,
  getDatasetStorageTotal,
  getDatasets,
  getRobotOptions,
  getTagOptions,
  getTrashDatasets,
  getUserOptions,
  importDatasets,
  refreshDatasets,
  removeDatasetTags,
  renameDatasets,
  restoreDataset,
} from '@/services/dataset'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import type { ProjectSummary } from '@/types/project'
import type {
  DatasetDetail,
  DatasetOption,
  DatasetScope,
  DatasetStats,
  DatasetView,
} from '@/types/dataset'
import { formatBytes, formatDateTime } from '@/utils/format'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

interface FilterState {
  scope: DatasetScope
  projectIds: number[]
  name: string
  robotCode: string
  tag: string
  uploaderId: number | null
  collectorIds: number[]
  sourceTaskCode: string
  minDuration: number | null
  maxDuration: number | null
  annotationText: string
  sortDir: 'ASC' | 'DESC'
  page: number
  size: number
}

const filters = reactive<FilterState>({
  scope: 'ALL',
  projectIds: [],
  name: '',
  robotCode: '',
  tag: '',
  uploaderId: null,
  collectorIds: [],
  sourceTaskCode: '',
  minDuration: null,
  maxDuration: null,
  annotationText: '',
  sortDir: 'DESC',
  page: 1,
  size: 20,
})

const loading = ref(false)
const error = ref('')
const rows = ref<DatasetView[]>([])
const total = ref(0)
const storageTotal = ref(0)
const selected = ref<DatasetView[]>([])
const tableRef = ref<{ clearSelection: () => void }>()

const projectOptions = ref<ProjectSummary[]>([])
const robotOptions = ref<string[]>([])
const tagOptions = ref<string[]>([])
const userOptions = ref<DatasetOption[]>([])

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<DatasetDetail | null>(null)
const playbackRate = ref(1)

const trashOpen = ref(false)
const trashItems = ref<DatasetView[]>([])

const renameOpen = ref(false)
const renameRows = ref<{ id: number; originalName: string; name: string }[]>([])

const statsOpen = ref(false)
const statsData = ref<DatasetStats | null>(null)

const annotateOpen = ref(false)
const annotateName = ref('')

const tagDialogOpen = ref(false)
const tagMode = ref<'add' | 'remove'>('add')
const tagSelection = ref<string[]>([])

const importOpen = ref(false)
const importProjectId = ref<number | null>(null)

const robotOpen = ref(false)
const robotValue = ref('')

function parseIds(value: unknown): number[] {
  if (typeof value !== 'string' || !value.trim()) return []
  return value
    .split(',')
    .map((part) => Number(part.trim()))
    .filter((value) => Number.isFinite(value) && value > 0)
}

function numberOrNull(value: unknown): number | null {
  if (typeof value !== 'string' || !value.trim()) return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function textOrEmpty(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function readRoute() {
  const scope = textOrEmpty(route.query.scope).toUpperCase()
  filters.scope = scope === 'PROJECT' || scope === 'PERSONAL' ? (scope as DatasetScope) : 'ALL'
  filters.projectIds = parseIds(route.query.projectIds)
  filters.name = textOrEmpty(route.query.name)
  filters.robotCode = textOrEmpty(route.query.robot ?? route.query.robotCode)
  filters.tag = textOrEmpty(route.query.tag)
  filters.uploaderId = numberOrNull(route.query.uploaderId)
  filters.collectorIds = parseIds(route.query.collectorIds)
  filters.sourceTaskCode = textOrEmpty(route.query.sourceTaskCode)
  filters.minDuration = numberOrNull(route.query.minDuration)
  filters.maxDuration = numberOrNull(route.query.maxDuration)
  filters.annotationText = textOrEmpty(route.query.annotationText)
  filters.sortDir = textOrEmpty(route.query.sortDir).toUpperCase() === 'ASC' ? 'ASC' : 'DESC'
  filters.page = numberOrNull(route.query.page) ?? 1
  const size = numberOrNull(route.query.size)
  filters.size = size && [10, 20, 50, 100].includes(size) ? size : 20
}

function buildQuery() {
  return {
    page: filters.page,
    size: filters.size,
    scope: filters.scope,
    projectIds: filters.projectIds.length ? filters.projectIds.join(',') : undefined,
    name: filters.name.trim() || undefined,
    robotCode: filters.robotCode.trim() || undefined,
    tag: filters.tag.trim() || undefined,
    uploaderId: filters.uploaderId ?? undefined,
    collectorIds: filters.collectorIds.length ? filters.collectorIds.join(',') : undefined,
    sourceTaskCode: filters.sourceTaskCode.trim() || undefined,
    minDuration: filters.minDuration ?? undefined,
    maxDuration: filters.maxDuration ?? undefined,
    annotationText: filters.annotationText.trim() || undefined,
    sortDir: filters.sortDir,
  }
}

function syncRoute() {
  const query: Record<string, string> = {}
  if (filters.scope !== 'ALL') query.scope = filters.scope
  if (filters.projectIds.length) query.projectIds = filters.projectIds.join(',')
  if (filters.name.trim()) query.name = filters.name.trim()
  if (filters.robotCode.trim()) query.robot = filters.robotCode.trim()
  if (filters.tag.trim()) query.tag = filters.tag.trim()
  if (filters.uploaderId != null) query.uploaderId = String(filters.uploaderId)
  if (filters.collectorIds.length) query.collectorIds = filters.collectorIds.join(',')
  if (filters.sourceTaskCode.trim()) query.sourceTaskCode = filters.sourceTaskCode.trim()
  if (filters.minDuration != null) query.minDuration = String(filters.minDuration)
  if (filters.maxDuration != null) query.maxDuration = String(filters.maxDuration)
  if (filters.annotationText.trim()) query.annotationText = filters.annotationText.trim()
  if (filters.sortDir !== 'DESC') query.sortDir = filters.sortDir
  if (filters.page !== 1) query.page = String(filters.page)
  if (filters.size !== 20) query.size = String(filters.size)
  void router.replace({ query })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const query = buildQuery()
    const [page, totalSize] = await Promise.all([
      getDatasets(query),
      getDatasetStorageTotal(query),
    ])
    rows.value = page.items
    total.value = page.total
    storageTotal.value = totalSize
    syncRoute()
  } catch (reason) {
    error.value = getErrorMessage(reason, t('dataset.loadFailed'))
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  filters.page = 1
  void load()
}

function resetFilters() {
  Object.assign(filters, {
    projectIds: [],
    name: '',
    robotCode: '',
    tag: '',
    uploaderId: null,
    collectorIds: [],
    sourceTaskCode: '',
    minDuration: null,
    maxDuration: null,
    annotationText: '',
    page: 1,
  })
  void load()
}

function handleSortChange(event: { prop: string; order: 'ascending' | 'descending' | null }) {
  if (event.prop !== 'createdAt') return
  filters.sortDir = event.order === 'ascending' ? 'ASC' : 'DESC'
  void load()
}

function handlePageChange() {
  void load()
}

function handleSelectionChange(selection: DatasetView[]) {
  selected.value = selection
}

const selectedSize = computed(() =>
  selected.value.reduce((sum, item) => sum + item.sizeBytes, 0),
)

function projectName(id: number) {
  return projectOptions.value.find((item) => item.id === id)?.name ?? `#${id}`
}

function userName(id: number | null) {
  if (id == null) return ''
  return userOptions.value.find((item) => item.id === id)?.name ?? `#${id}`
}

interface Chip {
  key: string
  label: string
  remove: () => void
}

const chips = computed<Chip[]>(() => {
  const list: Chip[] = []
  filters.projectIds.forEach((id) => {
    list.push({
      key: `project-${id}`,
      label: `${t('dataset.fields.project')}: ${projectName(id)}`,
      remove: () => {
        filters.projectIds = filters.projectIds.filter((value) => value !== id)
        applyFilters()
      },
    })
  })
  if (filters.robotCode.trim()) {
    list.push({
      key: 'robot',
      label: `${t('dataset.fields.robot')}: ${filters.robotCode}`,
      remove: () => {
        filters.robotCode = ''
        applyFilters()
      },
    })
  }
  if (filters.tag.trim()) {
    list.push({
      key: 'tag',
      label: `${t('dataset.fields.tag')}: ${filters.tag}`,
      remove: () => {
        filters.tag = ''
        applyFilters()
      },
    })
  }
  if (filters.uploaderId != null) {
    list.push({
      key: 'uploader',
      label: `${t('dataset.fields.uploader')}: ${userName(filters.uploaderId)}`,
      remove: () => {
        filters.uploaderId = null
        applyFilters()
      },
    })
  }
  filters.collectorIds.forEach((id) => {
    list.push({
      key: `collector-${id}`,
      label: `${t('dataset.fields.collector')}: ${userName(id)}`,
      remove: () => {
        filters.collectorIds = filters.collectorIds.filter((value) => value !== id)
        applyFilters()
      },
    })
  })
  if (filters.minDuration != null || filters.maxDuration != null) {
    list.push({
      key: 'duration',
      label: `${t('dataset.fields.duration')}: ${filters.minDuration ?? 0}–${filters.maxDuration ?? '∞'}s`,
      remove: () => {
        filters.minDuration = null
        filters.maxDuration = null
        applyFilters()
      },
    })
  }
  if (filters.sourceTaskCode.trim()) {
    list.push({
      key: 'sourceTask',
      label: `${t('dataset.fields.sourceTask')}: ${filters.sourceTaskCode}`,
      remove: () => {
        filters.sourceTaskCode = ''
        applyFilters()
      },
    })
  }
  return list
})

function formatDuration(value: number | null) {
  if (value == null || !Number.isFinite(value)) return '—'
  const totalSeconds = Math.round(value)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const mm = String(minutes).padStart(2, '0')
  const ss = String(seconds).padStart(2, '0')
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`
}

async function openDetail(row: DatasetView) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  playbackRate.value = 1
  try {
    detail.value = await getDataset(row.id)
  } catch (reason) {
    detailOpen.value = false
    notifyError(reason, t('dataset.detailFailed'))
  } finally {
    detailLoading.value = false
  }
}

type MediaKind = 'video' | 'audio' | 'image' | 'mcap' | 'other'

function mediaKind(dataType: string): MediaKind {
  const value = dataType.toUpperCase()
  if (value.includes('VIDEO')) return 'video'
  if (value.includes('AUDIO')) return 'audio'
  if (value.includes('IMAGE')) return 'image'
  if (value.includes('MCAP')) return 'mcap'
  return 'other'
}

const detailKind = computed<MediaKind>(() =>
  detail.value ? mediaKind(detail.value.dataset.dataType) : 'other',
)

function clearSelection() {
  tableRef.value?.clearSelection()
}

async function openRename() {
  renameRows.value = selected.value.map((item) => ({
    id: item.id,
    originalName: item.name,
    name: item.name,
  }))
  renameOpen.value = true
}

async function submitRename() {
  const items = renameRows.value
    .filter((row) => row.name.trim() && row.name.trim() !== row.originalName)
    .map((row) => ({ id: row.id, name: row.name.trim() }))
  if (!items.length) {
    renameOpen.value = false
    return
  }
  try {
    const results = await renameDatasets(items)
    const failed = results.filter((result) => result.status !== 'READY')
    if (failed.length) {
      notifyError(new Error(failed.map((item) => item.message).join('；')), t('dataset.renameFailed'))
    } else {
      ElMessage.success(t('dataset.renameDone'))
    }
    renameOpen.value = false
    clearSelection()
    await load()
  } catch (reason) {
    notifyError(reason, t('dataset.renameFailed'))
  }
}

async function openStats() {
  try {
    statsData.value = await getDatasetStats(selected.value.map((item) => item.id))
    statsOpen.value = true
  } catch (reason) {
    notifyError(reason, t('dataset.statsFailed'))
  }
}

function openAnnotate() {
  annotateName.value = selected.value[0]?.name
    ? `${selected.value[0].name}-${new Date().toISOString().slice(0, 10)}`
    : ''
  annotateOpen.value = true
}

async function submitAnnotate() {
  if (!annotateName.value.trim()) return
  try {
    await createAnnotationTask(
      selected.value.map((item) => item.id),
      annotateName.value.trim(),
    )
    ElMessage.success(t('dataset.annotateDone'))
    annotateOpen.value = false
    clearSelection()
    await load()
  } catch (reason) {
    notifyError(reason, t('dataset.annotateFailed'))
  }
}

function openTags(mode: 'add' | 'remove') {
  tagMode.value = mode
  tagSelection.value = []
  tagDialogOpen.value = true
}

async function submitTags() {
  const ids = selected.value.map((item) => item.id)
  const tags = tagSelection.value.map((item) => item.trim()).filter(Boolean)
  if (!tags.length) {
    tagDialogOpen.value = false
    return
  }
  try {
    if (tagMode.value === 'add') {
      await addDatasetTags(ids, tags)
    } else {
      await removeDatasetTags(ids, tags)
    }
    ElMessage.success(t('dataset.tagsDone'))
    tagDialogOpen.value = false
    clearSelection()
    await load()
  } catch (reason) {
    notifyError(reason, t('dataset.tagsFailed'))
  }
}

async function runRefresh() {
  const confirmed = await confirmAction(t('dataset.refreshConfirm'), t('dataset.refreshTitle'))
  if (!confirmed) return
  try {
    const results = await refreshDatasets(selected.value.map((item) => item.id))
    const failed = results.filter((result) => result.status === 'FAILED')
    if (failed.length) {
      notifyError(new Error(failed.map((item) => item.message).join('；')), t('dataset.refreshFailed'))
    } else {
      ElMessage.success(t('dataset.refreshDone'))
    }
    clearSelection()
    await load()
  } catch (reason) {
    notifyError(reason, t('dataset.refreshFailed'))
  }
}

async function runDelete() {
  const confirmed = await confirmAction(t('dataset.deleteConfirm'), t('dataset.deleteTitle'), t('common.delete'))
  if (!confirmed) return
  try {
    await deleteDatasets(selected.value.map((item) => item.id))
    ElMessage.success(t('dataset.deleteDone'))
    clearSelection()
    await load()
  } catch (reason) {
    notifyError(reason, t('dataset.deleteFailed'))
  }
}

function openImport() {
  importProjectId.value = null
  importOpen.value = true
}

async function submitImport() {
  if (importProjectId.value == null) return
  try {
    await importDatasets(selected.value.map((item) => item.id), importProjectId.value)
    ElMessage.success(t('dataset.importDone'))
    importOpen.value = false
    clearSelection()
    await load()
  } catch (reason) {
    notifyError(reason, t('dataset.importFailed'))
  }
}

function openRobot() {
  robotValue.value = selected.value[0]?.robotCode ?? ''
  robotOpen.value = true
}

async function submitRobot() {
  try {
    await assignDatasetRobot(
      selected.value.map((item) => item.id),
      robotValue.value.trim() || null,
    )
    ElMessage.success(t('dataset.robotDone'))
    robotOpen.value = false
    clearSelection()
    await load()
  } catch (reason) {
    notifyError(reason, t('dataset.robotFailed'))
  }
}

async function openTrash() {
  trashOpen.value = true
  await loadTrash()
}

async function loadTrash() {
  try {
    trashItems.value = await getTrashDatasets()
  } catch (reason) {
    notifyError(reason, t('dataset.trashFailed'))
  }
}

async function runRestore(item: DatasetView) {
  try {
    await restoreDataset(item.id)
    ElMessage.success(t('dataset.restoreDone'))
    await loadTrash()
    await load()
  } catch (reason) {
    notifyError(reason, t('dataset.restoreFailed'))
  }
}

const statsMetrics = computed(() => {
  const stats = statsData.value
  if (!stats) return []
  return [
    { label: t('dataset.stats.datasetTotal'), value: String(stats.datasetTotal) },
    { label: t('dataset.stats.datasetTotalDuration'), value: formatDuration(stats.datasetTotalDuration) },
    { label: t('dataset.stats.annotationTotal'), value: String(stats.annotationTotal) },
    { label: t('dataset.stats.qualifiedAnnotationTotal'), value: String(stats.qualifiedAnnotationTotal) },
    { label: t('dataset.stats.averageAnnotationsPerDataset'), value: stats.averageAnnotationsPerDataset.toFixed(2) },
    { label: t('dataset.stats.annotationTotalDuration'), value: formatDuration(stats.annotationTotalDuration) },
    { label: t('dataset.stats.averageAnnotationDurationPerDataset'), value: formatDuration(stats.averageAnnotationDurationPerDataset) },
    { label: t('dataset.stats.invalidDatasetTotal'), value: String(stats.invalidDatasetTotal) },
    { label: t('dataset.stats.checkedQualifiedRate'), value: `${stats.checkedQualifiedRate.toFixed(2)}%` },
    { label: t('dataset.stats.invalidCollect'), value: String(stats.invalidCollect) },
    { label: t('dataset.stats.semanticUncorrected'), value: String(stats.semanticUncorrected) },
    { label: t('dataset.stats.semanticCorrected'), value: String(stats.semanticCorrected) },
  ]
})

onMounted(async () => {
  readRoute()
  try {
    const [projects, robots, tags, users] = await Promise.all([
      getProjects({ page: 1, size: 200 }),
      getRobotOptions(),
      getTagOptions(),
      getUserOptions(),
    ])
    projectOptions.value = projects.items
    robotOptions.value = robots
    tagOptions.value = tags
    userOptions.value = users
  } catch {
    // options are non-blocking; list still loads
  }
  await load()
})
</script>

<template>
  <section class="admin-page dataset-page">
    <PageHeader
      :title="t('dataset.title')"
      :eyebrow="t('dataset.eyebrow')"
      :description="t('dataset.description')"
    >
      <template #actions>
        <el-button
          :icon="Delete"
          @click="openTrash"
        >
          {{ t('dataset.trash') }}
        </el-button>
        <el-button
          :icon="Refresh"
          circle
          :aria-label="t('dataset.refreshTitle')"
          @click="load"
        />
      </template>
    </PageHeader>

    <div class="scope-bar">
      <el-radio-group
        :model-value="filters.scope"
        @update:model-value="(value: unknown) => {
          filters.scope = value as DatasetScope
          applyFilters()
        }"
      >
        <el-radio-button value="ALL">{{ t('dataset.scope.all') }}</el-radio-button>
        <el-radio-button value="PROJECT">{{ t('dataset.scope.project') }}</el-radio-button>
        <el-radio-button value="PERSONAL">{{ t('dataset.scope.personal') }}</el-radio-button>
      </el-radio-group>
      <p v-if="filters.scope === 'ALL'">{{ t('dataset.scope.hint') }}</p>
    </div>

    <form
      class="filter-grid"
      @submit.prevent="applyFilters"
    >
      <el-input
        v-model="filters.name"
        clearable
        :placeholder="t('dataset.fields.name')"
      />
      <el-select
        v-model="filters.projectIds"
        multiple
        collapse-tags
        collapse-tags-tooltip
        :placeholder="t('dataset.fields.projects')"
      >
        <el-option
          v-for="project in projectOptions"
          :key="project.id"
          :label="project.name"
          :value="project.id"
        />
      </el-select>
      <el-select
        v-model="filters.robotCode"
        clearable
        filterable
        allow-create
        :placeholder="t('dataset.fields.robot')"
      >
        <el-option
          v-for="robot in robotOptions"
          :key="robot"
          :label="robot"
          :value="robot"
        />
      </el-select>
      <el-select
        v-model="filters.tag"
        clearable
        filterable
        :placeholder="t('dataset.fields.tag')"
      >
        <el-option
          v-for="tag in tagOptions"
          :key="tag"
          :label="tag"
          :value="tag"
        />
      </el-select>
      <el-select
        v-model="filters.uploaderId"
        clearable
        filterable
        :placeholder="t('dataset.fields.uploader')"
      >
        <el-option
          v-for="user in userOptions"
          :key="user.id"
          :label="user.name"
          :value="user.id"
        />
      </el-select>
      <el-select
        v-model="filters.collectorIds"
        multiple
        collapse-tags
        collapse-tags-tooltip
        filterable
        :placeholder="t('dataset.fields.collector')"
      >
        <el-option
          v-for="user in userOptions"
          :key="user.id"
          :label="user.name"
          :value="user.id"
        />
      </el-select>
      <el-input
        v-model="filters.sourceTaskCode"
        clearable
        :placeholder="t('dataset.fields.sourceTask')"
      />
      <div class="duration-range">
        <el-input-number
          v-model="filters.minDuration"
          :min="0"
          controls-position="right"
          :placeholder="t('dataset.minDuration')"
        />
        <span>–</span>
        <el-input-number
          v-model="filters.maxDuration"
          :min="0"
          controls-position="right"
          :placeholder="t('dataset.maxDuration')"
        />
      </div>
      <el-input
        v-model="filters.annotationText"
        clearable
        :placeholder="t('dataset.fields.annotationText')"
      />
      <div class="filter-actions">
        <el-button
          type="primary"
          native-type="submit"
          :icon="Search"
        >
          {{ t('common.search') }}
        </el-button>
        <el-button @click="resetFilters">
          {{ t('common.reset') }}
        </el-button>
      </div>
    </form>

    <div
      v-if="chips.length"
      class="chip-row"
    >
      <el-tag
        v-for="chip in chips"
        :key="chip.key"
        closable
        effect="plain"
        @close="chip.remove"
      >
        {{ chip.label }}
      </el-tag>
    </div>

    <StatePanel
      v-if="loading && !rows.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !rows.length"
      state="error"
      :title="t('dataset.loadFailed')"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!rows.length"
      state="empty"
      :title="t('dataset.notFound')"
      :description="t('dataset.notFoundDesc')"
    />
    <template v-else>
      <div class="table-shell">
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="rows"
          row-key="id"
          @selection-change="handleSelectionChange"
          @sort-change="handleSortChange"
        >
          <el-table-column
            type="selection"
            width="44"
            reserve-selection
          />
          <el-table-column
            :label="t('dataset.fields.thumbnail')"
            width="84"
          >
            <template #default="{ row }">
              <div
                class="thumb"
                :class="`thumb--${mediaKind(row.dataType)}`"
              >
                <el-icon><VideoPlay /></el-icon>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            prop="name"
            :label="t('dataset.fields.name')"
            min-width="240"
            fixed="left"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <button
                class="name-link"
                type="button"
                @click="openDetail(row)"
              >
                {{ row.name }}
              </button>
            </template>
          </el-table-column>
          <el-table-column
            prop="dataType"
            :label="t('dataset.fields.type')"
            width="110"
          >
            <template #default="{ row }">
              <el-tag
                size="small"
                effect="plain"
              >
                {{ row.dataType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('dataset.fields.size')"
            width="100"
            align="right"
          >
            <template #default="{ row }">
              {{ formatBytes(row.sizeBytes) }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('dataset.fields.duration')"
            width="100"
          >
            <template #default="{ row }">
              {{ formatDuration(row.durationSeconds) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="createdAt"
            :label="t('dataset.fields.createdAt')"
            min-width="150"
            sortable="custom"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('dataset.fields.annotationStatus')"
            min-width="130"
          >
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.annotationStatus === 'COMPLETED'
                  ? 'success'
                  : row.annotationStatus === 'ASSIGNED'
                    ? 'warning'
                    : 'info'"
                effect="plain"
              >
                {{ t(`dataset.annotationStatus.${row.annotationStatus}`) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('dataset.fields.metadataStatus')"
            min-width="110"
          >
            <template #default="{ row }">
              <span
                class="metadata"
                :class="`metadata--${row.metadataStatus.toLowerCase()}`"
              >
                <i />
                {{ t(`dataset.metadataStatus.${row.metadataStatus}`) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('dataset.fields.tags')"
            min-width="160"
          >
            <template #default="{ row }">
              <span class="tag-cell">
                <el-tag
                  v-for="tag in row.tags.slice(0, 3)"
                  :key="tag"
                  size="small"
                  effect="plain"
                >
                  {{ tag }}
                </el-tag>
                <span
                  v-if="row.tags.length > 3"
                  class="tag-more"
                >+{{ row.tags.length - 3 }}</span>
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="pagination-row">
        <span class="storage-total">
          {{ selected.length
            ? t('dataset.selectedTotal', { count: selected.length, size: formatBytes(selectedSize) })
            : t('dataset.storageTotal', { size: formatBytes(storageTotal) }) }}
        </span>
        <el-pagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @change="handlePageChange"
        />
      </div>
    </template>

    <Transition name="rise">
      <div
        v-if="selected.length"
        class="batch-bar"
      >
        <div class="batch-bar__count">
          <strong>{{ selected.length }}</strong>
          <span>{{ t('dataset.selected') }}</span>
        </div>
        <div class="batch-bar__actions">
          <el-button
            v-permission="'data:dataset:update'"
            :icon="EditPen"
            @click="openRename"
          >{{ t('dataset.actions.rename') }}</el-button>
          <el-button
            :icon="Histogram"
            @click="openStats"
          >{{ t('dataset.actions.stats') }}</el-button>
          <el-button
            v-permission="'data:dataset:annotate'"
            :icon="Operation"
            @click="openAnnotate"
          >{{ t('dataset.actions.annotate') }}</el-button>
          <el-button
            v-permission="'data:dataset:annotate'"
            :icon="PriceTag"
            @click="openTags('add')"
          >{{ t('dataset.actions.addTags') }}</el-button>
          <el-button
            v-permission="'data:dataset:annotate'"
            :icon="PriceTag"
            @click="openTags('remove')"
          >{{ t('dataset.actions.removeTags') }}</el-button>
          <el-button
            v-permission="'data:dataset:preprocess'"
            :icon="Refresh"
            @click="runRefresh"
          >{{ t('dataset.actions.refresh') }}</el-button>
          <el-button
            v-permission="'data:dataset:import'"
            :icon="Switch"
            @click="openImport"
          >{{ t('dataset.actions.import') }}</el-button>
          <el-button
            v-permission="'data:dataset:robot'"
            :icon="Files"
            @click="openRobot"
          >{{ t('dataset.actions.robot') }}</el-button>
          <el-button
            v-permission="'data:dataset:delete'"
            type="danger"
            :icon="Delete"
            @click="runDelete"
          >{{ t('dataset.actions.delete') }}</el-button>
        </div>
      </div>
    </Transition>

    <el-dialog
      v-model="detailOpen"
      :title="detail?.dataset.name ?? t('dataset.detailTitle')"
      width="min(960px, 94vw)"
      top="4vh"
      destroy-on-close
    >
      <div
        v-loading="detailLoading"
        class="detail-body"
      >
        <template v-if="detail">
          <div class="player-pane">
            <video
              v-if="detailKind === 'video' && detail.preview"
              :src="detail.preview.url"
              controls
              :playbackRate="playbackRate"
            />
            <audio
              v-else-if="detailKind === 'audio' && detail.preview"
              :src="detail.preview.url"
              controls
            />
            <img
              v-else-if="detailKind === 'image' && detail.preview"
              :src="detail.preview.url"
              :alt="detail.dataset.name"
            >
            <div
              v-else-if="detailKind === 'mcap'"
              class="player-placeholder"
            >
              <el-icon :size="30"><VideoPlay /></el-icon>
              <p>{{ t('dataset.mcapNote') }}</p>
            </div>
            <div
              v-else
              class="player-placeholder"
            >
              <el-icon :size="26"><VideoPlay /></el-icon>
              <p>{{ t('dataset.noPreview') }}</p>
            </div>
          </div>
          <div
            v-if="detailKind === 'video'"
            class="rate-row"
          >
            <span>{{ t('dataset.playbackRate') }}</span>
            <el-radio-group
              v-model="playbackRate"
              size="small"
            >
              <el-radio-button
                v-for="rate in [0.5, 1, 1.5, 2]"
                :key="rate"
                :value="rate"
              >{{ rate }}x</el-radio-button>
            </el-radio-group>
          </div>

          <div class="detail-grid">
            <div class="detail-card">
              <h3>{{ t('dataset.basicInfo') }}</h3>
              <dl>
                <div><dt>{{ t('dataset.fields.type') }}</dt><dd>{{ detail.dataset.dataType }}</dd></div>
                <div><dt>{{ t('dataset.fields.size') }}</dt><dd>{{ formatBytes(detail.dataset.sizeBytes) }}</dd></div>
                <div><dt>{{ t('dataset.fields.duration') }}</dt><dd>{{ formatDuration(detail.dataset.durationSeconds) }}</dd></div>
                <div><dt>{{ t('dataset.fields.project') }}</dt><dd>{{ detail.dataset.projectName ?? '—' }}</dd></div>
                <div><dt>{{ t('dataset.fields.uploader') }}</dt><dd>{{ detail.dataset.uploaderName }}</dd></div>
                <div><dt>{{ t('dataset.fields.createdAt') }}</dt><dd>{{ formatDateTime(detail.dataset.createdAt) }}</dd></div>
              </dl>
            </div>
            <div class="detail-card">
              <h3>{{ t('dataset.robotInfo') }}</h3>
              <dl>
                <div><dt>{{ t('dataset.fields.robot') }}</dt><dd>{{ detail.dataset.robotCode ?? '—' }}</dd></div>
                <div><dt>{{ t('dataset.fields.collector') }}</dt><dd>{{ detail.dataset.collectorName ?? '—' }}</dd></div>
                <div><dt>{{ t('dataset.fields.sourceTask') }}</dt><dd>{{ detail.dataset.sourceTaskCode ?? '—' }}</dd></div>
                <div><dt>{{ t('dataset.fields.openShared') }}</dt>
                  <dd>
                    <el-tag
                      size="small"
                      :type="detail.dataset.openShared ? 'danger' : 'info'"
                      effect="plain"
                    >{{ detail.dataset.openShared ? t('common.yes') : t('common.no') }}</el-tag>
                  </dd>
                </div>
              </dl>
            </div>
            <div class="detail-card">
              <h3>{{ t('dataset.annotationInfo') }}</h3>
              <dl>
                <div><dt>{{ t('dataset.summary.total') }}</dt><dd>{{ detail.annotation.total }}</dd></div>
                <div><dt>{{ t('dataset.summary.qualified') }}</dt><dd>{{ detail.annotation.qualified }}</dd></div>
                <div><dt>{{ t('dataset.summary.invalid') }}</dt><dd>{{ detail.annotation.invalid }}</dd></div>
                <div><dt>{{ t('dataset.summary.reviewed') }}</dt><dd>{{ detail.annotation.reviewed }}</dd></div>
                <div><dt>{{ t('dataset.summary.covered') }}</dt><dd>{{ formatDuration(detail.annotation.coveredDurationSeconds) }}</dd></div>
              </dl>
            </div>
            <div class="detail-card">
              <h3>{{ t('dataset.taskInfo') }}</h3>
              <dl v-if="detail.task">
                <div><dt>{{ t('dataset.fields.name') }}</dt><dd>{{ detail.task.name }}</dd></div>
                <div><dt>{{ t('common.status') }}</dt><dd>{{ detail.task.status }}</dd></div>
                <div><dt>{{ t('dataset.fields.datasetCount') }}</dt><dd>{{ detail.task.datasetCount }}</dd></div>
                <div><dt>{{ t('dataset.fields.creator') }}</dt><dd>{{ detail.task.creatorName }}</dd></div>
              </dl>
              <p v-else class="empty-note">{{ t('dataset.noTask') }}</p>
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailOpen = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="trashOpen"
      :title="t('dataset.trashTitle')"
      width="min(680px, 92vw)"
    >
      <el-table
        :data="trashItems"
        row-key="id"
      >
        <el-table-column
          prop="name"
          :label="t('dataset.fields.name')"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          :label="t('dataset.fields.size')"
          width="110"
        >
          <template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column
          :label="t('dataset.fields.deletedAt')"
          min-width="150"
        >
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column
          :label="t('common.operation')"
          width="100"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-permission="'data:dataset:delete'"
              :icon="RefreshRight"
              text
              type="primary"
              @click="runRestore(row)"
            >{{ t('dataset.restore') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="trashOpen = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="renameOpen"
      :title="t('dataset.renameTitle')"
      width="min(640px, 92vw)"
    >
      <div class="rename-list">
        <div
          v-for="row in renameRows"
          :key="row.id"
          class="rename-item"
        >
          <small>{{ row.originalName }}</small>
          <el-input v-model="row.name" />
        </div>
      </div>
      <template #footer>
        <el-button @click="renameOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          @click="submitRename"
        >{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="statsOpen"
      :title="t('dataset.statsTitle')"
      width="min(720px, 92vw)"
    >
      <div class="stats-grid">
        <div
          v-for="metric in statsMetrics"
          :key="metric.label"
          class="stats-cell"
        >
          <strong>{{ metric.value }}</strong>
          <span>{{ metric.label }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="statsOpen = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="annotateOpen"
      :title="t('dataset.annotateTitle')"
      width="min(480px, 92vw)"
    >
      <el-input
        v-model="annotateName"
        :placeholder="t('dataset.taskNamePlaceholder')"
      />
      <template #footer>
        <el-button @click="annotateOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          @click="submitAnnotate"
        >{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="tagDialogOpen"
      :title="tagMode === 'add' ? t('dataset.addTagsTitle') : t('dataset.removeTagsTitle')"
      width="min(480px, 92vw)"
    >
      <el-select
        v-model="tagSelection"
        multiple
        filterable
        allow-create
        default-first-option
        style="width: 100%"
        :placeholder="t('dataset.tagsPlaceholder')"
      >
        <el-option
          v-for="tag in tagOptions"
          :key="tag"
          :label="tag"
          :value="tag"
        />
      </el-select>
      <template #footer>
        <el-button @click="tagDialogOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          @click="submitTags"
        >{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importOpen"
      :title="t('dataset.importTitle')"
      width="min(480px, 92vw)"
    >
      <el-select
        v-model="importProjectId"
        filterable
        style="width: 100%"
        :placeholder="t('dataset.projectPlaceholder')"
      >
        <el-option
          v-for="project in projectOptions"
          :key="project.id"
          :label="project.name"
          :value="project.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="importOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          @click="submitImport"
        >{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="robotOpen"
      :title="t('dataset.robotTitle')"
      width="min(480px, 92vw)"
    >
      <el-select
        v-model="robotValue"
        filterable
        allow-create
        default-first-option
        style="width: 100%"
        :placeholder="t('dataset.robotPlaceholder')"
      >
        <el-option
          v-for="robot in robotOptions"
          :key="robot"
          :label="robot"
          :value="robot"
        />
      </el-select>
      <template #footer>
        <el-button @click="robotOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          @click="submitRobot"
        >{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.dataset-page {
  position: relative;
}

.scope-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin: 20px 0 14px;
}

.scope-bar p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}

.filter-actions,
.duration-range {
  display: flex;
  align-items: center;
  gap: 8px;
}

.duration-range :deep(.el-input-number) {
  width: 100%;
}

.duration-range span {
  color: var(--color-text-muted);
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.thumb {
  display: grid;
  width: 52px;
  height: 40px;
  place-items: center;
  border-radius: 5px;
  color: #fff;
  background: linear-gradient(135deg, #46536b, #2c3345);
}

.thumb--audio {
  background: linear-gradient(135deg, #6b5a9c, #453a6b);
}

.thumb--image {
  background: linear-gradient(135deg, #4a8a78, #33604f);
}

.thumb--mcap {
  background: linear-gradient(135deg, #9c7a4a, #6b5530);
}

.name-link {
  padding: 0;
  cursor: pointer;
  border: 0;
  color: var(--color-text-primary);
  background: none;
  font: inherit;
  font-weight: 600;
}

.name-link:hover {
  color: var(--color-accent);
}

.metadata {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.metadata i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-text-muted);
}

.metadata--ready i {
  background: var(--color-success, #4caf7d);
}

.metadata--processing i {
  background: #e0a94a;
}

.metadata--failed i {
  background: var(--color-danger, #e26a5c);
}

.tag-cell {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.tag-more {
  color: var(--color-text-muted);
  font-size: 11px;
}

.pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 16px;
}

.storage-total {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.batch-bar {
  position: sticky;
  bottom: 16px;
  z-index: 12;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 22px;
  padding: 12px 16px;
  border: 1px solid var(--color-border-strong);
  border-radius: 10px;
  background: rgb(255 255 255 / 92%);
  box-shadow: 0 12px 34px rgb(28 36 54 / 14%);
  backdrop-filter: blur(10px);
}

.batch-bar__count {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding-right: 14px;
  border-right: 1px solid var(--color-border-strong);
}

.batch-bar__count strong {
  font-size: 20px;
}

.batch-bar__count span {
  color: var(--color-text-muted);
  font-size: 12px;
}

.batch-bar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.rise-enter-active,
.rise-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.rise-enter-from,
.rise-leave-to {
  opacity: 0;
  transform: translateY(14px);
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.player-pane {
  display: grid;
  min-height: 320px;
  place-items: center;
  overflow: hidden;
  border-radius: 8px;
  background: #0e1320;
}

.player-pane video,
.player-pane img {
  display: block;
  max-width: 100%;
  max-height: 48vh;
}

.player-pane audio {
  width: 82%;
}

.player-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: #9fb0c8;
}

.player-placeholder p {
  margin: 0;
  font-size: 13px;
}

.rate-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rate-row span {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 14px;
}

.detail-card {
  padding: 16px;
  border: 1px solid var(--color-border-strong);
  border-radius: 8px;
  background: var(--color-surface);
}

.detail-card h3 {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 650;
}

.detail-card dl {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
}

.detail-card dl > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-card dt {
  color: var(--color-text-muted);
  font-size: 12px;
}

.detail-card dd {
  margin: 0;
  font-size: 13px;
  font-weight: 550;
  text-align: right;
}

.empty-note {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
}

.rename-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 56vh;
  overflow: auto;
}

.rename-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rename-item small {
  color: var(--color-text-muted);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}

.stats-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border: 1px solid var(--color-border-strong);
  border-radius: 8px;
  background: var(--color-surface);
}

.stats-cell strong {
  font-size: 20px;
  font-weight: 650;
}

.stats-cell span {
  color: var(--color-text-muted);
  font-size: 12px;
}
</style>
