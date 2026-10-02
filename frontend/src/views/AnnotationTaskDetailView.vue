<script setup lang="ts">
import {
  ArrowLeft,
  Check,
  CopyDocument,
  Delete,
  Edit,
  Refresh,
  Right,
  Search,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import StatePanel from '@/components/StatePanel.vue'
import {
  batchAnnotateTask,
  changeAnnotationTaskStatus,
  deleteAnnotationTask,
  getAnnotationTask,
  getAnnotationTaskOptions,
  reviewAnnotationDataset,
  updateAnnotationTask,
} from '@/services/annotation-tasks'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import type {
  AnnotationDataset,
  AnnotationPersonOption,
  AnnotationProjectOption,
  AnnotationTaskDetail,
  AnnotationTaskStatus,
} from '@/types/annotation-task'
import { formatBytes, formatDateTime } from '@/utils/format'

type DetailTab = 'dataset' | 'repeats' | 'invalids' | 'analysis'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const detail = ref<AnnotationTaskDetail | null>(null)
const projects = ref<AnnotationProjectOption[]>([])
const annotators = ref<AnnotationPersonOption[]>([])
const reviewers = ref<AnnotationPersonOption[]>([])
const activeTab = ref<DetailTab>(
  ['dataset', 'repeats', 'invalids', 'analysis'].includes(String(route.params.tab))
    ? route.params.tab as DetailTab
    : 'dataset',
)
const editOpen = ref(false)
const rejectionOpen = ref(false)
const rejectionTarget = ref<AnnotationTaskStatus | null>(null)
const rejectionReason = ref('')
const datasetReview = ref<AnnotationDataset | null>(null)
const datasetReason = ref('')
const batchMode = ref<'QUICK' | 'COPY' | 'REPLACE'>('QUICK')
const batchForm = reactive({
  description: '',
  sourceRelationId: undefined as number | undefined,
  findText: '',
  replaceText: '',
})
const editForm = reactive({
  name: '',
  projectId: undefined as number | undefined,
  annotatorId: undefined as number | undefined,
  reviewerId: undefined as number | undefined,
})

const taskId = computed(() => Number(route.params.id))
const invalidDatasets = computed(() =>
  detail.value?.datasets.filter((item) => item.checkResult === 'INVALID') ?? [],
)
const reviewStats = computed(() => {
  const datasets = detail.value?.datasets ?? []
  return {
    valid: datasets.filter((item) => item.checkResult === 'VALID').length,
    invalid: datasets.filter((item) => item.checkResult === 'INVALID').length,
    pending: datasets.filter((item) => !item.checkResult).length,
  }
})

async function loadDetail() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await getAnnotationTask(taskId.value)
  } catch (reason) {
    error.value = getErrorMessage(reason, t('annotationTasks.detailFailed'))
  } finally {
    loading.value = false
  }
}

async function changeTab(value: string | number) {
  activeTab.value = value as DetailTab
  await router.replace({
    name: 'annotation-task-detail-tab',
    params: { id: taskId.value, tab: activeTab.value },
  })
}

async function transition(status: AnnotationTaskStatus) {
  if (status === 'REJECTED') {
    rejectionTarget.value = status
    rejectionReason.value = ''
    rejectionOpen.value = true
    return
  }
  const confirmed = await confirmAction(
    t('annotationTasks.statusConfirm', { status: t(`annotationTasks.statuses.${status}`) }),
    t('annotationTasks.changeStatus'),
    t(`annotationTasks.actions.${status}`),
  )
  if (!confirmed) return
  await submitStatus(status)
}

async function submitStatus(status = rejectionTarget.value) {
  if (!status) return
  if (status === 'REJECTED' && !rejectionReason.value.trim()) {
    ElMessage.warning(t('annotationTasks.reasonRequired'))
    return
  }
  saving.value = true
  try {
    detail.value = await changeAnnotationTaskStatus(
      taskId.value,
      status,
      rejectionReason.value.trim() || undefined,
    )
    rejectionOpen.value = false
    ElMessage.success(t('annotationTasks.statusChanged'))
  } catch (reason) {
    notifyError(reason, t('annotationTasks.statusFailed'))
  } finally {
    saving.value = false
  }
}

