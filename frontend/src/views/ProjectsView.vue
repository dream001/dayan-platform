<script setup lang="ts">
import {
  Delete,
  Edit,
  MoreFilled,
  Plus,
  Refresh,
  User,
  View,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  changeProjectStatus,
  createProject,
  deleteProject,
  getProject,
  getProjectMembers,
  getProjects,
  getProjectOverview,
  getProjectUserOptions,
  removeProjectMember,
  saveProjectMember,
  updateProject,
} from '@/services/projects'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'
import type {
  DataAccessLevel,
  ProjectDetail,
  ProjectMember,
  ProjectPayload,
  ProjectRole,
  ProjectStatus,
  ProjectSummary,
  ProjectType,
  ProjectUserOption,
} from '@/types/project'
import { formatBytes, formatDateTime } from '@/utils/format'

const GIB = 1024 ** 3
const { t } = useI18n()
const auth = useAuthStore()

const loading = ref(false)
const error = ref('')
const projects = ref<ProjectSummary[]>([])
const total = ref(0)
const overview = reactive({ total: 0, active: 0, planning: 0, archived: 0 })
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  status: undefined as ProjectStatus | undefined,
  projectType: undefined as ProjectType | undefined,
})

const editorOpen = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const editorTab = ref('basic')
const editingId = ref<number | null>(null)
const saving = ref(false)
const quotaGb = ref(10)
const form = reactive<ProjectPayload>({
  code: '',
  name: '',
  description: '',
  projectType: 'TEAM',
  accessLevel: 'PRIVATE',
  storageProvider: 'MINIO',
  storageQuotaBytes: 10 * GIB,
  startDate: null,
  endDate: null,
  annotationGuideline: '',
  qualityThreshold: 90,
  reviewMode: 'SINGLE_REVIEW',
  notificationEnabled: true,
})

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<ProjectDetail | null>(null)
const detailTab = ref('overview')
const members = ref<ProjectMember[]>([])
const membersLoading = ref(false)

const memberDialogOpen = ref(false)
const memberSaving = ref(false)
const userOptions = ref<ProjectUserOption[]>([])
const memberForm = reactive({
  userId: null as number | null,
  role: 'ANNOTATOR' as ProjectRole,
  dataAccessLevel: 'READ_WRITE' as DataAccessLevel,
  validFrom: null as string | null,
  validUntil: null as string | null,
})

const canCreate = computed(() => auth.hasPermission('basic:project:create'))

const statusTransitions: Record<ProjectStatus, ProjectStatus[]> = {
  PLANNING: ['ACTIVE', 'ARCHIVED'],
  ACTIVE: ['SUSPENDED', 'COMPLETED', 'ARCHIVED'],
  SUSPENDED: ['ACTIVE', 'ARCHIVED'],
  COMPLETED: ['ARCHIVED'],
  ARCHIVED: [],
}

const statusType: Record<ProjectStatus, '' | 'success' | 'warning' | 'info' | 'danger'> = {
  PLANNING: 'info',
  ACTIVE: 'success',
  SUSPENDED: 'warning',
  COMPLETED: '',
  ARCHIVED: 'info',
}

function projectStatusType(status: ProjectStatus) {
  return statusType[status]
}

function nextStatuses(status: ProjectStatus) {
  return statusTransitions[status]
}

