<script setup lang="ts">
import {
  Delete,
  Grid,
  List,
  Plus,
  Refresh,
  Search,
  View,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  deleteAnnotationTask,
  getAnnotationTaskCounts,
  getAnnotationTaskOptions,
  getAnnotationTasks,
} from '@/services/annotation-tasks'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'
import type {
  AnnotationPersonOption,
  AnnotationProjectOption,
  AnnotationTaskCounts,
  AnnotationTaskStatus,
  AnnotationTaskSummary,
} from '@/types/annotation-task'
import { formatDateTime } from '@/utils/format'

const statuses: AnnotationTaskStatus[] = [
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
const error = ref('')
const tasks = ref<AnnotationTaskSummary[]>([])
const selected = ref<AnnotationTaskSummary[]>([])
const total = ref(0)
const viewMode = ref<'board' | 'table'>('board')
const projects = ref<AnnotationProjectOption[]>([])
const annotators = ref<AnnotationPersonOption[]>([])
const reviewers = ref<AnnotationPersonOption[]>([])
const counts = ref<AnnotationTaskCounts>({
  total: 0,
  statuses: {
    PENDING: 0,
    WORKING: 0,
    REVIEW_PENDING: 0,
    REJECTED: 0,
    APPROVED: 0,
    SUBMITTED: 0,
  },
})
const query = reactive({
  page: Number(route.query.page) || 1,
  size: Number(route.query.size) || 20,
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
  status: statuses.includes(route.query.status as AnnotationTaskStatus)
    ? route.query.status as AnnotationTaskStatus
    : undefined,
  projectId: route.query.projectId ? Number(route.query.projectId) : undefined,
  annotatorId: route.query.annotatorId ? Number(route.query.annotatorId) : undefined,
  reviewerId: route.query.reviewerId ? Number(route.query.reviewerId) : undefined,
  createdDate: typeof route.query.createdDate === 'string' ? route.query.createdDate : '',
})

const canManage = computed(() => auth.hasPermission('data:annotate:task:manage'))
const groupedTasks = computed(() => Object.fromEntries(
  statuses.map((status) => [status, tasks.value.filter((task) => task.status === status)]),
) as Record<AnnotationTaskStatus, AnnotationTaskSummary[]>)

function requestParams() {
  return {
    page: query.page,
    size: query.size,
    keyword: query.keyword.trim() || undefined,
    status: query.status,
    projectId: query.projectId,
    annotatorId: query.annotatorId,
    reviewerId: query.reviewerId,
    createdDate: query.createdDate || undefined,
  }
}

async function syncUrl() {
  const params = requestParams()
  await router.replace({
    query: Object.fromEntries(
      Object.entries(params)
        .filter(([, value]) => value !== undefined && value !== '')
        .map(([key, value]) => [key, String(value)]),
    ),
  })
}

async function loadTasks() {
  loading.value = true
  error.value = ''
  try {
    const [page, countResult] = await Promise.all([
      getAnnotationTasks(requestParams()),
      getAnnotationTaskCounts(),
    ])
    tasks.value = page.items
    total.value = page.total
    counts.value = countResult
    selected.value = []
  } catch (reason) {
    error.value = getErrorMessage(reason, t('annotationTasks.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const options = await getAnnotationTaskOptions(query.projectId)
    projects.value = options.projects
    annotators.value = options.annotators
    reviewers.value = options.reviewers
  } catch {
    projects.value = []
    annotators.value = []
    reviewers.value = []
  }
}

async function search() {
  query.page = 1
  await syncUrl()
  await loadTasks()
}

async function selectStatus(value: string | number) {
  query.status = value === 'ALL' ? undefined : value as AnnotationTaskStatus
  await search()
}

async function changeProject() {
  query.annotatorId = undefined
  query.reviewerId = undefined
  await loadOptions()
}

async function resetFilters() {
  Object.assign(query, {
    page: 1,
    keyword: '',
    status: undefined,
    projectId: undefined,
    annotatorId: undefined,
    reviewerId: undefined,
    createdDate: '',
  })
  await loadOptions()
  await syncUrl()
  await loadTasks()
}

async function openTask(task: AnnotationTaskSummary) {
  await router.push({ name: 'annotation-task-detail', params: { id: task.id } })
}

async function removeTasks(items: AnnotationTaskSummary[]) {
  if (!items.length) return
  const confirmed = await confirmAction(
    t('annotationTasks.deleteConfirm', { count: items.length }),
    t('annotationTasks.deleteTitle'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    for (const task of items) await deleteAnnotationTask(task.id)
    ElMessage.success(t('annotationTasks.deleted', { count: items.length }))
    await loadTasks()
  } catch (reason) {
    notifyError(reason, t('annotationTasks.deleteFailed'))
    await loadTasks()
  }
}

function statusType(status: AnnotationTaskStatus) {
  if (status === 'REJECTED') return 'danger'
  if (status === 'APPROVED' || status === 'SUBMITTED') return 'success'
  if (status === 'WORKING') return 'primary'
  if (status === 'REVIEW_PENDING') return 'warning'
  return 'info'
}

onMounted(async () => {
  await Promise.all([loadOptions(), loadTasks()])
})
</script>

<template>
  <section class="admin-page annotation-tasks">
    <PageHeader
      :title="t('annotationTasks.title')"
      :eyebrow="t('annotationTasks.eyebrow')"
      :description="t('annotationTasks.description')"
    >
      <template #actions>
        <el-tooltip :content="t('annotationTasks.refresh')">
          <el-button
            circle
            :icon="Refresh"
            :aria-label="t('annotationTasks.refresh')"
            @click="loadTasks"
          />
        </el-tooltip>
        <el-button
          v-if="canManage"
          type="primary"
          :icon="Plus"
          @click="router.push({ name: 'annotation-task-create' })"
        >
          {{ t('annotationTasks.create') }}
        </el-button>
      </template>
    </PageHeader>

    <el-tabs
      class="status-tabs"
      :model-value="query.status ?? 'ALL'"
      @tab-change="selectStatus"
    >
      <el-tab-pane
        name="ALL"
        :label="`${t('annotationTasks.all')} ${counts.total}`"
      />
      <el-tab-pane
        v-for="status in statuses"
        :key="status"
        :name="status"
        :label="`${t(`annotationTasks.statuses.${status}`)} ${counts.statuses[status]}`"
      />
    </el-tabs>

    <div class="task-toolbar">
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          clearable
          :prefix-icon="Search"
          :placeholder="t('annotationTasks.keyword')"
          @keyup.enter="search"
        />
        <el-select
          v-model="query.projectId"
          clearable
          filterable
          :placeholder="t('annotationTasks.allProjects')"
          @change="changeProject"
        >
          <el-option
            v-for="project in projects"
            :key="project.id"
            :label="project.name"
            :value="project.id"
          />
        </el-select>
        <el-select
          v-model="query.annotatorId"
          clearable
          filterable
          :disabled="!query.projectId"
          :placeholder="t('annotationTasks.allAnnotators')"
        >
          <el-option
            v-for="person in annotators"
            :key="person.id"
            :label="person.displayName"
            :value="person.id"
          />
        </el-select>
        <el-select
          v-model="query.reviewerId"
          clearable
          filterable
          :disabled="!query.projectId"
          :placeholder="t('annotationTasks.allReviewers')"
        >
          <el-option
            v-for="person in reviewers"
            :key="person.id"
            :label="person.displayName"
            :value="person.id"
          />
        </el-select>
        <el-date-picker
          v-model="query.createdDate"
          type="date"
          value-format="YYYY-MM-DD"
          :placeholder="t('annotationTasks.createdDate')"
        />
        <el-button
          type="primary"
          @click="search"
        >
          {{ t('common.search') }}
        </el-button>
        <el-button @click="resetFilters">
          {{ t('common.reset') }}
        </el-button>
      </div>
      <el-segmented
        v-model="viewMode"
        :options="[
          { value: 'board', label: t('annotationTasks.board'), icon: Grid },
          { value: 'table', label: t('annotationTasks.table'), icon: List },
        ]"
      />
    </div>

    <StatePanel
      v-if="loading && !tasks.length"
      state="loading"
      :title="t('state.loading')"
    />
    <StatePanel
      v-else-if="error && !tasks.length"
      state="error"
      :title="t('annotationTasks.loadFailed')"
      :description="error"
      @retry="loadTasks"
    />
    <StatePanel
      v-else-if="!tasks.length"
      state="empty"
      :title="t('annotationTasks.empty')"
      :description="t('annotationTasks.emptyDesc')"
    />

    <div
      v-else-if="viewMode === 'board'"
      class="status-board"
    >
      <section
        v-for="status in statuses"
        :key="status"
        class="status-column"
      >
        <header>
          <span
            class="status-marker"
            :data-status="status"
          />
          <strong>{{ t(`annotationTasks.statuses.${status}`) }}</strong>
          <small>{{ groupedTasks[status].length }}</small>
        </header>
        <div class="status-column__body">
          <button
            v-for="task in groupedTasks[status]"
            :key="task.id"
            type="button"
            class="task-card"
            @click="openTask(task)"
          >
            <span class="task-card__project">{{ task.projectName }}</span>
            <strong>{{ task.name }}</strong>
            <span class="task-card__people">
              {{ task.annotatorName }} · {{ task.reviewerName || t('annotationTasks.unassigned') }}
            </span>
            <span class="task-card__progress">
              <span :style="{ width: `${task.progress}%` }" />
            </span>
            <span class="task-card__meta">
              {{ task.reviewedCount }}/{{ task.datasetCount }}
              <time>{{ formatDateTime(task.createdAt) }}</time>
            </span>
          </button>
          <p v-if="!groupedTasks[status].length">
            {{ t('annotationTasks.noStatusTasks') }}
          </p>
        </div>
      </section>
    </div>

    <div
      v-else
      class="table-shell"
    >
      <el-table
        v-loading="loading"
        :data="tasks"
        row-key="id"
        @selection-change="selected = $event"
        @row-dblclick="openTask"
      >
        <el-table-column
          v-if="canManage"
          type="selection"
          width="44"
        />
        <el-table-column
          prop="name"
          :label="t('annotationTasks.taskName')"
          min-width="220"
        >
          <template #default="{ row }">
            <button
              class="task-link"
              type="button"
              @click="openTask(row)"
            >
              {{ row.name }}
            </button>
          </template>
        </el-table-column>
        <el-table-column
          prop="projectName"
          :label="t('annotationTasks.project')"
          min-width="150"
        />
        <el-table-column
          :label="t('annotationTasks.people')"
          min-width="180"
        >
          <template #default="{ row }">
            <strong>{{ row.annotatorName }}</strong>
            <small>{{ row.reviewerName || t('annotationTasks.unassigned') }}</small>
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
              {{ t(`annotationTasks.statuses.${row.status}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('annotationTasks.progress')"
          width="160"
        >
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress"
              :stroke-width="5"
            />
          </template>
        </el-table-column>
        <el-table-column
          prop="createdAt"
          :label="t('annotationTasks.createdAt')"
          width="170"
        >
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.operation')"
          width="72"
          fixed="right"
        >
          <template #default="{ row }">
            <el-tooltip :content="t('annotationTasks.view')">
              <el-button
                link
                :icon="View"
                :aria-label="t('annotationTasks.view')"
                @click="openTask(row)"
              />
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <footer
      v-if="tasks.length"
      class="task-footer"
    >
      <el-button
        v-if="canManage && selected.length"
        type="danger"
        plain
        :icon="Delete"
        @click="removeTasks(selected)"
      >
        {{ t('annotationTasks.batchDelete', { count: selected.length }) }}
      </el-button>
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @change="syncUrl().then(loadTasks)"
      />
    </footer>
  </section>
</template>

<style scoped>
.annotation-tasks {
  --task-blue: #2676a8;
  --task-amber: #b7791f;
  --task-red: #b34242;
  --task-green: #38805c;
}

.status-tabs {
  margin-top: 10px;
}

.task-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.task-toolbar .filter-bar {
  flex: 1;
  flex-wrap: nowrap;
}

.task-toolbar .filter-bar .el-input {
  width: 160px;
}

.task-toolbar .filter-bar .el-select,
.task-toolbar .filter-bar .el-date-editor {
  width: 130px;
}

.status-board {
  display: grid;
  grid-auto-columns: minmax(230px, 1fr);
  grid-auto-flow: column;
  gap: 10px;
  min-height: 410px;
  overflow-x: auto;
  padding: 4px 0 14px;
}

.status-column {
  min-width: 230px;
  background: #f0f4f6;
  border: 1px solid var(--color-border);
  border-radius: 6px;
}

.status-column > header {
  display: flex;
  height: 44px;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border-bottom: 1px solid var(--color-border);
}

.status-column > header strong {
  flex: 1;
  font-size: 12px;
}

.status-column > header small {
  color: var(--color-text-muted);
  font-family: var(--font-mono);
}

.status-marker {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-text-muted);
}

.status-marker[data-status='WORKING'] { background: var(--task-blue); }
.status-marker[data-status='REVIEW_PENDING'] { background: var(--task-amber); }
.status-marker[data-status='REJECTED'] { background: var(--task-red); }
.status-marker[data-status='APPROVED'],
.status-marker[data-status='SUBMITTED'] { background: var(--task-green); }

.status-column__body {
  display: grid;
  align-content: start;
  gap: 8px;
  min-height: 360px;
  padding: 8px;
}

.status-column__body > p {
  margin: 24px 0;
  color: var(--color-text-muted);
  font-size: 12px;
  text-align: center;
}

.task-card {
  display: grid;
  gap: 8px;
  width: 100%;
  padding: 13px;
  color: inherit;
  text-align: left;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 5px;
  cursor: pointer;
  transition: border-color 150ms ease, transform 150ms ease;
}

.task-card:hover {
  border-color: var(--color-accent-soft);
  transform: translateY(-1px);
}

.task-card__project,
.task-card__people,
.task-card__meta {
  color: var(--color-text-secondary);
  font-size: 11px;
}

.task-card > strong {
  overflow-wrap: anywhere;
  font-size: 13px;
}

.task-card__progress {
  height: 3px;
  overflow: hidden;
  background: var(--color-border);
  border-radius: 2px;
}

.task-card__progress span {
  display: block;
  height: 100%;
  background: var(--color-accent);
}

.task-card__meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-family: var(--font-mono);
}

.task-link {
  padding: 0;
  color: var(--color-accent);
  font-weight: 600;
  background: none;
  border: 0;
  cursor: pointer;
}

.el-table small {
  display: block;
  margin-top: 3px;
  color: var(--color-text-muted);
}

.task-footer {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 14px;
}

@media (max-width: 860px) {
  .task-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .task-toolbar .filter-bar {
    flex-wrap: wrap;
  }

  .task-toolbar :deep(.el-segmented) {
    align-self: flex-end;
  }

  .task-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .task-footer :deep(.el-pagination) {
    justify-content: center;
    overflow-x: auto;
  }
}
</style>
