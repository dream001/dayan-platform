<script setup lang="ts">
import {
  ArrowRight,
  Delete,
  Edit,
  Plus,
  Refresh,
  Remove,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  changeCollectionTaskStatus,
  createCollectionTask,
  deleteCollectionTask,
  getCollectionOptions,
  getCollectionStatusCounts,
  getCollectionTask,
  getCollectionTasks,
  unlinkCollectionDataset,
  updateCollectionTask,
} from '@/services/admin'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'
import type {
  CollectionAssignee,
  CollectionProjectOption,
  CollectionStatusCounts,
  CollectionTaskDetail,
  CollectionTaskPayload,
  CollectionTaskStatus,
  CollectionTaskSummary,
} from '@/types/admin'
import { formatBytes, formatDateTime } from '@/utils/format'

const statuses: CollectionTaskStatus[] = [
  'PENDING',
  'WORKING',
  'REVIEW_PENDING',
  'REJECTED',
  'APPROVED',
  'SUBMITTED',
]

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const tasks = ref<CollectionTaskSummary[]>([])
const total = ref(0)
const counts = ref<CollectionStatusCounts>({
  total: 0,
  statuses: Object.fromEntries(statuses.map((status) => [status, 0])) as Record<CollectionTaskStatus, number>,
})
const projects = ref<CollectionProjectOption[]>([])
const collectors = ref<CollectionAssignee[]>([])
const query = reactive({
  page: Number(route.query.page) || 1,
  size: Number(route.query.size) || 50,
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
  collectorId: route.query.collectorId ? Number(route.query.collectorId) : undefined as number | undefined,
  status: statuses.includes(route.query.status as CollectionTaskStatus)
    ? route.query.status as CollectionTaskStatus
    : undefined as CollectionTaskStatus | undefined,
})
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<CollectionTaskDetail | null>(null)
const detailTab = ref('datasets')
const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const form = reactive<CollectionTaskPayload>({
  name: '',
  projectId: null,
  assigneeIds: [],
  targetCount: 1,
  averageDurationSeconds: 60,
  notes: '',
  initialScene: '',
  remoteOperationEnabled: false,
  steps: [],
})

const canManage = computed(() => auth.hasPermission('data:collect:task:manage'))
const activeStatus = computed(() => query.status ?? 'ALL')

function statusLabel(status: CollectionTaskStatus) {
  return t(`collections.status.${status}`)
}