async function openEdit() {
  if (!detail.value) return
  const summary = detail.value.summary
  Object.assign(editForm, {
    name: summary.name,
    projectId: summary.projectId,
    annotatorId: summary.annotatorId,
    reviewerId: summary.reviewerId ?? undefined,
  })
  await loadPeople(summary.projectId)
  editOpen.value = true
}

async function loadPeople(projectId?: number) {
  if (!projectId) return
  try {
    const options = await getAnnotationTaskOptions(projectId)
    projects.value = options.projects
    annotators.value = options.annotators
    reviewers.value = options.reviewers
  } catch (reason) {
    notifyError(reason, t('annotationTasks.optionsFailed'))
  }
}

async function saveEdit() {
  if (!editForm.name.trim() || !editForm.projectId || !editForm.annotatorId) {
    ElMessage.warning(t('annotationTasks.editValidation'))
    return
  }
  saving.value = true
  try {
    detail.value = await updateAnnotationTask(taskId.value, {
      name: editForm.name.trim(),
      projectId: editForm.projectId,
      annotatorId: editForm.annotatorId,
      reviewerId: editForm.reviewerId ?? null,
    })
    editOpen.value = false
    ElMessage.success(t('annotationTasks.updated'))
  } catch (reason) {
    notifyError(reason, t('annotationTasks.updateFailed'))
  } finally {
    saving.value = false
  }
}