function requestParams() {
  return {
    page: query.page,
    size: query.size,
    keyword: query.keyword.trim() || undefined,
    status: query.status,
    projectType: query.projectType,
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [page, stats] = await Promise.all([getProjects(requestParams()), getProjectOverview()])
    projects.value = page.items
    total.value = page.total
    Object.assign(overview, stats)
  } catch (reason) {
    error.value = getErrorMessage(reason, t('projects.loadFailed'))
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  void load()
}

function resetFilters() {
  query.keyword = ''
  query.status = undefined
  query.projectType = undefined
  search()
}

function resetForm() {
  Object.assign(form, {
    code: '',
    name: '',
    description: '',
    projectType: 'TEAM',
    accessLevel: 'PRIVATE',
    storageProvider: 'MINIO',
    storageQuotaBytes: 10 * GIB,
    startDate: null,
    endDate: null,
    annotationGuideline: '',
    qualityThreshold: 90,
    reviewMode: 'SINGLE_REVIEW',
    notificationEnabled: true,
  })
  quotaGb.value = 10
  editingId.value = null
  editorTab.value = 'basic'
}

function openCreate() {
  resetForm()
  editorMode.value = 'create'
  editorOpen.value = true
}

async function openEdit(project: ProjectSummary) {
  saving.value = true
  try {
    const value = await getProject(project.id)
    resetForm()
    editorMode.value = 'edit'
    editingId.value = project.id
    Object.assign(form, {
      code: value.summary.code,
      name: value.summary.name,
      description: value.summary.description ?? '',
      projectType: value.summary.projectType,
      accessLevel: value.summary.accessLevel,
      storageProvider: value.storageProvider,
      storageQuotaBytes: value.summary.storageQuotaBytes,
      startDate: value.summary.startDate,
      endDate: value.summary.endDate,
      annotationGuideline: value.annotationGuideline ?? '',
      qualityThreshold: value.qualityThreshold,
      reviewMode: value.reviewMode,
      notificationEnabled: value.notificationEnabled,
    })
    quotaGb.value = Math.max(1, Math.round(value.summary.storageQuotaBytes / GIB))
    editorOpen.value = true
  } catch (reason) {
    notifyError(reason, t('projects.detailFailed'))
  } finally {
    saving.value = false
  }
}

async function saveProject() {
  if (!form.name.trim() || !form.code.trim() || quotaGb.value <= 0) {
    ElMessage.warning(t('projects.validation'))
    return
  }
  if (form.startDate && form.endDate && form.startDate > form.endDate) {
    ElMessage.warning(t('projects.invalidDates'))
    return
  }
  saving.value = true
  const payload = {
    ...form,
    code: form.code.trim(),
    name: form.name.trim(),
    description: form.description.trim(),
    annotationGuideline: form.annotationGuideline.trim(),
    storageQuotaBytes: quotaGb.value * GIB,
  }
  try {
    if (editorMode.value === 'create') {
      await createProject(payload)
      ElMessage.success(t('projects.created'))
    } else if (editingId.value) {
      await updateProject(editingId.value, payload)
      ElMessage.success(t('projects.updated'))
    }
    editorOpen.value = false
    await load()
    if (detailOpen.value && editingId.value) await openDetail(editingId.value)
  } catch (reason) {
    notifyError(reason, t('projects.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function openDetail(id: number) {
  detailOpen.value = true
  detailLoading.value = true
  detailTab.value = 'overview'
  members.value = []
  try {
    detail.value = await getProject(id)
  } catch (reason) {
    detail.value = null
    notifyError(reason, t('projects.detailFailed'))
  } finally {
    detailLoading.value = false
  }
}

async function loadMembers() {
  if (!detail.value) return
  membersLoading.value = true
  try {
    members.value = await getProjectMembers(detail.value.summary.id)
  } catch (reason) {
    notifyError(reason, t('projects.membersLoadFailed'))
  } finally {
    membersLoading.value = false
  }
}

function selectDetailTab(name: string | number) {
  if (name === 'members' && !members.value.length) void loadMembers()
}

async function transition(project: ProjectSummary, status: ProjectStatus) {
  const confirmed = await confirmAction(
    t('projects.statusConfirm', {
      name: project.name,
      status: t(`projects.statuses.${status}`),
    }),
    t('projects.changeStatus'),
    t('common.confirm'),
  )
  if (!confirmed) return
  try {
    const updated = await changeProjectStatus(project.id, status)
    ElMessage.success(t('projects.statusChanged'))
    if (detail.value?.summary.id === project.id) detail.value = updated
    await load()
  } catch (reason) {
    notifyError(reason, t('projects.statusFailed'))
  }
}

async function remove(project: ProjectSummary) {
  const confirmed = await confirmAction(
    t('projects.deleteConfirm', { name: project.name }),
    t('projects.deleteTitle'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    await deleteProject(project.id)
    ElMessage.success(t('projects.deleted'))
    if (detail.value?.summary.id === project.id) detailOpen.value = false
    await load()
  } catch (reason) {
    notifyError(reason, t('projects.deleteFailed'))
  }
}

async function openMemberDialog(member?: ProjectMember) {
  if (!detail.value) return
  Object.assign(memberForm, {
    userId: member?.userId ?? null,
    role: member?.role ?? 'ANNOTATOR',
    dataAccessLevel: member?.dataAccessLevel ?? 'READ_WRITE',
    validFrom: member?.validFrom ?? null,
    validUntil: member?.validUntil ?? null,
  })
  try {
    userOptions.value = await getProjectUserOptions(detail.value.summary.id)
    memberDialogOpen.value = true
  } catch (reason) {
    notifyError(reason, t('projects.userOptionsFailed'))
  }
}

async function saveMember() {
  if (!detail.value || !memberForm.userId) {
    ElMessage.warning(t('projects.memberRequired'))
    return
  }
  if (memberForm.validFrom && memberForm.validUntil && memberForm.validFrom >= memberForm.validUntil) {
    ElMessage.warning(t('projects.invalidMemberDates'))
    return
  }
  memberSaving.value = true
  try {
    await saveProjectMember(detail.value.summary.id, {
      userId: memberForm.userId,
      role: memberForm.role,
      dataAccessLevel: memberForm.dataAccessLevel,
      validFrom: memberForm.validFrom,
      validUntil: memberForm.validUntil,
    })
    ElMessage.success(t('projects.memberSaved'))
    memberDialogOpen.value = false
    await Promise.all([loadMembers(), refreshDetail()])
  } catch (reason) {
    notifyError(reason, t('projects.memberSaveFailed'))
  } finally {
    memberSaving.value = false
  }
}

async function removeMember(member: ProjectMember) {
  if (!detail.value) return
  const confirmed = await confirmAction(
    t('projects.removeMemberConfirm', { name: member.displayName }),
    t('projects.removeMember'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    await removeProjectMember(detail.value.summary.id, member.userId)
    ElMessage.success(t('projects.memberRemoved'))
    await Promise.all([loadMembers(), refreshDetail()])
  } catch (reason) {
    notifyError(reason, t('projects.memberRemoveFailed'))
  }
}

async function refreshDetail() {
  if (detail.value) detail.value = await getProject(detail.value.summary.id)
}

onMounted(load)
</script>

<template>
  <section class="admin-page projects-page">
    <PageHeader
      :title="t('projects.title')"
      :eyebrow="t('projects.eyebrow')"
      :description="t('projects.description')"
    >
      <template #actions>
        <el-tooltip :content="t('projects.refresh')">
          <el-button
            :icon="Refresh"
            circle
            :loading="loading"
            :aria-label="t('projects.refresh')"
            @click="load"
          />
        </el-tooltip>
        <el-button
          v-if="canCreate"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          {{ t('projects.create') }}
        </el-button>
      </template>
    </PageHeader>

    <div class="project-overview">
      <div>
        <span>{{ t('projects.total') }}</span>
        <strong>{{ overview.total }}</strong>
      </div>
      <div>
        <span>{{ t('projects.active') }}</span>
        <strong>{{ overview.active }}</strong>
      </div>
      <div>
        <span>{{ t('projects.planning') }}</span>
        <strong>{{ overview.planning }}</strong>
      </div>
      <div>
        <span>{{ t('projects.archived') }}</span>
        <strong>{{ overview.archived }}</strong>
      </div>
    </div>

    <form
      class="filter-bar"
      @submit.prevent="search"
    >
      <el-input
        v-model="query.keyword"
        clearable
        :placeholder="t('projects.keyword')"
      />
      <el-select
        v-model="query.status"
        clearable
        :placeholder="t('projects.allStatuses')"
      >
        <el-option
          v-for="status in Object.keys(statusTransitions)"
          :key="status"
          :label="t(`projects.statuses.${status}`)"
          :value="status"
        />
      </el-select>
      <el-select
        v-model="query.projectType"
        clearable
        :placeholder="t('projects.allTypes')"
      >
        <el-option
          v-for="type in ['PERSONAL', 'TEAM', 'SHARED']"
          :key="type"
          :label="t(`projects.types.${type}`)"
          :value="type"
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
    </form>

    <StatePanel
      v-if="loading && !projects.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !projects.length"
      state="error"
      :title="t('projects.loadFailed')"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!projects.length"
      state="empty"
      :title="t('projects.empty')"
      :description="t('projects.emptyDesc')"
    />
    <template v-else>
      <div class="table-shell">
        <el-table
          v-loading="loading"
          :data="projects"
          row-key="id"
          @row-click="(row: ProjectSummary) => openDetail(row.id)"
        >
          <el-table-column
            :label="t('projects.project')"
            min-width="240"
            fixed="left"
          >
            <template #default="{ row }">
              <div class="project-cell">
                <span class="project-cell__marker" />
                <div>
                  <strong>{{ row.name }}</strong>
                  <span>{{ row.code }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('projects.typeAccess')"
            min-width="155"
          >
            <template #default="{ row }">
              <div class="stacked-cell">
                <span>{{ t(`projects.types.${row.projectType}`) }}</span>
                <small>{{ t(`projects.access.${row.accessLevel}`) }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('projects.period')"
            min-width="190"
          >
            <template #default="{ row }">
              <span class="date-range">{{ row.startDate || '—' }} / {{ row.endDate || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('projects.resources')"
            min-width="150"
          >
            <template #default="{ row }">
              <div class="stacked-cell">
                <span>{{ t('projects.memberCount', { count: row.memberCount }) }}</span>
                <small>{{ formatBytes(row.storageQuotaBytes) }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('common.status')"
            width="108"
          >
            <template #default="{ row }">
              <el-tag
                :type="projectStatusType(row.status)"
                effect="plain"
                size="small"
              >
                {{ t(`projects.statuses.${row.status}`) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('common.operation')"
            width="138"
            fixed="right"
          >
            <template #default="{ row }">
              <div
                class="table-actions"
                @click.stop
              >
                <el-tooltip :content="t('projects.view')">
                  <el-button
                    :icon="View"
                    circle
                    text
                    :aria-label="t('projects.view')"
                    @click="openDetail(row.id)"
                  />
                </el-tooltip>
                <el-tooltip
                  v-if="row.canEdit"
                  :content="t('common.edit')"
                >
                  <el-button
                    :icon="Edit"
                    circle
                    text
                    :aria-label="t('common.edit')"
                    @click="openEdit(row)"
                  />
                </el-tooltip>
                <el-dropdown
                  v-if="nextStatuses(row.status).length"
                  trigger="click"
                  @command="(status: ProjectStatus) => transition(row, status)"
                >
                  <el-button
                    :icon="MoreFilled"
                    circle
                    text
                    :aria-label="t('projects.changeStatus')"
                  />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item
                        v-for="status in nextStatuses(row.status)"
                        :key="status"
                        :command="status"
                      >
                        {{ t(`projects.moveTo.${status}`) }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-tooltip
                  v-if="row.canDelete"
                  :content="t('common.delete')"
                >
                  <el-button
                    :icon="Delete"
                    circle
                    text
                    type="danger"
                    :aria-label="t('common.delete')"
                    @click="remove(row)"
                  />
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @change="load"
        />
      </div>
    </template>

    <el-drawer
      v-model="editorOpen"
      :title="editorMode === 'create' ? t('projects.create') : t('projects.edit')"
      size="560px"
      destroy-on-close
    >
      <el-form
        class="drawer-form"
        label-position="top"
        @submit.prevent="saveProject"
      >
        <el-tabs v-model="editorTab">
          <el-tab-pane
            :label="t('projects.basicConfiguration')"
            name="basic"
          >
            <div class="form-grid">
              <el-form-item
                :label="t('projects.name')"
                required
              >
                <el-input
                  v-model="form.name"
                  maxlength="120"
                />
              </el-form-item>
              <el-form-item
                :label="t('projects.code')"
                required
              >
                <el-input
                  v-model="form.code"
                  maxlength="64"
                  :disabled="editorMode === 'edit'"
                />
              </el-form-item>
              <el-form-item
                class="form-grid__wide"
                :label="t('projects.projectDescription')"
              >
                <el-input
                  v-model="form.description"
                  type="textarea"
                  :rows="3"
                  maxlength="1000"
                  show-word-limit
                />
              </el-form-item>
              <el-form-item :label="t('projects.projectType')">
                <el-select
                  v-model="form.projectType"
                  style="width: 100%"
                >
                  <el-option
                    v-for="type in ['PERSONAL', 'TEAM', 'SHARED']"
                    :key="type"
                    :label="t(`projects.types.${type}`)"
                    :value="type"
                  />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('projects.accessLevel')">
                <el-select
                  v-model="form.accessLevel"
                  style="width: 100%"
                >
                  <el-option
                    v-for="access in ['PUBLIC', 'PRIVATE', 'RESTRICTED']"
                    :key="access"
                    :label="t(`projects.access.${access}`)"
                    :value="access"
                  />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('projects.startDate')">
                <el-date-picker
                  v-model="form.startDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item :label="t('projects.endDate')">
                <el-date-picker
                  v-model="form.endDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item :label="t('projects.storageProvider')">
                <el-input
                  v-model="form.storageProvider"
                  maxlength="32"
                />
              </el-form-item>
              <el-form-item :label="t('projects.storageQuota')">
                <el-input-number
                  v-model="quotaGb"
                  :min="1"
                  :max="1048576"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </div>
          </el-tab-pane>
          <el-tab-pane
            :label="t('projects.qualityWorkflow')"
            name="quality"
          >
            <el-form-item :label="t('projects.annotationGuideline')">
              <el-input
                v-model="form.annotationGuideline"
                type="textarea"
                :rows="7"
                maxlength="10000"
                show-word-limit
              />
            </el-form-item>
            <div class="form-grid">
              <el-form-item :label="t('projects.qualityThreshold')">
                <el-input-number
                  v-model="form.qualityThreshold"
                  :min="0"
                  :max="100"
                  :precision="2"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item :label="t('projects.reviewMode')">
                <el-select
                  v-model="form.reviewMode"
                  style="width: 100%"
                >
                  <el-option
                    v-for="mode in ['NONE', 'SINGLE_REVIEW', 'DOUBLE_REVIEW']"
                    :key="mode"
                    :label="t(`projects.reviewModes.${mode}`)"
                    :value="mode"
                  />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item>
              <el-checkbox v-model="form.notificationEnabled">
                {{ t('projects.enableNotifications') }}
              </el-checkbox>
            </el-form-item>
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="editorOpen = false">
            {{ t('common.cancel') }}
          </el-button>
          <el-button
            type="primary"
            :loading="saving"
            @click="saveProject"
          >
            {{ t('common.save') }}
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer
      v-model="detailOpen"
      size="720px"
      destroy-on-close
    >
      <template #header>
        <div
          v-if="detail"
          class="detail-heading"
        >
          <span>{{ detail.summary.code }}</span>
          <strong>{{ detail.summary.name }}</strong>
        </div>
      </template>
      <StatePanel
        v-if="detailLoading"
        state="loading"
      />
      <el-tabs
        v-else-if="detail"
        v-model="detailTab"
        @tab-change="selectDetailTab"
      >
        <el-tab-pane
          :label="t('projects.overviewTab')"
          name="overview"
        >
          <div class="detail-actions">
            <el-button
              v-if="detail.summary.canEdit"
              :icon="Edit"
              @click="openEdit(detail.summary)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-dropdown
              v-if="nextStatuses(detail.summary.status).length"
              @command="(status: ProjectStatus) => transition(detail!.summary, status)"
            >
              <el-button :icon="MoreFilled">
                {{ t('projects.changeStatus') }}
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="status in nextStatuses(detail.summary.status)"
                    :key="status"
                    :command="status"
                  >
                    {{ t(`projects.moveTo.${status}`) }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <dl class="project-facts">
            <div>
              <dt>{{ t('common.status') }}</dt>
              <dd>{{ t(`projects.statuses.${detail.summary.status}`) }}</dd>
            </div>
            <div>
              <dt>{{ t('projects.owner') }}</dt>
              <dd>{{ detail.ownerName }}</dd>
            </div>
            <div>
              <dt>{{ t('projects.projectType') }}</dt>
              <dd>{{ t(`projects.types.${detail.summary.projectType}`) }}</dd>
            </div>
            <div>
              <dt>{{ t('projects.accessLevel') }}</dt>
              <dd>{{ t(`projects.access.${detail.summary.accessLevel}`) }}</dd>
            </div>
            <div>
              <dt>{{ t('projects.storage') }}</dt>
              <dd>{{ detail.storageProvider }} · {{ formatBytes(detail.summary.storageQuotaBytes) }}</dd>
            </div>
            <div>
              <dt>{{ t('projects.period') }}</dt>
              <dd>{{ detail.summary.startDate || '—' }} / {{ detail.summary.endDate || '—' }}</dd>
            </div>
            <div>
              <dt>{{ t('projects.qualityThreshold') }}</dt>
              <dd>{{ detail.qualityThreshold }}%</dd>
            </div>
            <div>
              <dt>{{ t('projects.reviewMode') }}</dt>
              <dd>{{ t(`projects.reviewModes.${detail.reviewMode}`) }}</dd>
            </div>
          </dl>
          <section class="detail-section">
            <h3>{{ t('projects.projectDescription') }}</h3>
            <p>{{ detail.summary.description || t('projects.notConfigured') }}</p>
          </section>
          <section class="detail-section">
            <h3>{{ t('projects.annotationGuideline') }}</h3>
            <p class="pre-line">
              {{ detail.annotationGuideline || t('projects.notConfigured') }}
            </p>
          </section>
          <p class="detail-meta">
            {{ t('projects.updatedAt') }} {{ formatDateTime(detail.summary.updatedAt) }}
          </p>
        </el-tab-pane>
        <el-tab-pane
          :label="t('projects.membersTab', { count: detail.summary.memberCount })"
          name="members"
        >
          <div class="member-toolbar">
            <span>{{ t('projects.membersHint') }}</span>
            <el-button
              v-if="detail.summary.canManageMembers"
              type="primary"
              :icon="User"
              @click="openMemberDialog()"
            >
              {{ t('projects.addMember') }}
            </el-button>
          </div>
          <el-table
            v-loading="membersLoading"
            class="member-table-desktop"
            :data="members"
            row-key="userId"
          >
            <el-table-column
              :label="t('projects.member')"
              min-width="170"
            >
              <template #default="{ row }">
                <div class="stacked-cell">
                  <span>{{ row.displayName }}</span>
                  <small>@{{ row.username }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              :label="t('projects.memberRole')"
              min-width="145"
            >
              <template #default="{ row }">
                {{ t(`projects.roles.${row.role}`) }}
              </template>
            </el-table-column>
            <el-table-column
              :label="t('projects.dataAccess')"
              min-width="120"
            >
              <template #default="{ row }">
                {{ t(`projects.dataAccessLevels.${row.dataAccessLevel}`) }}
              </template>
            </el-table-column>
            <el-table-column
              :label="t('common.status')"
              width="86"
            >
              <template #default="{ row }">
                <el-tag
                  :type="row.active ? 'success' : 'info'"
                  size="small"
                  effect="plain"
                >
                  {{ row.active ? t('projects.valid') : t('projects.expired') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              v-if="detail.summary.canManageMembers"
              :label="t('common.operation')"
              width="88"
            >
              <template #default="{ row }">
                <el-button
                  :icon="Edit"
                  circle
                  text
                  :aria-label="t('common.edit')"
                  @click="openMemberDialog(row)"
                />
                <el-button
                  :icon="Delete"
                  circle
                  text
                  type="danger"
                  :aria-label="t('projects.removeMember')"
                  @click="removeMember(row)"
                />
              </template>
            </el-table-column>
          </el-table>
          <div
            v-loading="membersLoading"
            class="member-list-mobile"
          >
            <article
              v-for="member in members"
              :key="member.userId"
            >
              <div class="member-list-mobile__identity">
                <strong>{{ member.displayName }}</strong>
                <span>@{{ member.username }}</span>
              </div>
              <dl>
                <div>
                  <dt>{{ t('projects.memberRole') }}</dt>
                  <dd>{{ t(`projects.roles.${member.role}`) }}</dd>
                </div>
                <div>
                  <dt>{{ t('projects.dataAccess') }}</dt>
                  <dd>{{ t(`projects.dataAccessLevels.${member.dataAccessLevel}`) }}</dd>
                </div>
              </dl>
              <div class="member-list-mobile__actions">
                <el-tag
                  :type="member.active ? 'success' : 'info'"
                  size="small"
                  effect="plain"
                >
                  {{ member.active ? t('projects.valid') : t('projects.expired') }}
                </el-tag>
                <span v-if="detail.summary.canManageMembers">
                  <el-button
                    :icon="Edit"
                    circle
                    text
                    :aria-label="t('common.edit')"
                    @click="openMemberDialog(member)"
                  />
                  <el-button
                    :icon="Delete"
                    circle
                    text
                    type="danger"
                    :aria-label="t('projects.removeMember')"
                    @click="removeMember(member)"
                  />
                </span>
              </div>
            </article>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <el-dialog
      v-model="memberDialogOpen"
      :title="t('projects.memberConfiguration')"
      width="480px"
    >
      <el-form
        class="dialog-form"
        label-position="top"
        @submit.prevent="saveMember"
      >
        <el-form-item
          :label="t('projects.member')"
          required
        >
          <el-select
            v-model="memberForm.userId"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="`${user.displayName} (@${user.username})`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item :label="t('projects.memberRole')">
            <el-select
              v-model="memberForm.role"
              style="width: 100%"
            >
              <el-option
                v-for="role in ['PROJECT_ADMIN', 'PROJECT_MANAGER', 'ANNOTATOR', 'REVIEWER', 'OBSERVER']"
                :key="role"
                :label="t(`projects.roles.${role}`)"
                :value="role"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('projects.dataAccess')">
            <el-select
              v-model="memberForm.dataAccessLevel"
              style="width: 100%"
            >
              <el-option
                v-for="access in ['READ_ONLY', 'READ_WRITE', 'FULL']"
                :key="access"
                :label="t(`projects.dataAccessLevels.${access}`)"
                :value="access"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('projects.validFrom')">
            <el-date-picker
              v-model="memberForm.validFrom"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ssZ"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item :label="t('projects.validUntil')">
            <el-date-picker
              v-model="memberForm.validUntil"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ssZ"
              style="width: 100%"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="memberDialogOpen = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="memberSaving"
          @click="saveMember"
        >
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.project-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-bottom: 1px solid var(--color-border);
}

.project-overview > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  min-height: 78px;
  padding: 20px 24px;
  border-right: 1px solid var(--color-border);
}

.project-overview > div:first-child {
  padding-left: 0;
}

.project-overview > div:last-child {
  border-right: 0;
}

.project-overview span {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.project-overview strong {
  color: var(--color-ink);
  font-family: var(--font-mono);
  font-size: 26px;
  font-weight: 620;
}

.projects-page :deep(.el-table__row) {
  cursor: pointer;
}

.project-cell {
  display: flex;
  align-items: center;
  gap: 11px;
}

.project-cell__marker {
  width: 3px;
  height: 30px;
  flex: 0 0 auto;
  background: var(--color-accent);
}

.project-cell div,
.stacked-cell,
.detail-heading {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.project-cell strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-cell span:last-child,
.stacked-cell small,
.detail-heading span,
.date-range {
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.form-grid__wide {
  grid-column: 1 / -1;
}

.detail-heading strong {
  color: var(--color-ink);
  font-size: 20px;
  font-weight: 650;
}

.detail-actions,
.member-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.project-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  border-top: 1px solid var(--color-border);
  border-left: 1px solid var(--color-border);
}

.project-facts div {
  min-height: 76px;
  padding: 14px 16px;
  border-right: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}

.project-facts dt {
  margin-bottom: 7px;
  color: var(--color-text-muted);
  font-size: 11px;
}

.project-facts dd {
  margin: 0;
  color: var(--color-ink);
  font-size: 13px;
  font-weight: 600;
}

.detail-section {
  padding: 22px 0 4px;
  border-bottom: 1px solid var(--color-border);
}

.detail-section h3 {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 650;
}

.detail-section p,
.member-toolbar span,
.detail-meta {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.pre-line {
  white-space: pre-line;
}

.detail-meta {
  margin-top: 18px;
}

.member-list-mobile {
  display: none;
}

@media (max-width: 700px) {
  .project-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .project-overview > div {
    min-height: 64px;
    padding: 14px;
    border-bottom: 1px solid var(--color-border);
  }

  .project-overview > div:first-child {
    padding-left: 14px;
  }

  .form-grid,
  .project-facts {
    grid-template-columns: 1fr;
  }

  .member-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .member-table-desktop {
    display: none;
  }

  .member-list-mobile {
    display: grid;
    gap: 0;
    border-top: 1px solid var(--color-border);
  }

  .member-list-mobile article {
    padding: 16px 0;
    border-bottom: 1px solid var(--color-border);
  }

  .member-list-mobile__identity {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .member-list-mobile__identity strong {
    font-size: 13px;
  }

  .member-list-mobile__identity span {
    color: var(--color-text-muted);
    font-family: var(--font-mono);
    font-size: 10px;
  }

  .member-list-mobile dl {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
    margin: 14px 0;
  }

  .member-list-mobile dl div {
    min-width: 0;
  }

  .member-list-mobile dt {
    margin-bottom: 4px;
    color: var(--color-text-muted);
    font-size: 10px;
  }

  .member-list-mobile dd {
    margin: 0;
    font-size: 12px;
  }

  .member-list-mobile__actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
}
</style>
