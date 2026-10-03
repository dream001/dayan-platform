<script setup lang="ts">
import {
  Delete,
  Edit,
  Plus,
  Refresh,
  Search,
  VideoPlay,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  createQualityRule,
  deleteQualityRule,
  getQualityDatasets,
  getQualityLogs,
  getQualityOverview,
  getQualityProjects,
  getQualityRules,
  overrideQualityExecution,
  runQualityCheck,
  updateQualityRule,
} from '@/services/quality-control'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'
import type {
  QualityAssertion,
  QualityDatasetOption,
  QualityExecution,
  QualityExecutionStatus,
  QualityProjectOption,
  QualityRule,
  QualityRulePayload,
  QualityRuleScope,
} from '@/types/quality-control'
import { formatDateTime } from '@/utils/format'

const globalMetrics = [
  'record_duration_sec',
  'timestamp_monotonic_violations',
  'frame_rate',
  'frame_gap_median_ms',
  'frame_gap_p95_ms',
  'frame_gap_p99_ms',
  'frame_gap_max_ms',
  'drop_frame_count',
  'cross_topic_sync_p95_ms',
  'cross_topic_sync_p99_ms',
  'cross_topic_sync_max_ms',
  'leading_joint_still_sec',
  'trailing_joint_still_sec',
  'blur_score_p90',
  'exposure_outlier_ratio',
]
const topicMetrics = [
  'frequency_hz',
  'topic_frame_gap_max_ms',
  'message_count',
  'duration_sec',
  'first_ts_sec',
  'last_ts_sec',
]
const statuses: QualityExecutionStatus[] = [
  'QUEUED',
  'RUNNING',
  'PASSED',
  'FAILED',
  'ERROR',
  'CANCELLED',
]

const { t } = useI18n()
const auth = useAuthStore()
const activeTab = ref('rules')
const loading = ref(false)
const error = ref('')
const rules = ref<QualityRule[]>([])
const logs = ref<QualityExecution[]>([])
const projects = ref<QualityProjectOption[]>([])
const datasets = ref<QualityDatasetOption[]>([])
const ruleTotal = ref(0)
const logTotal = ref(0)
const overview = reactive({
  totalRules: 0,
  enabledRules: 0,
  queued: 0,
  passed: 0,
  failed: 0,
  overridden: 0,
  canManageGlobal: false,
})
const ruleQuery = reactive({
  page: 1,
  size: 20,
  projectId: undefined as number | undefined,
  enabled: undefined as boolean | undefined,
  keyword: '',
})
const logQuery = reactive({
  page: 1,
  size: 20,
  projectId: undefined as number | undefined,
  datasetId: undefined as number | undefined,
  status: undefined as QualityExecutionStatus | undefined,
  effectivePass: undefined as boolean | undefined,
  overridden: undefined as boolean | undefined,
  keyword: '',
})

const editorOpen = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive<QualityRulePayload>({
  name: '',
  description: '',
  scope: 'PROJECT',
  projectId: null,
  datasetPattern: '*',
  enabled: true,
  priority: 100,
  assertions: [],
})

const runOpen = ref(false)
const runDatasetId = ref<number | null>(null)
const running = ref(false)
const overrideOpen = ref(false)
const overrideExecution = ref<QualityExecution | null>(null)
const overridePassed = ref<boolean | null>(null)
const overrideReason = ref('')
const overriding = ref(false)

const canManageRules = computed(() => auth.hasPermission('data:qc:rule:manage'))
const canExecute = computed(() => auth.hasPermission('data:qc:execute'))
const canOverride = computed(() => auth.hasPermission('data:qc:override'))

function emptyAssertion(): QualityAssertion {
  return {
    type: 'NUMERIC',
    metric: 'record_duration_sec',
    operator: '>=',
    threshold: 3,
    severity: 'ERROR',
    metricScope: 'ALL',
    matchPattern: '',
  }
}

function metricOptions(assertion: QualityAssertion) {
  return assertion.metricScope === 'ALL' ? globalMetrics : topicMetrics
}

function changeAssertionType(assertion: QualityAssertion) {
  if (assertion.type === 'NUMERIC') {
    assertion.metricScope = 'ALL'
    assertion.metric = 'record_duration_sec'
    assertion.operator = '>='
    assertion.threshold = 3
    assertion.matchPattern = ''
  } else {
    assertion.metric = ''
    assertion.operator = ''
    assertion.threshold = null
    assertion.metricScope = 'TOPIC'
    assertion.matchPattern = ''
  }
}