async function removeTask() {
  if (!detail.value) return
  const confirmed = await confirmAction(
    t('annotationTasks.deleteSingleConfirm', { name: detail.value.summary.name }),
    t('annotationTasks.deleteTitle'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    await deleteAnnotationTask(taskId.value)
    ElMessage.success(t('annotationTasks.deleted', { count: 1 }))
    await router.replace({ name: 'annotation-tasks' })
  } catch (reason) {
    notifyError(reason, t('annotationTasks.deleteFailed'))
  }
}

async function markDataset(dataset: AnnotationDataset, result: 'VALID' | 'INVALID' | null) {
  if (result === 'INVALID') {
    datasetReview.value = dataset
    datasetReason.value = dataset.rejectionReason ?? ''
    return
  }
  try {
    detail.value = await reviewAnnotationDataset(taskId.value, dataset.relationId, result)
    ElMessage.success(t('annotationTasks.reviewSaved'))
  } catch (reason) {
    notifyError(reason, t('annotationTasks.reviewFailed'))
  }
}

async function submitDatasetRejection() {
  if (!datasetReview.value || !datasetReason.value.trim()) {
    ElMessage.warning(t('annotationTasks.reasonRequired'))
    return
  }
  saving.value = true
  try {
    detail.value = await reviewAnnotationDataset(
      taskId.value,
      datasetReview.value.relationId,
      'INVALID',
      datasetReason.value.trim(),
    )
    datasetReview.value = null
    ElMessage.success(t('annotationTasks.reviewSaved'))
  } catch (reason) {
    notifyError(reason, t('annotationTasks.reviewFailed'))
  } finally {
    saving.value = false
  }
}

async function runBatch() {
  if (batchMode.value === 'QUICK' && !batchForm.description.trim()
    || batchMode.value === 'COPY' && !batchForm.sourceRelationId
    || batchMode.value === 'REPLACE' && !batchForm.findText.trim()) {
    ElMessage.warning(t('annotationTasks.batchValidation'))
    return
  }
  saving.value = true
  try {
    detail.value = await batchAnnotateTask(taskId.value, {
      mode: batchMode.value,
      description: batchForm.description.trim() || undefined,
      sourceRelationId: batchForm.sourceRelationId,
      findText: batchForm.findText.trim() || undefined,
      replaceText: batchForm.replaceText,
    })
    ElMessage.success(t('annotationTasks.batchSaved'))
  } catch (reason) {
    notifyError(reason, t('annotationTasks.batchFailed'))
  } finally {
    saving.value = false
  }
}

function statusType(status: AnnotationTaskStatus) {
  if (status === 'REJECTED') return 'danger'
  if (status === 'APPROVED' || status === 'SUBMITTED') return 'success'
  if (status === 'WORKING') return 'primary'
  if (status === 'REVIEW_PENDING') return 'warning'
  return 'info'
}

watch(() => route.params.tab, (tab) => {
  if (['dataset', 'repeats', 'invalids', 'analysis'].includes(String(tab))) {
    activeTab.value = tab as DetailTab
  }
})

onMounted(async () => {
  await loadDetail()
  if (detail.value?.canEdit) await loadPeople(detail.value.summary.projectId)
})
</script>

<template>
  <section class="admin-page task-detail">
    <el-button
      class="back-button"
      link
      :icon="ArrowLeft"
      @click="router.push({ name: 'annotation-tasks' })"
    >
      {{ t('annotationTasks.backToList') }}
    </el-button>

    <StatePanel
      v-if="loading && !detail"
      state="loading"
      :title="t('state.loading')"
    />
    <StatePanel
      v-else-if="error && !detail"
      state="error"
      :title="t('annotationTasks.detailFailed')"
      :description="error"
      @retry="loadDetail"
    />

    <div
      v-else-if="detail"
      class="detail-layout"
    >
      <aside class="task-sidebar">
        <div class="task-cover">
          <span>ANNOTATION</span>
          <strong>{{ detail.summary.progress }}%</strong>
          <small>{{ detail.summary.reviewedCount }}/{{ detail.summary.datasetCount }}</small>
        </div>
        <div class="task-identity">
          <el-tag
            :type="statusType(detail.summary.status)"
            effect="plain"
          >
            {{ t(`annotationTasks.statuses.${detail.summary.status}`) }}
          </el-tag>
          <h1>{{ detail.summary.name }}</h1>
          <span>{{ detail.summary.projectName }}</span>
        </div>
        <dl>
          <div>
            <dt>{{ t('annotationTasks.annotator') }}</dt>
            <dd>{{ detail.summary.annotatorName }}</dd>
          </div>
          <div>
            <dt>{{ t('annotationTasks.reviewer') }}</dt>
            <dd>{{ detail.summary.reviewerName || t('annotationTasks.unassigned') }}</dd>
          </div>
          <div>
            <dt>{{ t('annotationTasks.creator') }}</dt>
            <dd>{{ detail.summary.creatorName }}</dd>
          </div>
          <div>
            <dt>{{ t('annotationTasks.createdAt') }}</dt>
            <dd>{{ formatDateTime(detail.summary.createdAt) }}</dd>
          </div>
        </dl>
        <div
          v-if="detail.summary.rejectionReason"
          class="rejection-note"
        >
          <strong>{{ t('annotationTasks.rejectionReason') }}</strong>
          <span>{{ detail.summary.rejectionReason }}</span>
        </div>
        <div class="sidebar-actions">
          <el-button
            v-if="detail.canEdit"
            :icon="Edit"
            @click="openEdit"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="detail.canDelete"
            type="danger"
            plain
            :icon="Delete"
            @click="removeTask"
          >
            {{ t('common.delete') }}
          </el-button>
        </div>
        <section class="workflow">
          <span>{{ t('annotationTasks.currentFlow') }}</span>
          <strong>{{ t(`annotationTasks.statuses.${detail.summary.status}`) }}</strong>
          <button
            v-for="status in detail.allowedTransitions"
            :key="status"
            type="button"
            @click="transition(status)"
          >
            <span>{{ t(`annotationTasks.actions.${status}`) }}</span>
            <el-icon><Right /></el-icon>
          </button>
        </section>
      </aside>

      <main class="task-workspace">
        <el-tabs
          :model-value="activeTab"
          @tab-change="changeTab"
        >
          <el-tab-pane
            name="dataset"
            :label="t('annotationTasks.tabs.dataset')"
          />
          <el-tab-pane
            name="repeats"
            :label="t('annotationTasks.tabs.repeats')"
          />
          <el-tab-pane
            name="invalids"
            :label="`${t('annotationTasks.tabs.invalids')} ${invalidDatasets.length}`"
          />
          <el-tab-pane
            name="analysis"
            :label="t('annotationTasks.tabs.analysis')"
          />
        </el-tabs>

        <section v-if="activeTab === 'dataset'">
          <header class="workspace-header">
            <div>
              <h2>{{ t('annotationTasks.datasetsTitle') }}</h2>
              <span>{{ t('annotationTasks.datasetsDescription') }}</span>
            </div>
            <el-button
              circle
              :icon="Refresh"
              :aria-label="t('common.refresh')"
              @click="loadDetail"
            />
          </header>
          <el-table :data="detail.datasets">
            <el-table-column
              prop="name"
              :label="t('annotationTasks.dataset')"
              min-width="190"
            />
            <el-table-column
              prop="dataType"
              :label="t('annotationTasks.dataType')"
              width="100"
            />
            <el-table-column
              :label="t('annotationTasks.size')"
              width="100"
            >
              <template #default="{ row }">
                {{ formatBytes(row.sizeBytes) }}
              </template>
            </el-table-column>
            <el-table-column
              :label="t('annotationTasks.annotation')"
              min-width="210"
            >
              <template #default="{ row }">
                <span class="annotation-copy">
                  {{ row.annotationDescription || t('annotationTasks.notAnnotated') }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              :label="t('annotationTasks.checkResult')"
              width="130"
            >
              <template #default="{ row }">
                <el-tag
                  v-if="row.checkResult"
                  :type="row.checkResult === 'VALID' ? 'success' : 'danger'"
                  effect="plain"
                >
                  {{ t(`annotationTasks.results.${row.checkResult}`) }}
                </el-tag>
                <span
                  v-else
                  class="muted"
                >{{ t('annotationTasks.results.PENDING') }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="detail.canReview || detail.canExecute"
              :label="t('common.operation')"
              width="116"
            >
              <template #default="{ row }">
                <el-button
                  link
                  type="success"
                  :icon="Check"
                  @click="markDataset(row, 'VALID')"
                />
                <el-button
                  link
                  type="danger"
                  :icon="Search"
                  @click="markDataset(row, 'INVALID')"
                />
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section
          v-else-if="activeTab === 'repeats'"
          class="batch-workspace"
        >
          <header class="workspace-header">
            <div>
              <h2>{{ t('annotationTasks.batchTitle') }}</h2>
              <span>{{ t('annotationTasks.batchDescription') }}</span>
            </div>
          </header>
          <el-segmented
            v-model="batchMode"
            :options="[
              { label: t('annotationTasks.batchModes.QUICK'), value: 'QUICK' },
              { label: t('annotationTasks.batchModes.COPY'), value: 'COPY' },
              { label: t('annotationTasks.batchModes.REPLACE'), value: 'REPLACE' },
            ]"
          />
          <el-form
            class="batch-form"
            label-position="top"
          >
            <el-form-item
              v-if="batchMode === 'QUICK'"
              :label="t('annotationTasks.annotationDescription')"
            >
              <el-input
                v-model="batchForm.description"
                type="textarea"
                :rows="6"
                maxlength="10000"
                show-word-limit
              />
            </el-form-item>
            <el-form-item
              v-else-if="batchMode === 'COPY'"
              :label="t('annotationTasks.sampleDataset')"
            >
              <el-select
                v-model="batchForm.sourceRelationId"
                filterable
              >
                <el-option
                  v-for="dataset in detail.datasets"
                  :key="dataset.relationId"
                  :label="dataset.name"
                  :value="dataset.relationId"
                  :disabled="!dataset.annotationDescription"
                />
              </el-select>
            </el-form-item>
            <template v-else>
              <el-form-item :label="t('annotationTasks.findText')">
                <el-input v-model="batchForm.findText" />
              </el-form-item>
              <el-form-item :label="t('annotationTasks.replaceText')">
                <el-input v-model="batchForm.replaceText" />
              </el-form-item>
            </template>
            <el-button
              type="primary"
              :icon="CopyDocument"
              :loading="saving"
              :disabled="!detail.canExecute"
              @click="runBatch"
            >
              {{ t('annotationTasks.applyBatch') }}
            </el-button>
          </el-form>
        </section>

        <section v-else-if="activeTab === 'invalids'">
          <header class="workspace-header">
            <div>
              <h2>{{ t('annotationTasks.invalidTitle') }}</h2>
              <span>{{ t('annotationTasks.invalidDescription') }}</span>
            </div>
          </header>
          <el-table :data="invalidDatasets">
            <el-table-column
              prop="name"
              :label="t('annotationTasks.dataset')"
              min-width="220"
            />
            <el-table-column
              prop="rejectionReason"
              :label="t('annotationTasks.rejectionReason')"
              min-width="280"
            />
          </el-table>
        </section>

        <section
          v-else
          class="analysis-view"
        >
          <header class="workspace-header">
            <div>
              <h2>{{ t('annotationTasks.analysisTitle') }}</h2>
              <span>{{ t('annotationTasks.analysisDescription') }}</span>
            </div>
          </header>
          <div class="analysis-grid">
            <article>
              <span>{{ t('annotationTasks.results.VALID') }}</span>
              <strong>{{ reviewStats.valid }}</strong>
            </article>
            <article>
              <span>{{ t('annotationTasks.results.INVALID') }}</span>
              <strong>{{ reviewStats.invalid }}</strong>
            </article>
            <article>
              <span>{{ t('annotationTasks.results.PENDING') }}</span>
              <strong>{{ reviewStats.pending }}</strong>
            </article>
          </div>
          <el-progress
            type="dashboard"
            :percentage="detail.summary.progress"
            :width="180"
          />
        </section>
      </main>
    </div>

    <el-dialog
      v-model="rejectionOpen"
      :title="t('annotationTasks.rejectionTitle')"
      width="500px"
    >
      <el-input
        v-model="rejectionReason"
        type="textarea"
        :rows="5"
        maxlength="1000"
        show-word-limit
        :placeholder="t('annotationTasks.rejectionPlaceholder')"
      />
      <template #footer>
        <el-button @click="rejectionOpen = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="danger"
          :loading="saving"
          @click="submitStatus()"
        >
          {{ t('annotationTasks.confirmRejection') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      :model-value="Boolean(datasetReview)"
      :title="t('annotationTasks.datasetRejectionTitle')"
      width="500px"
      @close="datasetReview = null"
    >
      <el-input
        v-model="datasetReason"
        type="textarea"
        :rows="5"
        maxlength="1000"
        show-word-limit
      />
      <template #footer>
        <el-button @click="datasetReview = null">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="danger"
          :loading="saving"
          @click="submitDatasetRejection"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="editOpen"
      :title="t('annotationTasks.editTask')"
      size="440px"
    >
      <el-form
        class="drawer-form"
        label-position="top"
      >
        <el-form-item
          :label="t('annotationTasks.taskName')"
          required
        >
          <el-input
            v-model="editForm.name"
            maxlength="120"
            show-word-limit
          />
        </el-form-item>
        <el-form-item
          :label="t('annotationTasks.project')"
          required
        >
          <el-select
            v-model="editForm.projectId"
            filterable
            @change="loadPeople"
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
          :label="t('annotationTasks.annotator')"
          required
        >
          <el-select v-model="editForm.annotatorId">
            <el-option
              v-for="person in annotators"
              :key="person.id"
              :label="person.displayName"
              :value="person.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('annotationTasks.reviewer')">
          <el-select
            v-model="editForm.reviewerId"
            clearable
          >
            <el-option
              v-for="person in reviewers"
              :key="person.id"
              :label="person.displayName"
              :value="person.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="editOpen = false">
            {{ t('common.cancel') }}
          </el-button>
          <el-button
            type="primary"
            :loading="saving"
            @click="saveEdit"
          >
            {{ t('common.save') }}
          </el-button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.back-button {
  margin: 0 0 14px;
}

.detail-layout {
  display: grid;
  grid-template-columns: 270px minmax(0, 1fr);
  min-height: 660px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
}

.task-sidebar {
  padding: 18px;
  border-right: 1px solid var(--color-border);
  background: #f4f7f8;
}

.task-cover {
  display: grid;
  min-height: 132px;
  align-content: space-between;
  padding: 16px;
  color: #e5f4f5;
  background:
    linear-gradient(135deg, rgb(18 116 138 / 88%), rgb(23 35 43 / 96%)),
    #17232b;
  border-radius: 5px;
}

.task-cover span {
  font-family: var(--font-mono);
  font-size: 9px;
}

.task-cover strong {
  font-family: var(--font-mono);
  font-size: 34px;
}

.task-cover small {
  color: #b9d5da;
}

.task-identity {
  padding: 18px 0;
  border-bottom: 1px solid var(--color-border);
}

.task-identity h1 {
  margin: 10px 0 5px;
  overflow-wrap: anywhere;
  font-size: 19px;
  letter-spacing: 0;
}

.task-identity > span {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.task-sidebar dl {
  display: grid;
  gap: 12px;
  margin: 17px 0;
}

.task-sidebar dl div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.task-sidebar dt {
  color: var(--color-text-muted);
  font-size: 11px;
}

.task-sidebar dd {
  margin: 0;
  font-size: 12px;
  text-align: right;
}

.rejection-note {
  display: grid;
  gap: 5px;
  padding: 10px;
  color: #8f3434;
  font-size: 11px;
  background: #fff1f1;
  border-left: 3px solid var(--color-danger);
}

.sidebar-actions {
  display: flex;
  gap: 8px;
  padding: 4px 0 18px;
}

.workflow {
  display: grid;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}

.workflow > span {
  color: var(--color-text-muted);
  font-size: 10px;
}

.workflow > strong {
  margin-bottom: 4px;
  font-size: 13px;
}

.workflow button {
  display: flex;
  min-height: 38px;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px;
  color: var(--color-text-primary);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  cursor: pointer;
}

.workflow button:hover {
  color: var(--color-accent);
  border-color: var(--color-accent-soft);
}

.task-workspace {
  min-width: 0;
  padding: 6px 22px 24px;
}

.workspace-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0 20px;
}

.workspace-header h2 {
  margin: 0 0 5px;
  font-size: 17px;
  letter-spacing: 0;
}

.workspace-header span {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.annotation-copy {
  display: -webkit-box;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 12px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.batch-workspace {
  max-width: 720px;
}

.batch-form {
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
}

.batch-form :deep(.el-select) {
  width: 100%;
}

.analysis-view {
  text-align: center;
}

.analysis-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  margin: 8px 0 40px;
  background: var(--color-border);
  border: 1px solid var(--color-border);
}

.analysis-grid article {
  display: grid;
  gap: 10px;
  padding: 24px;
  text-align: left;
  background: var(--color-surface);
}

.analysis-grid span {
  color: var(--color-text-secondary);
  font-size: 11px;
}

.analysis-grid strong {
  font-family: var(--font-mono);
  font-size: 26px;
}

.task-detail :deep(.el-drawer .el-select) {
  width: 100%;
}

@media (max-width: 820px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .task-sidebar {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .task-cover {
    min-height: 108px;
  }

  .task-workspace {
    padding-inline: 14px;
  }

  .analysis-grid {
    grid-template-columns: 1fr;
  }
}
</style>