function statusType(status: CollectionTaskStatus) {
  if (status === 'APPROVED' || status === 'SUBMITTED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'WORKING' || status === 'REVIEW_PENDING') return 'warning'
  return 'info'
}

function queryParams() {
  return {
    page: query.page,
    size: query.size,
    keyword: query.keyword.trim() || undefined,
    collectorId: query.collectorId,
    status: query.status,
  }
}

async function syncUrl() {
  await router.replace({
    name: 'collection-tasks',
    query: Object.fromEntries(
      Object.entries(queryParams()).filter(([, value]) => value !== undefined && value !== ''),
    ),
  })
}

async function loadTasks() {
  loading.value = true
  error.value = ''
  try {
    const [page, statusCounts] = await Promise.all([
      getCollectionTasks(queryParams()),
      getCollectionStatusCounts(),
    ])
    tasks.value = page.items
    total.value = page.total
    counts.value = statusCounts
  } catch (reason) {
    error.value = getErrorMessage(reason, t('collections.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function search() {
  query.page = 1
  await syncUrl()
  await loadTasks()
}

async function selectStatus(status?: CollectionTaskStatus) {
  query.status = status
  await search()
}

async function resetFilters() {
  query.keyword = ''
  query.collectorId = undefined
  query.status = undefined
  await search()
}

async function loadBaseOptions() {
  if (!canManage.value) return
  try {
    const options = await getCollectionOptions()
    projects.value = options.projects
    collectors.value = options.collectors
  } catch {
    projects.value = []
    collectors.value = []
  }
}

async function loadCollectors(projectId: number | null, preserve = false) {
  collectors.value = []
  if (!projectId) return
  try {
    const options = await getCollectionOptions(projectId)
    projects.value = options.projects
    collectors.value = options.collectors
    if (!preserve) form.assigneeIds = []
  } catch (reason) {
    notifyError(reason, t('collections.optionsFailed'))
  }
}

function resetForm() {
  Object.assign(form, {
    name: '',
    projectId: null,
    assigneeIds: [],
    targetCount: 1,
    averageDurationSeconds: 60,
    notes: '',
    initialScene: '',
    remoteOperationEnabled: false,
    steps: [],
  })
  editingId.value = null
  collectors.value = []
}

function openCreate() {
  resetForm()
  formMode.value = 'create'
  formOpen.value = true
}

async function openEdit() {
  if (!detail.value) return
  resetForm()
  formMode.value = 'edit'
  editingId.value = detail.value.summary.id
  Object.assign(form, {
    name: detail.value.summary.name,
    projectId: detail.value.summary.projectId,
    assigneeIds: detail.value.assignees.map((item) => item.id),
    targetCount: detail.value.summary.targetCount,
    averageDurationSeconds: detail.value.summary.averageDurationSeconds,
    notes: detail.value.notes ?? '',
    initialScene: detail.value.initialScene ?? '',
    remoteOperationEnabled: detail.value.remoteOperationEnabled,
    steps: detail.value.steps.map((step) => ({ ...step })),
  })
  await loadCollectors(form.projectId, true)
  formOpen.value = true
}

function addStep() {
  form.steps.push({
    actionName: '',
    objectName: '',
    targetName: '',
    notes: '',
  })
}

function removeStep(index: number) {
  form.steps.splice(index, 1)
}

async function saveTask() {
  if (!form.name.trim() || !form.projectId || !form.assigneeIds.length) {
    ElMessage.warning(t('collections.required'))
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      name: form.name.trim(),
      notes: form.notes.trim(),
      initialScene: form.initialScene.trim(),
      steps: form.steps.map((step) => ({
        actionName: step.actionName.trim(),
        objectName: step.objectName.trim(),
        targetName: step.targetName.trim(),
        notes: step.notes.trim(),
      })),
    }
    const result = formMode.value === 'create'
      ? await createCollectionTask(payload)
      : await updateCollectionTask(editingId.value!, payload)
    ElMessage.success(t(formMode.value === 'create' ? 'collections.created' : 'collections.updated'))
    formOpen.value = false
    await loadTasks()
    await showDetail(result.summary.id)
  } catch (reason) {
    notifyError(reason, t('collections.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function showDetail(id: number, updateRoute = true) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getCollectionTask(id)
    if (updateRoute && route.params.id !== String(id)) {
      await router.push({
        name: 'collection-task-detail',
        params: { id },
        query: route.query,
      })
    }
  } catch (reason) {
    notifyError(reason, t('collections.detailFailed'))
    detailOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

async function leaveDetailRoute() {
  if (route.name === 'collection-task-detail') {
    await router.replace({ name: 'collection-tasks', query: route.query })
  }
}

async function transition(status: CollectionTaskStatus) {
  if (!detail.value) return
  try {
    detail.value = await changeCollectionTaskStatus(detail.value.summary.id, status)
    ElMessage.success(t('collections.statusChanged'))
    await loadTasks()
  } catch (reason) {
    notifyError(reason, t('collections.statusFailed'))
  }
}

async function removeTask() {
  if (!detail.value) return
  const confirmed = await confirmAction(
    t('collections.deleteConfirm', { name: detail.value.summary.name }),
    t('collections.deleteTitle'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    await deleteCollectionTask(detail.value.summary.id)
    ElMessage.success(t('collections.deleted'))
    detailOpen.value = false
    await leaveDetailRoute()
    await loadTasks()
  } catch (reason) {
    notifyError(reason, t('collections.deleteFailed'))
  }
}

async function unlinkDataset(fileId: number) {
  if (!detail.value) return
  const confirmed = await confirmAction(
    t('collections.unlinkConfirm'),
    t('collections.unlinkData'),
    t('collections.unlink'),
  )
  if (!confirmed) return
  try {
    await unlinkCollectionDataset(detail.value.summary.id, fileId)
    await showDetail(detail.value.summary.id, false)
    await loadTasks()
    ElMessage.success(t('collections.unlinked'))
  } catch (reason) {
    notifyError(reason, t('collections.unlinkFailed'))
  }
}

watch(
  () => route.params.id,
  (id) => {
    if (typeof id === 'string' && Number.isFinite(Number(id))) {
      void showDetail(Number(id), false)
    } else {
      detailOpen.value = false
    }
  },
)

onMounted(async () => {
  await Promise.all([loadTasks(), loadBaseOptions()])
  if (typeof route.params.id === 'string') await showDetail(Number(route.params.id), false)
})
</script>

<template>
  <section class="admin-page collection-page">
    <PageHeader
      :title="t('collections.title')"
      :eyebrow="t('collections.eyebrow')"
      :description="t('collections.description')"
    >
      <template #actions>
        <el-button
          v-if="canManage"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          {{ t('collections.create') }}
        </el-button>
      </template>
    </PageHeader>

    <nav
      class="status-rail"
      :aria-label="t('collections.statusFilter')"
    >
      <button
        :class="{ active: activeStatus === 'ALL' }"
        type="button"
        @click="selectStatus()"
      >
        <span>{{ t('collections.all') }}</span>
        <strong>{{ counts.total }}</strong>
      </button>
      <button
        v-for="status in statuses"
        :key="status"
        :class="['status-' + status.toLowerCase(), { active: activeStatus === status }]"
        type="button"
        @click="selectStatus(status)"
      >
        <span>{{ statusLabel(status) }}</span>
        <strong>{{ counts.statuses[status] }}</strong>
      </button>
    </nav>

    <form
      class="filter-bar"
      @submit.prevent="search"
    >
      <el-input
        v-model="query.keyword"
        clearable
        :placeholder="t('collections.keyword')"
      />
      <el-select
        v-model="query.collectorId"
        clearable
        filterable
        :placeholder="t('collections.allCollectors')"
      >
        <el-option
          v-for="collector in collectors"
          :key="collector.id"
          :label="`${collector.displayName} (@${collector.username})`"
          :value="collector.id"
        />
      </el-select>
      <el-button
        type="primary"
        native-type="submit"
      >
        {{ t('common.search') }}
      </el-button>
      <el-button @click="resetFilters">
        {{ t('common.reset') }}
      </el-button>
      <el-tooltip :content="t('common.refresh')">
        <el-button
          :icon="Refresh"
          circle
          :aria-label="t('common.refresh')"
          @click="loadTasks"
        />
      </el-tooltip>
    </form>

    <StatePanel
      v-if="loading && !tasks.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !tasks.length"
      state="error"
      :title="t('collections.loadFailed')"
      :description="error"
      @retry="loadTasks"
    />
    <StatePanel
      v-else-if="!tasks.length"
      state="empty"
      :title="t('collections.empty')"
      :description="t('collections.emptyDesc')"
    />
    <template v-else>
      <div class="table-shell">
        <el-table
          v-loading="loading"
          :data="tasks"
          row-key="id"
        >
          <el-table-column
            :label="t('collections.taskName')"
            min-width="190"
            fixed="left"
          >
            <template #default="{ row }">
              <button
                class="task-link"
                type="button"
                @click="showDetail(row.id)"
              >
                {{ row.name }}
              </button>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('collections.progress')"
            min-width="180"
          >
            <template #default="{ row }">
              <div class="progress-cell">
                <span>{{ row.collectedCount }} / {{ row.targetCount }}</span>
                <el-progress
                  :percentage="Math.min(100, Math.round(row.collectedCount / row.targetCount * 100))"
                  :stroke-width="5"
                  :show-text="false"
                />
              </div>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('collections.latestData')"
            min-width="190"
          >
            <template #default="{ row }">
              <div
                v-if="row.latestFileName"
                class="stacked-cell"
              >
                <strong>{{ row.latestFileName }}</strong>
                <span>{{ formatDateTime(row.latestFileAt) }}</span>
              </div>
              <span
                v-else
                class="muted"
              >—</span>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('collections.actions')"
            min-width="180"
          >
            <template #default="{ row }">
              <div class="tag-list">
                <el-tag
                  v-for="action in row.actions.slice(0, 3)"
                  :key="action"
                  size="small"
                  effect="plain"
                >
                  {{ action }}
                </el-tag>
                <span
                  v-if="!row.actions.length"
                  class="muted"
                >—</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            prop="projectName"
            :label="t('collections.project')"
            min-width="130"
          />
          <el-table-column
            :label="t('collections.collectors')"
            min-width="170"
          >
            <template #default="{ row }">
              {{ row.assignees.join('、') }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('collections.createdAt')"
            min-width="160"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('common.status')"
            width="112"
            fixed="right"
          >
            <template #default="{ row }">
              <el-tag
                :type="statusType(row.status)"
                effect="plain"
              >
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @change="async () => { await syncUrl(); await loadTasks() }"
        />
      </div>
    </template>

    <el-drawer
      v-model="detailOpen"
      size="min(760px, 94vw)"
      destroy-on-close
      @closed="leaveDetailRoute"
    >
      <template #header>
        <div
          v-if="detail"
          class="detail-heading"
        >
          <span>{{ detail.summary.projectName }}</span>
          <h2>{{ detail.summary.name }}</h2>
        </div>
      </template>
      <div
        v-if="detailLoading"
        class="detail-loading"
      >
        <StatePanel state="loading" />
      </div>
      <template v-else-if="detail">
        <div class="detail-toolbar">
          <div>
            <el-tag
              :type="statusType(detail.summary.status)"
              effect="plain"
            >
              {{ statusLabel(detail.summary.status) }}
            </el-tag>
            <span>{{ detail.summary.collectedCount }} / {{ detail.summary.targetCount }}</span>
          </div>
          <div class="detail-actions">
            <el-dropdown
              v-if="detail.allowedTransitions.length"
              trigger="click"
              @command="transition"
            >
              <el-button type="primary">
                {{ t('collections.changeStatus') }}
                <el-icon><ArrowRight /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="status in detail.allowedTransitions"
                    :key="status"
                    :command="status"
                  >
                    {{ statusLabel(status) }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-tooltip :content="t('common.edit')">
              <el-button
                v-if="detail.canEdit"
                :icon="Edit"
                circle
                @click="openEdit"
              />
            </el-tooltip>
            <el-tooltip :content="t('common.delete')">
              <el-button
                v-if="detail.canDelete"
                :icon="Delete"
                circle
                type="danger"
                plain
                @click="removeTask"
              />
            </el-tooltip>
          </div>
        </div>

        <el-tabs v-model="detailTab">
          <el-tab-pane
            :label="t('collections.datasets')"
            name="datasets"
          >
            <el-table
              v-if="detail.datasets.length"
              :data="detail.datasets"
            >
              <el-table-column
                prop="name"
                :label="t('files.name')"
                min-width="220"
              />
              <el-table-column
                :label="t('files.size')"
                width="110"
              >
                <template #default="{ row }">
                  {{ formatBytes(row.sizeBytes) }}
                </template>
              </el-table-column>
              <el-table-column
                :label="t('files.createdAt')"
                min-width="160"
              >
                <template #default="{ row }">
                  {{ formatDateTime(row.uploadedAt) }}
                </template>
              </el-table-column>
              <el-table-column
                v-if="detail.canUnlinkData"
                width="58"
              >
                <template #default="{ row }">
                  <el-tooltip :content="t('collections.unlinkData')">
                    <el-button
                      :icon="Remove"
                      circle
                      text
                      :aria-label="t('collections.unlinkData')"
                      @click="unlinkDataset(row.id)"
                    />
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
            <StatePanel
              v-else
              state="empty"
              :title="t('collections.noDatasets')"
              :description="detail.summary.status === 'SUBMITTED'
                ? t('collections.uploadPrompt')
                : t('collections.noDatasetsDesc')"
            />
          </el-tab-pane>
          <el-tab-pane
            :label="t('collections.devices')"
            name="devices"
          >
            <StatePanel
              state="empty"
              :title="t('collections.noDevices')"
              :description="t('collections.noDevicesDesc')"
            />
          </el-tab-pane>
          <el-tab-pane
            :label="t('collections.overview')"
            name="overview"
          >
            <dl class="overview-grid">
              <div>
                <dt>{{ t('collections.collectors') }}</dt>
                <dd>{{ detail.assignees.map((item) => item.displayName).join('、') }}</dd>
              </div>
              <div>
                <dt>{{ t('collections.quantity') }}</dt>
                <dd>{{ detail.summary.targetCount }}</dd>
              </div>
              <div>
                <dt>{{ t('collections.averageDuration') }}</dt>
                <dd>{{ detail.summary.averageDurationSeconds }} s</dd>
              </div>
              <div>
                <dt>{{ t('collections.initialScene') }}</dt>
                <dd>{{ detail.initialScene || '—' }}</dd>
              </div>
              <div class="overview-wide">
                <dt>{{ t('collections.notes') }}</dt>
                <dd>{{ detail.notes || '—' }}</dd>
              </div>
            </dl>
            <ol
              v-if="detail.steps.length"
              class="step-timeline"
            >
              <li
                v-for="step in detail.steps"
                :key="step.id"
              >
                <span>{{ step.sequenceNo }}</span>
                <div>
                  <strong>{{ step.actionName || step.notes }}</strong>
                  <p v-if="step.objectName || step.targetName">
                    {{ [step.objectName, step.targetName].filter(Boolean).join(' → ') }}
                  </p>
                  <p v-if="step.actionName && step.notes">
                    {{ step.notes }}
                  </p>
                </div>
              </li>
            </ol>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <el-drawer
      v-model="formOpen"
      :title="t(formMode === 'create' ? 'collections.createTitle' : 'collections.editTitle')"
      size="min(680px, 94vw)"
      destroy-on-close
    >
      <el-form
        class="drawer-form"
        label-position="top"
        @submit.prevent="saveTask"
      >
        <div class="form-grid">
          <el-form-item
            :label="t('collections.taskName')"
            required
          >
            <el-input
              v-model="form.name"
              maxlength="120"
              show-word-limit
            />
          </el-form-item>
          <el-form-item
            :label="t('collections.project')"
            required
          >
            <el-select
              v-model="form.projectId"
              filterable
              @change="(value: number) => loadCollectors(value)"
            >
              <el-option
                v-for="project in projects"
                :key="project.id"
                :label="project.name"
                :value="project.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            class="form-wide"
            :label="t('collections.collectors')"
            required
          >
            <el-select
              v-model="form.assigneeIds"
              multiple
              filterable
              :placeholder="form.projectId ? t('collections.selectCollectors') : t('collections.selectProjectFirst')"
              :disabled="!form.projectId"
            >
              <el-option
                v-for="collector in collectors"
                :key="collector.id"
                :label="`${collector.displayName} (@${collector.username})`"
                :value="collector.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            :label="t('collections.quantity')"
            required
          >
            <el-input-number
              v-model="form.targetCount"
              :min="1"
              :max="1000000"
            />
          </el-form-item>
          <el-form-item
            :label="t('collections.averageDuration')"
            required
          >
            <el-input-number
              v-model="form.averageDurationSeconds"
              :min="1"
              :max="86400"
            />
          </el-form-item>
          <el-form-item
            class="form-wide"
            :label="t('collections.notes')"
          >
            <el-input
              v-model="form.notes"
              type="textarea"
              :rows="3"
              maxlength="2000"
            />
          </el-form-item>
          <el-form-item
            class="form-wide"
            :label="t('collections.initialScene')"
          >
            <el-input
              v-model="form.initialScene"
              type="textarea"
              :rows="3"
              maxlength="2000"
            />
          </el-form-item>
          <el-form-item class="form-wide">
            <el-checkbox
              v-model="form.remoteOperationEnabled"
              disabled
            >
              {{ t('collections.remoteOperation') }}
            </el-checkbox>
            <span class="field-hint">{{ t('collections.remoteUnavailable') }}</span>
          </el-form-item>
        </div>

        <div class="step-editor">
          <div class="section-heading">
            <div>
              <strong>{{ t('collections.steps') }}</strong>
              <span>{{ t('collections.stepsHint') }}</span>
            </div>
            <el-button
              :icon="Plus"
              @click="addStep"
            >
              {{ t('collections.addStep') }}
            </el-button>
          </div>
          <div
            v-for="(step, index) in form.steps"
            :key="index"
            class="step-row"
          >
            <span class="step-index">{{ index + 1 }}</span>
            <div class="step-fields">
              <el-input
                v-model="step.actionName"
                :placeholder="t('collections.actionPlaceholder')"
              />
              <el-input
                v-model="step.objectName"
                :disabled="!step.actionName.includes('{A}')"
                :placeholder="t('collections.objectPlaceholder')"
              />
              <el-input
                v-model="step.targetName"
                :disabled="!step.actionName.includes('{B}')"
                :placeholder="t('collections.targetPlaceholder')"
              />
              <el-input
                v-model="step.notes"
                :placeholder="t('collections.stepNotes')"
              />
            </div>
            <el-button
              :icon="Delete"
              circle
              text
              :aria-label="t('collections.removeStep')"
              @click="removeStep(index)"
            />
          </div>
        </div>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="formOpen = false">
            {{ t('common.cancel') }}
          </el-button>
          <el-button
            type="primary"
            :loading="saving"
            @click="saveTask"
          >
            {{ t('common.save') }}
          </el-button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.collection-page {
  --status-pending: #71808a;
  --status-working: #267f93;
  --status-review: #b67a22;
  --status-rejected: #b34242;
  --status-approved: #368363;
  --status-submitted: #5067a8;
}

.status-rail {
  display: grid;
  grid-template-columns: repeat(7, minmax(116px, 1fr));
  gap: 1px;
  margin-top: 18px;
  overflow-x: auto;
  background: var(--color-border);
  border: 1px solid var(--color-border);
}

.status-rail button {
  min-height: 58px;
  padding: 10px 12px;
  border: 0;
  border-bottom: 3px solid transparent;
  color: var(--color-text-secondary);
  background: var(--color-surface);
  cursor: pointer;
  text-align: left;
}

.status-rail button:hover,
.status-rail button.active {
  background: #f3f7f8;
}

.status-rail button.active {
  border-bottom-color: var(--color-accent);
  color: var(--color-text-primary);
}

.status-rail span,
.status-rail strong {
  display: block;
}

.status-rail span {
  font-size: 12px;
}

.status-rail strong {
  margin-top: 3px;
  font-family: var(--font-mono);
  font-size: 17px;
}

.task-link {
  padding: 0;
  border: 0;
  color: var(--color-accent);
  background: none;
  cursor: pointer;
  font-weight: 650;
  text-align: left;
}

.progress-cell {
  display: grid;
  grid-template-columns: 52px 1fr;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
}

.stacked-cell {
  display: grid;
}

.stacked-cell strong {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stacked-cell span {
  color: var(--color-text-muted);
  font-size: 11px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.detail-heading span {
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 10px;
  text-transform: uppercase;
}

.detail-heading h2 {
  margin: 4px 0 0;
  font-size: 20px;
  letter-spacing: 0;
}

.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 0 16px;
}

.detail-toolbar > div,
.detail-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  margin: 10px 0 24px;
  background: var(--color-border);
  border: 1px solid var(--color-border);
}

.overview-grid div {
  min-height: 76px;
  padding: 14px;
  background: var(--color-surface);
}

.overview-grid dt {
  color: var(--color-text-muted);
  font-size: 11px;
}

.overview-grid dd {
  margin: 7px 0 0;
  line-height: 1.55;
}

.overview-wide {
  grid-column: 1 / -1;
}

.step-timeline {
  margin: 0;
  padding: 0;
  list-style: none;
}

.step-timeline li {
  display: grid;
  grid-template-columns: 30px 1fr;
  gap: 12px;
  padding: 0 0 20px;
}

.step-timeline li > span,
.step-index {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border: 1px solid var(--color-accent-soft);
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 11px;
}

.step-timeline p {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.form-grid :deep(.el-select),
.form-grid :deep(.el-input-number) {
  width: 100%;
}

.form-wide {
  grid-column: 1 / -1;
}

.field-hint,
.section-heading span {
  display: block;
  color: var(--color-text-muted);
  font-size: 11px;
}

.field-hint {
  margin-top: 5px;
}

.step-editor {
  padding-top: 8px;
  border-top: 1px solid var(--color-border);
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.section-heading strong {
  display: block;
}

.step-row {
  display: grid;
  grid-template-columns: 28px 1fr 32px;
  align-items: start;
  gap: 10px;
  padding: 12px 0;
  border-top: 1px solid var(--color-border);
}

.step-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

@media (max-width: 700px) {
  .status-rail {
    grid-template-columns: repeat(7, 116px);
  }

  .detail-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .form-grid,
  .overview-grid,
  .step-fields {
    grid-template-columns: 1fr;
  }

  .form-wide,
  .overview-wide {
    grid-column: auto;
  }
}
</style>