function changeMetricScope(assertion: QualityAssertion) {
  assertion.metric = assertion.metricScope === 'ALL' ? 'record_duration_sec' : 'frequency_hz'
  if (assertion.metricScope === 'ALL') assertion.matchPattern = ''
}

async function initialize() {
  loading.value = true
  error.value = ''
  try {
    const [summary, projectRows, datasetRows] = await Promise.all([
      getQualityOverview(),
      getQualityProjects(),
      getQualityDatasets(),
    ])
    Object.assign(overview, summary)
    projects.value = projectRows
    datasets.value = datasetRows
    await Promise.all([loadRules(), loadLogs()])
  } catch (reason) {
    error.value = getErrorMessage(reason, t('qualityControl.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function refreshOverview() {
  Object.assign(overview, await getQualityOverview())
}

async function loadRules() {
  loading.value = true
  try {
    const page = await getQualityRules({
      page: ruleQuery.page,
      size: ruleQuery.size,
      projectId: ruleQuery.projectId,
      enabled: ruleQuery.enabled,
      keyword: ruleQuery.keyword.trim() || undefined,
    })
    rules.value = page.items
    ruleTotal.value = page.total
  } catch (reason) {
    notifyError(reason, t('qualityControl.rulesLoadFailed'))
  } finally {
    loading.value = false
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const page = await getQualityLogs({
      page: logQuery.page,
      size: logQuery.size,
      projectId: logQuery.projectId,
      datasetId: logQuery.datasetId,
      status: logQuery.status,
      effectivePass: logQuery.effectivePass,
      overridden: logQuery.overridden,
      keyword: logQuery.keyword.trim() || undefined,
    })
    logs.value = page.items
    logTotal.value = page.total
  } catch (reason) {
    notifyError(reason, t('qualityControl.logsLoadFailed'))
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await Promise.all([refreshOverview(), loadRules(), loadLogs()])
}

function resetForm() {
  Object.assign(form, {
    name: '',
    description: '',
    scope: 'PROJECT',
    projectId: projects.value[0]?.id ?? null,
    datasetPattern: '*',
    enabled: true,
    priority: 100,
    assertions: [emptyAssertion()],
  })
  editingId.value = null
}

function openCreate() {
  resetForm()
  editorOpen.value = true
}

function openEdit(rule: QualityRule) {
  editingId.value = rule.id
  Object.assign(form, {
    name: rule.name,
    description: rule.description ?? '',
    scope: rule.scope,
    projectId: rule.projectId,
    datasetPattern: rule.datasetPattern,
    enabled: rule.enabled,
    priority: rule.priority,
    assertions: rule.assertions.map((assertion) => ({ ...assertion })),
  })
  editorOpen.value = true
}

function changeScope(scope: QualityRuleScope) {
  form.projectId = scope === 'GLOBAL' ? null : (projects.value[0]?.id ?? null)
}

function addAssertion() {
  form.assertions.push(emptyAssertion())
}

function removeAssertion(index: number) {
  form.assertions.splice(index, 1)
}

async function saveRule() {
  if (!form.name.trim() || !form.datasetPattern.trim()) {
    ElMessage.warning(t('qualityControl.ruleRequired'))
    return
  }
  if (form.scope === 'PROJECT' && !form.projectId) {
    ElMessage.warning(t('qualityControl.projectRequired'))
    return
  }
  saving.value = true
  const payload: QualityRulePayload = {
    ...form,
    name: form.name.trim(),
    description: form.description.trim(),
    datasetPattern: form.datasetPattern.trim(),
    projectId: form.scope === 'PROJECT' ? form.projectId : null,
    assertions: form.assertions.map((assertion) => ({ ...assertion })),
  }
  try {
    if (editingId.value) {
      await updateQualityRule(editingId.value, payload)
      ElMessage.success(t('qualityControl.ruleUpdated'))
    } else {
      await createQualityRule(payload)
      ElMessage.success(t('qualityControl.ruleCreated'))
    }
    editorOpen.value = false
    await Promise.all([refreshOverview(), loadRules()])
  } catch (reason) {
    notifyError(reason, t('qualityControl.ruleSaveFailed'))
  } finally {
    saving.value = false
  }
}

async function removeRule(rule: QualityRule) {
  const confirmed = await confirmAction(
    t('qualityControl.deleteConfirm', { name: rule.name }),
    t('qualityControl.deleteRule'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    await deleteQualityRule(rule.id)
    ElMessage.success(t('qualityControl.ruleDeleted'))
    await Promise.all([refreshOverview(), loadRules(), loadLogs()])
  } catch (reason) {
    notifyError(reason, t('qualityControl.ruleDeleteFailed'))
  }
}

function openRun() {
  runDatasetId.value = datasets.value[0]?.id ?? null
  runOpen.value = true
}

async function runCheck() {
  if (!runDatasetId.value) {
    ElMessage.warning(t('qualityControl.datasetRequired'))
    return
  }
  running.value = true
  try {
    const result = await runQualityCheck(runDatasetId.value)
    ElMessage.success(t('qualityControl.runQueued', {
      queued: result.queued,
      skipped: result.skipped,
    }))
    runOpen.value = false
    activeTab.value = 'logs'
    await Promise.all([refreshOverview(), loadLogs()])
  } catch (reason) {
    notifyError(reason, t('qualityControl.runFailed'))
  } finally {
    running.value = false
  }
}

function openOverride(execution: QualityExecution) {
  overrideExecution.value = execution
  overridePassed.value = execution.overridePass ?? execution.effectivePass
  overrideReason.value = execution.overrideReason ?? ''
  overrideOpen.value = true
}

async function saveOverride() {
  if (!overrideExecution.value) return
  if (overridePassed.value !== null && !overrideReason.value.trim()) {
    ElMessage.warning(t('qualityControl.overrideReasonRequired'))
    return
  }
  overriding.value = true
  try {
    await overrideQualityExecution(
      overrideExecution.value.id,
      overridePassed.value,
      overridePassed.value === null ? null : overrideReason.value.trim(),
    )
    ElMessage.success(t('qualityControl.overrideSaved'))
    overrideOpen.value = false
    await Promise.all([refreshOverview(), loadLogs()])
  } catch (reason) {
    notifyError(reason, t('qualityControl.overrideFailed'))
  } finally {
    overriding.value = false
  }
}

function statusType(status: QualityExecutionStatus) {
  if (status === 'PASSED') return 'success'
  if (status === 'FAILED' || status === 'ERROR') return 'danger'
  if (status === 'QUEUED' || status === 'RUNNING') return 'warning'
  return 'info'
}

onMounted(initialize)
</script>

<template>
  <section class="admin-page quality-page">
    <PageHeader
      :title="t('qualityControl.title')"
      :eyebrow="t('qualityControl.eyebrow')"
      :description="t('qualityControl.description')"
    >
      <template #actions>
        <el-tooltip :content="t('common.refresh')">
          <el-button
            :icon="Refresh"
            circle
            :loading="loading"
            :aria-label="t('common.refresh')"
            @click="refresh"
          />
        </el-tooltip>
        <el-button
          v-if="canExecute"
          :icon="VideoPlay"
          @click="openRun"
        >
          {{ t('qualityControl.run') }}
        </el-button>
        <el-button
          v-if="canManageRules"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          {{ t('qualityControl.createRule') }}
        </el-button>
      </template>
    </PageHeader>

    <div class="quality-overview">
      <div><span>{{ t('qualityControl.totalRules') }}</span><strong>{{ overview.totalRules }}</strong></div>
      <div><span>{{ t('qualityControl.enabledRules') }}</span><strong>{{ overview.enabledRules }}</strong></div>
      <div><span>{{ t('qualityControl.queued') }}</span><strong>{{ overview.queued }}</strong></div>
      <div><span>{{ t('qualityControl.passed') }}</span><strong>{{ overview.passed }}</strong></div>
      <div><span>{{ t('qualityControl.failed') }}</span><strong>{{ overview.failed }}</strong></div>
      <div><span>{{ t('qualityControl.overridden') }}</span><strong>{{ overview.overridden }}</strong></div>
    </div>

    <StatePanel
      v-if="error"
      state="error"
      :title="t('qualityControl.loadFailed')"
      :description="error"
      @retry="initialize"
    />

    <el-tabs
      v-else
      v-model="activeTab"
      class="quality-tabs"
    >
      <el-tab-pane
        :label="t('qualityControl.rulesTab')"
        name="rules"
      >
        <form
          class="filter-bar"
          @submit.prevent="ruleQuery.page = 1; loadRules()"
        >
          <el-input
            v-model="ruleQuery.keyword"
            clearable
            :prefix-icon="Search"
            :placeholder="t('qualityControl.searchRules')"
          />
          <el-select
            v-model="ruleQuery.projectId"
            clearable
            :placeholder="t('qualityControl.allProjects')"
          >
            <el-option
              v-for="project in projects"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
          <el-select
            v-model="ruleQuery.enabled"
            clearable
            :placeholder="t('qualityControl.allStates')"
          >
            <el-option
              :label="t('qualityControl.enabled')"
              :value="true"
            />
            <el-option
              :label="t('qualityControl.disabled')"
              :value="false"
            />
          </el-select>
          <el-button
            type="primary"
            native-type="submit"
          >
            {{ t('common.search') }}
          </el-button>
        </form>

        <div class="table-shell">
          <el-table
            v-loading="loading"
            :data="rules"
            row-key="id"
          >
            <el-table-column
              :label="t('qualityControl.rule')"
              min-width="230"
            >
              <template #default="{ row }">
                <div class="stacked-cell">
                  <strong>{{ row.name }}</strong>
                  <small>{{ row.algorithmCode }} · {{ row.datasetPattern }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              :label="t('qualityControl.scope')"
              min-width="150"
            >
              <template #default="{ row }">
                {{ row.scope === 'GLOBAL' ? t('qualityControl.global') : row.projectName }}
              </template>
            </el-table-column>
            <el-table-column
              :label="t('qualityControl.assertions')"
              width="110"
            >
              <template #default="{ row }">
                {{ row.assertions.length }}
              </template>
            </el-table-column>
            <el-table-column
              :label="t('qualityControl.priority')"
              width="90"
              prop="priority"
            />
            <el-table-column
              :label="t('common.status')"
              width="100"
            >
              <template #default="{ row }">
                <el-tag
                  :type="row.enabled ? 'success' : 'info'"
                  effect="plain"
                >
                  {{ row.enabled ? t('qualityControl.enabled') : t('qualityControl.disabled') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              :label="t('common.operation')"
              width="100"
            >
              <template #default="{ row }">
                <el-button
                  v-if="row.canEdit"
                  :icon="Edit"
                  circle
                  text
                  :aria-label="t('common.edit')"
                  @click="openEdit(row)"
                />
                <el-button
                  v-if="row.canDelete"
                  :icon="Delete"
                  circle
                  text
                  type="danger"
                  :aria-label="t('common.delete')"
                  @click="removeRule(row)"
                />
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="pagination-row">
          <el-pagination
            v-model:current-page="ruleQuery.page"
            v-model:page-size="ruleQuery.size"
            :total="ruleTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @change="loadRules"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane
        :label="t('qualityControl.logsTab')"
        name="logs"
      >
        <form
          class="filter-bar"
          @submit.prevent="logQuery.page = 1; loadLogs()"
        >
          <el-input
            v-model="logQuery.keyword"
            clearable
            :prefix-icon="Search"
            :placeholder="t('qualityControl.searchLogs')"
          />
          <el-select
            v-model="logQuery.datasetId"
            clearable
            filterable
            :placeholder="t('qualityControl.allDatasets')"
          >
            <el-option
              v-for="dataset in datasets"
              :key="dataset.id"
              :label="`${dataset.name} · ${dataset.projectName || '-'}`"
              :value="dataset.id"
            />
          </el-select>
          <el-select
            v-model="logQuery.status"
            clearable
            :placeholder="t('qualityControl.allStates')"
          >
            <el-option
              v-for="status in statuses"
              :key="status"
              :label="t(`qualityControl.statuses.${status}`)"
              :value="status"
            />
          </el-select>
          <el-button
            type="primary"
            native-type="submit"
          >
            {{ t('common.search') }}
          </el-button>
        </form>

        <div class="table-shell">
          <el-table
            v-loading="loading"
            :data="logs"
            row-key="id"
          >
            <el-table-column
              :label="t('qualityControl.dataset')"
              min-width="210"
            >
              <template #default="{ row }">
                <div class="stacked-cell">
                  <strong>{{ row.datasetName }}</strong>
                  <small>{{ row.projectName || '—' }} · {{ row.dataType }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              prop="ruleName"
              :label="t('qualityControl.rule')"
              min-width="170"
            />
            <el-table-column
              :label="t('common.status')"
              width="130"
            >
              <template #default="{ row }">
                <el-tag
                  :type="statusType(row.status)"
                  effect="plain"
                >
                  {{ t(`qualityControl.statuses.${row.status}`) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              :label="t('qualityControl.effectiveResult')"
              width="130"
            >
              <template #default="{ row }">
                <span v-if="row.effectivePass === null">—</span>
                <el-tag
                  v-else
                  :type="row.effectivePass ? 'success' : 'danger'"
                >
                  {{ row.effectivePass ? t('qualityControl.passed') : t('qualityControl.failed') }}
                </el-tag>
                <small
                  v-if="row.overridePass !== null"
                  class="override-mark"
                >
                  {{ t('qualityControl.overridden') }}
                </small>
              </template>
            </el-table-column>
            <el-table-column
              :label="t('qualityControl.failedAssertions')"
              width="120"
            >
              <template #default="{ row }">
                {{ row.results.filter((result: { passed: boolean }) => !result.passed).length }}
              </template>
            </el-table-column>
            <el-table-column
              :label="t('qualityControl.createdAt')"
              min-width="160"
            >
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column
              v-if="canOverride"
              :label="t('common.operation')"
              width="90"
            >
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'PASSED' || row.status === 'FAILED'"
                  text
                  @click="openOverride(row)"
                >
                  {{ t('qualityControl.override') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="pagination-row">
          <el-pagination
            v-model:current-page="logQuery.page"
            v-model:page-size="logQuery.size"
            :total="logTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @change="loadLogs"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer
      v-model="editorOpen"
      :title="editingId ? t('qualityControl.editRule') : t('qualityControl.createRule')"
      size="680px"
      destroy-on-close
    >
      <el-form
        class="drawer-form"
        label-position="top"
        @submit.prevent="saveRule"
      >
        <div class="form-grid">
          <el-form-item
            class="form-grid__wide"
            :label="t('qualityControl.ruleName')"
            required
          >
            <el-input
              v-model="form.name"
              maxlength="200"
            />
          </el-form-item>
          <el-form-item
            class="form-grid__wide"
            :label="t('qualityControl.ruleDescription')"
          >
            <el-input
              v-model="form.description"
              type="textarea"
              maxlength="500"
              :rows="2"
              show-word-limit
            />
          </el-form-item>
          <el-form-item :label="t('qualityControl.scope')">
            <el-segmented
              v-model="form.scope"
              :options="[
                { label: t('qualityControl.project'), value: 'PROJECT' },
                { label: t('qualityControl.global'), value: 'GLOBAL', disabled: !overview.canManageGlobal },
              ]"
              @change="changeScope"
            />
          </el-form-item>
          <el-form-item
            v-if="form.scope === 'PROJECT'"
            :label="t('qualityControl.project')"
            required
          >
            <el-select
              v-model="form.projectId"
              style="width: 100%"
            >
              <el-option
                v-for="project in projects"
                :key="project.id"
                :label="project.name"
                :value="project.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('qualityControl.datasetPattern')">
            <el-input
              v-model="form.datasetPattern"
              maxlength="200"
            />
          </el-form-item>
          <el-form-item :label="t('qualityControl.priority')">
            <el-input-number
              v-model="form.priority"
              :min="-100000"
              :max="100000"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item>
            <el-switch
              v-model="form.enabled"
              :active-text="t('qualityControl.enabled')"
              :inactive-text="t('qualityControl.disabled')"
            />
          </el-form-item>
        </div>

        <div class="assertion-heading">
          <div>
            <strong>{{ t('qualityControl.assertions') }}</strong>
            <span>{{ t('qualityControl.assertionsHint') }}</span>
          </div>
          <el-button
            :icon="Plus"
            @click="addAssertion"
          >
            {{ t('qualityControl.addAssertion') }}
          </el-button>
        </div>

        <div class="assertion-list">
          <div
            v-for="(assertion, index) in form.assertions"
            :key="index"
            class="assertion-row"
          >
            <el-select
              v-model="assertion.type"
              @change="changeAssertionType(assertion)"
            >
              <el-option
                v-for="type in ['NUMERIC', 'REQUIRED_TOPIC', 'FORBIDDEN_TOPIC']"
                :key="type"
                :label="t(`qualityControl.assertionTypes.${type}`)"
                :value="type"
              />
            </el-select>
            <template v-if="assertion.type === 'NUMERIC'">
              <el-select
                v-model="assertion.metricScope"
                @change="changeMetricScope(assertion)"
              >
                <el-option
                  v-for="scope in ['ALL', 'TOPIC', 'SCHEMA']"
                  :key="scope"
                  :label="t(`qualityControl.metricScopes.${scope}`)"
                  :value="scope"
                />
              </el-select>
              <el-select
                v-model="assertion.metric"
                filterable
              >
                <el-option
                  v-for="metric in metricOptions(assertion)"
                  :key="metric"
                  :label="t(`qualityControl.metrics.${metric}`)"
                  :value="metric"
                />
              </el-select>
              <el-input
                v-if="assertion.metricScope !== 'ALL'"
                v-model="assertion.matchPattern"
                :placeholder="assertion.metricScope === 'TOPIC' ? '/camera/*' : 'sensor_msgs/Image'"
              />
              <el-select v-model="assertion.operator">
                <el-option
                  v-for="operator in ['<=', '>=', '<', '>', '==', '!=']"
                  :key="operator"
                  :label="operator"
                  :value="operator"
                />
              </el-select>
              <el-input-number
                v-model="assertion.threshold"
                controls-position="right"
              />
            </template>
            <el-input
              v-else
              v-model="assertion.matchPattern"
              placeholder="/topic/*"
            />
            <el-select v-model="assertion.severity">
              <el-option
                :label="t('qualityControl.errorSeverity')"
                value="ERROR"
              />
              <el-option
                :label="t('qualityControl.warningSeverity')"
                value="WARNING"
              />
            </el-select>
            <el-button
              :icon="Delete"
              circle
              text
              type="danger"
              :aria-label="t('common.delete')"
              @click="removeAssertion(index)"
            />
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editorOpen = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="saveRule"
        >
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-drawer>

    <el-dialog
      v-model="runOpen"
      :title="t('qualityControl.run')"
      width="500px"
    >
      <el-form label-position="top">
        <el-form-item :label="t('qualityControl.dataset')">
          <el-select
            v-model="runDatasetId"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="dataset in datasets"
              :key="dataset.id"
              :label="`${dataset.name} · ${dataset.projectName || '-'}`"
              :value="dataset.id"
            />
          </el-select>
        </el-form-item>
        <p class="dialog-hint">
          {{ t('qualityControl.runHint') }}
        </p>
      </el-form>
      <template #footer>
        <el-button @click="runOpen = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="running"
          @click="runCheck"
        >
          {{ t('qualityControl.queueRun') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="overrideOpen"
      :title="t('qualityControl.override')"
      width="500px"
    >
      <el-form label-position="top">
        <el-form-item :label="t('qualityControl.effectiveResult')">
          <el-segmented
            v-model="overridePassed"
            :options="[
              { label: t('qualityControl.passed'), value: true },
              { label: t('qualityControl.failed'), value: false },
              { label: t('qualityControl.clearOverride'), value: null },
            ]"
          />
        </el-form-item>
        <el-form-item
          v-if="overridePassed !== null"
          :label="t('qualityControl.overrideReason')"
          required
        >
          <el-input
            v-model="overrideReason"
            type="textarea"
            maxlength="500"
            show-word-limit
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="overrideOpen = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="overriding"
          @click="saveOverride"
        >
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.quality-overview {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  border-bottom: 1px solid var(--color-border);
}

.quality-overview > div {
  display: flex;
  min-height: 76px;
  align-items: baseline;
  justify-content: space-between;
  padding: 20px 16px;
  border-right: 1px solid var(--color-border);
}

.quality-overview > div:last-child {
  border-right: 0;
}

.quality-overview span {
  color: var(--color-text-secondary);
  font-size: 11px;
}

.quality-overview strong {
  color: var(--color-ink);
  font-family: var(--font-mono);
  font-size: 24px;
}

.quality-tabs {
  margin-top: 20px;
}

.stacked-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.stacked-cell strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stacked-cell small,
.override-mark {
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.override-mark {
  display: block;
  margin-top: 4px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.form-grid__wide {
  grid-column: 1 / -1;
}

.assertion-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 0 12px;
  border-top: 1px solid var(--color-border);
}

.assertion-heading > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.assertion-heading strong {
  font-size: 13px;
}

.assertion-heading span,
.dialog-hint {
  color: var(--color-text-secondary);
  font-size: 11px;
  line-height: 1.6;
}

.assertion-list {
  display: grid;
  border-top: 1px solid var(--color-border);
}

.assertion-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);
}

@media (max-width: 900px) {
  .quality-overview {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .assertion-row {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 600px) {
  .quality-overview,
  .form-grid {
    grid-template-columns: 1fr 1fr;
  }

  .assertion-row {
    grid-template-columns: 1fr;
  }
}
</style>
