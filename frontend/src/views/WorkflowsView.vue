<script setup lang="ts">
import {
  Connection,
  Delete,
  Edit,
  Plus,
  Refresh,
  Search,
  VideoPlay,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import {
  deleteWorkflowResource,
  getActionRules,
  getMatchRules,
  getWorkflowDatasets,
  getWorkflowDefinitions,
  getWorkflowMatchingDatasets,
  getWorkflowOverview,
  getWorkflowProjects,
  getWorkflowRuns,
  saveActionRule,
  saveMatchRule,
  saveWorkflowDefinition,
  startWorkflowRun,
  testMatchRule,
} from '@/services/workflows'
import { useAuthStore } from '@/stores/auth'
import type {
  ActionRule,
  ActionRulePayload,
  ActionStep,
  MatchCondition,
  MatchRule,
  MatchRulePayload,
  MatchTestResult,
  WorkflowDatasetOption,
  WorkflowDefinition,
  WorkflowOverview,
  WorkflowPayload,
  WorkflowProjectOption,
  WorkflowRun,
  WorkflowRunStatus,
  WorkflowScope,
} from '@/types/workflow'
import { formatDateTime } from '@/utils/format'

type TabName = 'overview' | 'manage' | 'match-rules' | 'action-rules' | 'runs'
type EditableStep = ActionStep & { paramsText: string }

const tabs: Array<{ name: TabName; label: string }> = [
  { name: 'overview', label: '流程总览' },
  { name: 'manage', label: '工作流管理' },
  { name: 'match-rules', label: '匹配规则' },
  { name: 'action-rules', label: '处理规则' },
  { name: 'runs', label: '运行中心' },
]
const fieldOptions = [
  { value: 'name', label: '数据集名称' },
  { value: 'extension_name', label: '文件扩展名' },
  { value: 'robot_type', label: '机器人类型' },
  { value: 'sizemb', label: '大小（MB）' },
  { value: 'source_url', label: '来源地址' },
  { value: 'remote_url', label: '远程地址' },
  { value: 'local_url', label: '本地地址' },
]
const operatorOptions = [
  { value: 'eq', label: '等于' },
  { value: 'contains', label: '包含' },
  { value: 'regex', label: '正则匹配' },
  { value: 'gt', label: '大于' },
  { value: 'lt', label: '小于' },
  { value: 'gte', label: '大于等于' },
  { value: 'lte', label: '小于等于' },
  { value: 'is_null', label: '为空' },
  { value: 'is_not_null', label: '不为空' },
]
const actionOptions = [
  { value: 'autoRename', label: '自动重命名' },
  { value: 'autoImportProject', label: '自动导入项目' },
  { value: 'io_hdf5agilex2mcap', label: 'Agilex HDF5 → MCAP' },
  { value: 'io_hdf5realman2mcap', label: 'Realman HDF5 → MCAP' },
  { value: 'io_hdf5dobot2mcap', label: 'Dobot HDF5 → MCAP' },
  { value: 'io_hdf5limx2mcap', label: 'Limx HDF5 → MCAP' },
  { value: 'io_hdf5unix2mcap', label: 'Unix HDF5 → MCAP' },
  { value: 'io_lerobot2mcap', label: 'LeRobot → MCAP' },
  { value: 'io_agibot2mcap', label: 'Agibot → MCAP' },
  { value: 'io_meituan2mcap', label: '美团 → MCAP' },
  { value: 'io_lumos2mcap', label: 'Lumos FastUMI → MCAP' },
  { value: 'io_zc0touch2mcap', label: 'zc0touch → MCAP' },
  { value: 'io_sensexperience2mcap', label: 'SenseXperience → MCAP' },
  { value: 'io_bvh2mcap', label: 'BVH → MCAP' },
  { value: 'io_generateTransforms', label: '生成三维动画' },
  { value: 'io_galbotproto2mcap', label: '银河通用 Proto → ROS2' },
  { value: 'runAlgorithm', label: '运行自定义算法' },
  { value: 'qualityCheck', label: '执行质量评估' },
  { value: 'autoAnnotate', label: '自动标注' },
  { value: 'exportDataset', label: '导出数据' },
]
const statusOptions: Array<{ value: WorkflowRunStatus; label: string }> = [
  { value: 'QUEUED', label: '等待中' },
  { value: 'RUNNING', label: '运行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'FAILED', label: '失败' },
  { value: 'CANCELLED', label: '已取消' },
]

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const overview = ref<WorkflowOverview | null>(null)
const workflows = ref<WorkflowDefinition[]>([])
const matchRules = ref<MatchRule[]>([])
const actionRules = ref<ActionRule[]>([])
const runs = ref<WorkflowRun[]>([])
const projects = ref<WorkflowProjectOption[]>([])
const datasets = ref<WorkflowDatasetOption[]>([])
const query = reactive({
  projectId: undefined as number | undefined,
  enabled: undefined as boolean | undefined,
  keyword: '',
  status: undefined as WorkflowRunStatus | undefined,
})

const activeTab = computed<TabName>(() => {
  const segment = String(route.params.tab || 'overview')
  return tabs.some((tab) => tab.name === segment) ? segment as TabName : 'overview'
})
const canManage = computed(() => auth.hasPermission('basic:workflow:manage'))
const canTest = computed(() => auth.hasPermission('basic:workflow:test'))
const canGlobal = computed(() => auth.hasPermission('basic:workflow:global'))
const manageableProjects = computed(() => projects.value.filter((project) => project.manageable))

const workflowOpen = ref(false)
const workflowSaving = ref(false)
const workflowId = ref<number | null>(null)
const workflowForm = reactive<WorkflowPayload>({
  name: '',
  description: '',
  scope: 'PROJECT',
  projectId: null,
  priority: 100,
  enabled: true,
  matchRuleId: 0,
  actionRuleId: 0,
})

const matchOpen = ref(false)
const matchSaving = ref(false)
const matchId = ref<number | null>(null)
const matchForm = reactive<MatchRulePayload>({
  name: '',
  description: '',
  scope: 'PROJECT',
  projectId: null,
  priority: 100,
  enabled: true,
  logicOperator: 'AND',
  conditions: [],
})

const actionOpen = ref(false)
const actionSaving = ref(false)
const actionId = ref<number | null>(null)
const actionForm = reactive<Omit<ActionRulePayload, 'steps'> & { steps: EditableStep[] }>({
  name: '',
  description: '',
  scope: 'PROJECT',
  projectId: null,
  enabled: true,
  steps: [],
})

const testOpen = ref(false)
const testRule = ref<MatchRule | null>(null)
const testProjectId = ref<number | null>(null)
const testDatasetIds = ref<number[]>([])
const testing = ref(false)
const testResult = ref<MatchTestResult | null>(null)

const runOpen = ref(false)
const runWorkflowId = ref<number | null>(null)
const runDatasetId = ref<number | null>(null)
const runSaving = ref(false)

function actionLabel(action: string) {
  return actionOptions.find((option) => option.value === action)?.label ?? action
}

function statusLabel(status: WorkflowRunStatus) {
  return statusOptions.find((option) => option.value === status)?.label ?? status
}

function statusType(status: WorkflowRunStatus) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'primary'
  if (status === 'CANCELLED') return 'info'
  return 'warning'
}

function scopeLabel(scope: WorkflowScope, projectName: string | null) {
  return scope === 'GLOBAL' ? '全局' : (projectName ?? '项目')
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [summary, workflowRows, matchRows, actionRows, runRows, projectRows, datasetRows] =
      await Promise.all([
        getWorkflowOverview(),
        getWorkflowDefinitions(filterParams()),
        getMatchRules(filterParams()),
        getActionRules(filterParams()),
        getWorkflowRuns({ projectId: query.projectId, status: query.status }),
        getWorkflowProjects(),
        getWorkflowDatasets(query.projectId),
      ])
    overview.value = summary
    workflows.value = workflowRows
    matchRules.value = matchRows
    actionRules.value = actionRows
    runs.value = runRows
    projects.value = projectRows
    datasets.value = datasetRows
  } catch (reason) {
    error.value = getErrorMessage(reason, '流程管理加载失败')
  } finally {
    loading.value = false
  }
}

function filterParams() {
  return {
    projectId: query.projectId,
    enabled: query.enabled,
    keyword: query.keyword.trim() || undefined,
  }
}

async function navigateTab(name: TabName) {
  await router.push(`/workflows/${name}`)
}

function resetFilters() {
  Object.assign(query, {
    projectId: undefined,
    enabled: undefined,
    keyword: '',
    status: undefined,
  })
  void loadAll()
}

function defaultScope() {
  return canGlobal.value ? 'GLOBAL' as const : 'PROJECT' as const
}

function defaultProjectId() {
  return manageableProjects.value[0]?.id ?? null
}

function changeScope(form: { scope: WorkflowScope; projectId: number | null }) {
  form.projectId = form.scope === 'GLOBAL' ? null : defaultProjectId()
}

function openWorkflow(item?: WorkflowDefinition) {
  workflowId.value = item?.id ?? null
  Object.assign(workflowForm, item
    ? {
        name: item.name,
        description: item.description ?? '',
        scope: item.scope,
        projectId: item.projectId,
        priority: item.priority,
        enabled: item.enabled,
        matchRuleId: item.matchRuleId,
        actionRuleId: item.actionRuleId,
      }
    : {
        name: '',
        description: '',
        scope: defaultScope(),
        projectId: defaultScope() === 'GLOBAL' ? null : defaultProjectId(),
        priority: 100,
        enabled: true,
        matchRuleId: matchRules.value[0]?.id ?? 0,
        actionRuleId: actionRules.value[0]?.id ?? 0,
      })
  workflowOpen.value = true
}

function openMatch(item?: MatchRule) {
  matchId.value = item?.id ?? null
  Object.assign(matchForm, item
    ? {
        name: item.name,
        description: item.description ?? '',
        scope: item.scope,
        projectId: item.projectId,
        priority: item.priority,
        enabled: item.enabled,
        logicOperator: item.logicOperator,
        conditions: item.conditions.map((condition) => ({ ...condition })),
      }
    : {
        name: '',
        description: '',
        scope: defaultScope(),
        projectId: defaultScope() === 'GLOBAL' ? null : defaultProjectId(),
        priority: 100,
        enabled: true,
        logicOperator: 'AND',
        conditions: [emptyCondition()],
      })
  matchOpen.value = true
}

function emptyCondition(): MatchCondition {
  return { field: 'extension_name', operator: 'eq', value: 'mcap', flags: '' }
}

function addCondition() {
  matchForm.conditions.push(emptyCondition())
}

function removeCondition(index: number) {
  if (matchForm.conditions.length > 1) matchForm.conditions.splice(index, 1)
}

function openAction(item?: ActionRule) {
  actionId.value = item?.id ?? null
  Object.assign(actionForm, item
    ? {
        name: item.name,
        description: item.description ?? '',
        scope: item.scope,
        projectId: item.projectId,
        enabled: item.enabled,
        steps: item.steps.map((step) => ({
          ...step,
          paramsText: JSON.stringify(step.params, null, 2),
        })),
      }
    : {
        name: '',
        description: '',
        scope: defaultScope(),
        projectId: defaultScope() === 'GLOBAL' ? null : defaultProjectId(),
        enabled: true,
        steps: [emptyStep()],
      })
  actionOpen.value = true
}

function emptyStep(): EditableStep {
  return { action: 'autoRename', params: {}, paramsText: '{}' }
}

function addStep() {
  actionForm.steps.push(emptyStep())
}

function removeStep(index: number) {
  if (actionForm.steps.length > 1) actionForm.steps.splice(index, 1)
}

function validateBase(form: { name: string; scope: WorkflowScope; projectId: number | null }) {
  if (!form.name.trim()) {
    ElMessage.warning('请输入名称')
    return false
  }
  if (form.scope === 'PROJECT' && !form.projectId) {
    ElMessage.warning('请选择项目')
    return false
  }
  return true
}

async function submitWorkflow() {
  if (!validateBase(workflowForm) || !workflowForm.matchRuleId || !workflowForm.actionRuleId) {
    ElMessage.warning('请选择匹配规则和处理规则')
    return
  }
  workflowSaving.value = true
  try {
    await saveWorkflowDefinition(workflowId.value, {
      ...workflowForm,
      name: workflowForm.name.trim(),
      description: workflowForm.description.trim(),
    })
    ElMessage.success(workflowId.value ? '工作流已更新' : '工作流已创建')
    workflowOpen.value = false
    await loadAll()
  } catch (reason) {
    notifyError(reason, '工作流保存失败')
  } finally {
    workflowSaving.value = false
  }
}

async function submitMatch() {
  if (!validateBase(matchForm)) return
  matchSaving.value = true
  try {
    await saveMatchRule(matchId.value, {
      ...matchForm,
      name: matchForm.name.trim(),
      description: matchForm.description.trim(),
    })
    ElMessage.success(matchId.value ? '匹配规则已更新' : '匹配规则已创建')
    matchOpen.value = false
    await loadAll()
  } catch (reason) {
    notifyError(reason, '匹配规则保存失败')
  } finally {
    matchSaving.value = false
  }
}

async function submitAction() {
  if (!validateBase(actionForm)) return
  let steps: ActionStep[]
  try {
    steps = actionForm.steps.map((step) => ({
      action: step.action,
      params: JSON.parse(step.paramsText || '{}') as Record<string, unknown>,
    }))
  } catch {
    ElMessage.warning('步骤参数必须是合法 JSON')
    return
  }
  actionSaving.value = true
  try {
    await saveActionRule(actionId.value, {
      name: actionForm.name.trim(),
      description: actionForm.description.trim(),
      scope: actionForm.scope,
      projectId: actionForm.projectId,
      enabled: actionForm.enabled,
      steps,
    })
    ElMessage.success(actionId.value ? '处理规则已更新' : '处理规则已创建')
    actionOpen.value = false
    await loadAll()
  } catch (reason) {
    notifyError(reason, '处理规则保存失败')
  } finally {
    actionSaving.value = false
  }
}

async function removeResource(
  resource: 'definitions' | 'match-rules' | 'action-rules',
  id: number,
  name: string,
) {
  const confirmed = await confirmAction(
    `删除“${name}”后将不再参与流程匹配，历史运行记录会保留。`,
    '确认删除',
    '删除',
  )
  if (!confirmed) return
  try {
    await deleteWorkflowResource(resource, id)
    ElMessage.success('已删除')
    await loadAll()
  } catch (reason) {
    notifyError(reason, '删除失败')
  }
}

async function openTest(rule: MatchRule) {
  testRule.value = rule
  testProjectId.value = rule.projectId ?? query.projectId ?? projects.value[0]?.id ?? null
  testDatasetIds.value = []
  testResult.value = null
  testOpen.value = true
  datasets.value = await getWorkflowDatasets(testProjectId.value ?? undefined)
}

async function changeTestProject() {
  testDatasetIds.value = []
  datasets.value = await getWorkflowDatasets(testProjectId.value ?? undefined)
}

async function executeTest() {
  if (!testRule.value) return
  testing.value = true
  try {
    testResult.value = await testMatchRule(
      testRule.value.id,
      testProjectId.value,
      testDatasetIds.value,
    )
  } catch (reason) {
    notifyError(reason, '匹配测试失败')
  } finally {
    testing.value = false
  }
}

async function openRun(workflow?: WorkflowDefinition) {
  runWorkflowId.value = workflow?.id ?? workflows.value[0]?.id ?? null
  runDatasetId.value = null
  runOpen.value = true
  await changeRunWorkflow()
}

async function changeRunWorkflow() {
  runDatasetId.value = null
  datasets.value = runWorkflowId.value
    ? await getWorkflowMatchingDatasets(runWorkflowId.value)
    : []
  runDatasetId.value = datasets.value[0]?.id ?? null
}

async function executeRun() {
  if (!runWorkflowId.value || !runDatasetId.value) {
    ElMessage.warning('请选择工作流和数据集')
    return
  }
  runSaving.value = true
  try {
    await startWorkflowRun(runWorkflowId.value, runDatasetId.value)
    ElMessage.success('试运行已加入队列')
    runOpen.value = false
    await loadAll()
    await navigateTab('runs')
  } catch (reason) {
    const message = getErrorMessage(reason, '试运行创建失败')
    if (message === 'Dataset does not match this workflow') {
      ElMessage.error('所选数据集不符合该工作流的匹配条件')
    } else {
      notifyError(reason, '试运行创建失败')
    }
  } finally {
    runSaving.value = false
  }
}

watch(() => query.projectId, async (projectId) => {
  datasets.value = await getWorkflowDatasets(projectId)
})

onMounted(loadAll)
</script>

<template>
  <section class="admin-page workflow-page">
    <PageHeader
      title="流程管理"
      eyebrow="Workflow orchestration"
      description="组合匹配条件与处理步骤，让数据在入库后按规则进入规范化、质检、标注与交付环节。"
    >
      <template #actions>
        <el-button
          :icon="Refresh"
          :loading="loading"
          @click="loadAll"
        >
          刷新
        </el-button>
        <el-button
          v-if="canManage"
          type="primary"
          :icon="Plus"
          @click="openWorkflow()"
        >
          新建工作流
        </el-button>
      </template>
    </PageHeader>

    <nav
      class="workflow-tabs"
      aria-label="流程管理页面"
    >
      <button
        v-for="tab in tabs"
        :key="tab.name"
        type="button"
        :class="{ active: activeTab === tab.name }"
        @click="navigateTab(tab.name)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <StatePanel
      v-if="loading && !overview"
      state="loading"
    />
    <StatePanel
      v-else-if="error"
      state="error"
      :description="error"
      @retry="loadAll"
    />

    <template v-else>
      <div
        v-if="activeTab !== 'overview'"
        class="filter-bar workflow-filters"
      >
        <el-input
          v-if="activeTab !== 'runs'"
          v-model="query.keyword"
          clearable
          :prefix-icon="Search"
          placeholder="搜索名称或描述"
          @keyup.enter="loadAll"
        />
        <el-select
          v-model="query.projectId"
          clearable
          placeholder="全部项目"
        >
          <el-option
            v-for="project in projects"
            :key="project.id"
            :label="project.name"
            :value="project.id"
          />
        </el-select>
        <el-select
          v-if="activeTab !== 'runs'"
          v-model="query.enabled"
          clearable
          placeholder="全部状态"
        >
          <el-option label="已启用" :value="true" />
          <el-option label="已停用" :value="false" />
        </el-select>
        <el-select
          v-else
          v-model="query.status"
          clearable
          placeholder="全部状态"
        >
          <el-option
            v-for="status in statusOptions"
            :key="status.value"
            :label="status.label"
            :value="status.value"
          />
        </el-select>
        <el-button type="primary" @click="loadAll">
          查询
        </el-button>
        <el-button @click="resetFilters">
          重置
        </el-button>
      </div>

      <section
        v-if="activeTab === 'overview'"
        class="overview-view"
      >
        <div class="metric-strip">
          <div>
            <span>工作流</span>
            <strong>{{ overview?.workflowCount ?? 0 }}</strong>
            <small>{{ overview?.enabledWorkflowCount ?? 0 }} 条启用</small>
          </div>
          <div>
            <span>匹配规则</span>
            <strong>{{ overview?.matchRuleCount ?? 0 }}</strong>
            <small>定义数据入口</small>
          </div>
          <div>
            <span>处理规则</span>
            <strong>{{ overview?.actionRuleCount ?? 0 }}</strong>
            <small>编排处理步骤</small>
          </div>
          <div>
            <span>执行队列</span>
            <strong>{{ overview?.queuedRunCount ?? 0 }}</strong>
            <small>{{ overview?.failedRunCount ?? 0 }} 条失败</small>
          </div>
        </div>

        <div class="pipeline-heading">
          <div>
            <h2>流程蓝图</h2>
            <p>匹配节点接收数据，处理节点按顺序执行动作。</p>
          </div>
          <el-button
            v-if="canTest && workflows.length"
            :icon="VideoPlay"
            @click="openRun()"
          >
            试运行
          </el-button>
        </div>

        <div
          v-if="workflows.length"
          class="pipeline-canvas"
        >
          <div
            v-for="workflow in workflows"
            :key="workflow.id"
            class="pipeline-lane"
          >
            <button
              class="pipeline-node pipeline-node--match"
              type="button"
              @click="navigateTab('match-rules')"
            >
              <span>匹配</span>
              <strong>{{ workflow.matchRuleName }}</strong>
              <small>{{ scopeLabel(workflow.scope, workflow.projectName) }}</small>
            </button>
            <span class="pipeline-link" aria-hidden="true">
              <i />
            </span>
            <button
              class="pipeline-node pipeline-node--action"
              type="button"
              @click="navigateTab('action-rules')"
            >
              <span>处理</span>
              <strong>{{ workflow.actionRuleName }}</strong>
              <small>{{ workflow.stepCount }} 个步骤</small>
            </button>
            <span class="pipeline-link pipeline-link--short" aria-hidden="true">
              <i />
            </span>
            <button
              class="pipeline-terminal"
              type="button"
              @click="openRun(workflow)"
            >
              <Connection />
              <span>{{ workflow.name }}</span>
              <small>优先级 {{ workflow.priority }}</small>
            </button>
          </div>
        </div>
        <StatePanel
          v-else
          state="empty"
          title="还没有工作流"
          description="先创建匹配规则和处理规则，再把它们组合成工作流。"
        />
      </section>

      <section v-else-if="activeTab === 'manage'">
        <div class="section-toolbar">
          <div>
            <h2>工作流管理</h2>
            <p>按优先级依次评估启用的工作流。</p>
          </div>
          <el-button
            v-if="canManage"
            type="primary"
            :icon="Plus"
            @click="openWorkflow()"
          >
            新建工作流
          </el-button>
        </div>
        <div class="table-shell">
          <el-table :data="workflows">
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column label="名称" min-width="210">
              <template #default="{ row }">
                <strong>{{ row.name }}</strong>
                <p class="table-subtitle">{{ row.description || '—' }}</p>
              </template>
            </el-table-column>
            <el-table-column label="范围" width="130">
              <template #default="{ row }">
                {{ scopeLabel(row.scope, row.projectName) }}
              </template>
            </el-table-column>
            <el-table-column prop="matchRuleName" label="匹配规则" min-width="160" />
            <el-table-column prop="actionRuleName" label="处理规则" min-width="180" />
            <el-table-column prop="priority" label="优先级" width="90" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">
                  {{ row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="138" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button
                    v-if="canTest"
                    circle
                    text
                    :icon="VideoPlay"
                    title="试运行"
                    @click="openRun(row)"
                  />
                  <el-button
                    v-if="row.canEdit"
                    circle
                    text
                    :icon="Edit"
                    title="编辑"
                    @click="openWorkflow(row)"
                  />
                  <el-button
                    v-if="row.canEdit"
                    circle
                    text
                    type="danger"
                    :icon="Delete"
                    title="删除"
                    @click="removeResource('definitions', row.id, row.name)"
                  />
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section v-else-if="activeTab === 'match-rules'">
        <div class="section-toolbar">
          <div>
            <h2>匹配规则</h2>
            <p>使用数据集属性决定哪条流程接收数据。</p>
          </div>
          <el-button
            v-if="canManage"
            type="primary"
            :icon="Plus"
            @click="openMatch()"
          >
            新建匹配规则
          </el-button>
        </div>
        <div class="table-shell">
          <el-table :data="matchRules">
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column label="名称" min-width="220">
              <template #default="{ row }">
                <strong>{{ row.name }}</strong>
                <p class="table-subtitle">{{ row.description || '—' }}</p>
              </template>
            </el-table-column>
            <el-table-column label="范围" width="130">
              <template #default="{ row }">
                {{ scopeLabel(row.scope, row.projectName) }}
              </template>
            </el-table-column>
            <el-table-column label="条件" min-width="260">
              <template #default="{ row }">
                <span class="rule-summary">
                  {{ row.conditions.length }} 条，{{ row.logicOperator === 'AND' ? '全部满足' : '任一满足' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="90" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">
                  {{ row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="138" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button
                    v-if="canTest"
                    circle
                    text
                    :icon="VideoPlay"
                    title="测试"
                    @click="openTest(row)"
                  />
                  <el-button
                    v-if="row.canEdit"
                    circle
                    text
                    :icon="Edit"
                    title="编辑"
                    @click="openMatch(row)"
                  />
                  <el-button
                    v-if="row.canEdit"
                    circle
                    text
                    type="danger"
                    :icon="Delete"
                    title="删除"
                    @click="removeResource('match-rules', row.id, row.name)"
                  />
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section v-else-if="activeTab === 'action-rules'">
        <div class="section-toolbar">
          <div>
            <h2>处理规则</h2>
            <p>将格式转换、质检、标注与导出动作编排为有序步骤。</p>
          </div>
          <el-button
            v-if="canManage"
            type="primary"
            :icon="Plus"
            @click="openAction()"
          >
            新建处理规则
          </el-button>
        </div>
        <div class="table-shell">
          <el-table :data="actionRules">
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column label="名称" min-width="220">
              <template #default="{ row }">
                <strong>{{ row.name }}</strong>
                <p class="table-subtitle">{{ row.description || '—' }}</p>
              </template>
            </el-table-column>
            <el-table-column label="范围" width="130">
              <template #default="{ row }">
                {{ scopeLabel(row.scope, row.projectName) }}
              </template>
            </el-table-column>
            <el-table-column label="步骤" min-width="320">
              <template #default="{ row }">
                <div class="step-chain">
                  <template v-for="(step, index) in row.steps" :key="`${row.id}-${index}`">
                    <span>{{ index + 1 }}. {{ actionLabel(step.action) }}</span>
                  </template>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">
                  {{ row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="104" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button
                    v-if="row.canEdit"
                    circle
                    text
                    :icon="Edit"
                    title="编辑"
                    @click="openAction(row)"
                  />
                  <el-button
                    v-if="row.canEdit"
                    circle
                    text
                    type="danger"
                    :icon="Delete"
                    title="删除"
                    @click="removeResource('action-rules', row.id, row.name)"
                  />
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section v-else>
        <div class="section-toolbar">
          <div>
            <h2>运行中心</h2>
            <p>查看流程排队、执行进度与失败信息。</p>
          </div>
          <el-button
            v-if="canTest && workflows.length"
            type="primary"
            :icon="VideoPlay"
            @click="openRun()"
          >
            发起试运行
          </el-button>
        </div>
        <div class="table-shell">
          <el-table :data="runs">
            <el-table-column prop="id" label="运行 ID" width="96" />
            <el-table-column prop="workflowName" label="工作流" min-width="170" />
            <el-table-column label="数据集" min-width="200">
              <template #default="{ row }">
                <strong>{{ row.datasetName }}</strong>
                <p class="table-subtitle">{{ row.projectName || '未分配项目' }}</p>
              </template>
            </el-table-column>
            <el-table-column prop="stage" label="阶段" width="120" />
            <el-table-column label="状态" width="160">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">
                  {{ statusLabel(row.status) }}
                </el-tag>
                <el-progress
                  v-if="row.status === 'RUNNING'"
                  :percentage="row.progress"
                  :stroke-width="4"
                />
              </template>
            </el-table-column>
            <el-table-column label="步骤" min-width="220">
              <template #default="{ row }">
                {{ row.steps.map((step: ActionStep) => actionLabel(step.action)).join(' → ') }}
              </template>
            </el-table-column>
            <el-table-column label="启动时间" width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.startedAt || row.createdAt) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </template>

    <el-drawer
      v-model="workflowOpen"
      :title="workflowId ? '编辑工作流' : '新建工作流'"
      size="480px"
    >
      <el-form label-position="top" class="drawer-form">
        <el-form-item label="名称" required>
          <el-input v-model="workflowForm.name" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="workflowForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="范围" required>
            <el-segmented
              v-model="workflowForm.scope"
              :options="canGlobal
                ? [{ label: '全局', value: 'GLOBAL' }, { label: '项目', value: 'PROJECT' }]
                : [{ label: '项目', value: 'PROJECT' }]"
              @change="changeScope(workflowForm)"
            />
          </el-form-item>
          <el-form-item label="优先级" required>
            <el-input-number v-model="workflowForm.priority" :min="0" :max="9999" />
          </el-form-item>
        </div>
        <el-form-item v-if="workflowForm.scope === 'PROJECT'" label="项目" required>
          <el-select v-model="workflowForm.projectId" filterable>
            <el-option
              v-for="project in manageableProjects"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配规则" required>
          <el-select v-model="workflowForm.matchRuleId" filterable>
            <el-option
              v-for="rule in matchRules"
              :key="rule.id"
              :label="rule.name"
              :value="rule.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="处理规则" required>
          <el-select v-model="workflowForm.actionRuleId" filterable>
            <el-option
              v-for="rule in actionRules"
              :key="rule.id"
              :label="rule.name"
              :value="rule.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="workflowForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="workflowOpen = false">取消</el-button>
          <el-button type="primary" :loading="workflowSaving" @click="submitWorkflow">
            保存
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer
      v-model="matchOpen"
      :title="matchId ? '编辑匹配规则' : '新建匹配规则'"
      size="620px"
    >
      <el-form label-position="top" class="drawer-form">
        <el-form-item label="名称" required>
          <el-input v-model="matchForm.name" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="matchForm.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <div class="form-grid form-grid--three">
          <el-form-item label="范围" required>
            <el-select v-model="matchForm.scope" @change="changeScope(matchForm)">
              <el-option v-if="canGlobal" label="全局" value="GLOBAL" />
              <el-option label="项目" value="PROJECT" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级" required>
            <el-input-number v-model="matchForm.priority" :min="0" :max="9999" />
          </el-form-item>
          <el-form-item label="组合逻辑" required>
            <el-segmented
              v-model="matchForm.logicOperator"
              :options="[{ label: '且', value: 'AND' }, { label: '或', value: 'OR' }]"
            />
          </el-form-item>
        </div>
        <el-form-item v-if="matchForm.scope === 'PROJECT'" label="项目" required>
          <el-select v-model="matchForm.projectId">
            <el-option
              v-for="project in manageableProjects"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <div class="editor-heading">
          <strong>匹配条件</strong>
          <el-button text type="primary" :icon="Plus" @click="addCondition">
            添加条件
          </el-button>
        </div>
        <div class="condition-list">
          <div
            v-for="(condition, index) in matchForm.conditions"
            :key="index"
            class="condition-row"
          >
            <span class="sequence">{{ index + 1 }}</span>
            <el-select v-model="condition.field" aria-label="字段">
              <el-option
                v-for="field in fieldOptions"
                :key="field.value"
                :label="field.label"
                :value="field.value"
              />
            </el-select>
            <el-select v-model="condition.operator" aria-label="运算符">
              <el-option
                v-for="operator in operatorOptions"
                :key="operator.value"
                :label="operator.label"
                :value="operator.value"
              />
            </el-select>
            <el-input
              v-if="!condition.operator.startsWith('is_')"
              v-model="condition.value"
              placeholder="值"
            />
            <span v-else class="empty-value">无需值</span>
            <el-button
              circle
              text
              type="danger"
              :icon="Delete"
              title="删除条件"
              @click="removeCondition(index)"
            />
          </div>
        </div>
        <el-form-item label="启用">
          <el-switch v-model="matchForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="matchOpen = false">取消</el-button>
          <el-button type="primary" :loading="matchSaving" @click="submitMatch">保存</el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer
      v-model="actionOpen"
      :title="actionId ? '编辑处理规则' : '新建处理规则'"
      size="620px"
    >
      <el-form label-position="top" class="drawer-form">
        <el-form-item label="名称" required>
          <el-input v-model="actionForm.name" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="actionForm.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="范围" required>
            <el-select v-model="actionForm.scope" @change="changeScope(actionForm)">
              <el-option v-if="canGlobal" label="全局" value="GLOBAL" />
              <el-option label="项目" value="PROJECT" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="actionForm.scope === 'PROJECT'" label="项目" required>
            <el-select v-model="actionForm.projectId">
              <el-option
                v-for="project in manageableProjects"
                :key="project.id"
                :label="project.name"
                :value="project.id"
              />
            </el-select>
          </el-form-item>
        </div>
        <div class="editor-heading">
          <strong>处理步骤</strong>
          <el-button text type="primary" :icon="Plus" @click="addStep">
            添加步骤
          </el-button>
        </div>
        <div class="step-editor">
          <div
            v-for="(step, index) in actionForm.steps"
            :key="index"
            class="step-editor__item"
          >
            <div class="step-editor__rail">
              <span>{{ index + 1 }}</span>
              <i v-if="index < actionForm.steps.length - 1" />
            </div>
            <div class="step-editor__body">
              <div class="step-editor__title">
                <el-select v-model="step.action" filterable>
                  <el-option
                    v-for="action in actionOptions"
                    :key="action.value"
                    :label="action.label"
                    :value="action.value"
                  />
                </el-select>
                <el-button
                  circle
                  text
                  type="danger"
                  :icon="Delete"
                  title="删除步骤"
                  @click="removeStep(index)"
                />
              </div>
              <el-input
                v-model="step.paramsText"
                type="textarea"
                :rows="3"
                placeholder='参数 JSON，例如 {"pattern":"^raw_","replacement":"clean_"}'
              />
            </div>
          </div>
        </div>
        <el-form-item label="启用">
          <el-switch v-model="actionForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="actionOpen = false">取消</el-button>
          <el-button type="primary" :loading="actionSaving" @click="submitAction">保存</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="testOpen" title="测试匹配规则" width="680px">
      <div class="test-controls">
        <el-select
          v-model="testProjectId"
          placeholder="选择项目"
          @change="changeTestProject"
        >
          <el-option
            v-for="project in projects"
            :key="project.id"
            :label="project.name"
            :value="project.id"
          />
        </el-select>
        <el-select
          v-model="testDatasetIds"
          multiple
          collapse-tags
          clearable
          placeholder="全部数据集"
        >
          <el-option
            v-for="dataset in datasets"
            :key="dataset.id"
            :label="dataset.name"
            :value="dataset.id"
          />
        </el-select>
        <el-button type="primary" :loading="testing" @click="executeTest">
          执行测试
        </el-button>
      </div>
      <div v-if="testResult" class="test-result">
        <div>
          <strong>{{ testResult.matched }}</strong>
          <span>命中</span>
        </div>
        <div>
          <strong>{{ testResult.scanned }}</strong>
          <span>已扫描</span>
        </div>
      </div>
      <el-table v-if="testResult" :data="testResult.samples" max-height="280">
        <el-table-column prop="name" label="命中样例" min-width="220" />
        <el-table-column prop="dataType" label="类型" width="100" />
        <el-table-column prop="projectName" label="项目" min-width="150" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="runOpen" title="发起试运行" width="520px">
      <el-form label-position="top" class="dialog-form">
        <el-form-item label="工作流" required>
          <el-select
            v-model="runWorkflowId"
            filterable
            @change="changeRunWorkflow"
          >
            <el-option
              v-for="workflow in workflows.filter((item) => item.enabled)"
              :key="workflow.id"
              :label="workflow.name"
              :value="workflow.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数据集" required>
          <el-select
            v-model="runDatasetId"
            filterable
            :placeholder="datasets.length ? '请选择匹配的数据集' : '暂无匹配数据集'"
            :disabled="!datasets.length"
          >
            <el-option
              v-for="dataset in datasets"
              :key="dataset.id"
              :label="`${dataset.name} · ${dataset.projectName || '未分配项目'}`"
              :value="dataset.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="runOpen = false">取消</el-button>
          <el-button type="primary" :loading="runSaving" @click="executeRun">
            加入队列
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.workflow-page {
  --workflow-blue: #276fd6;
  --workflow-purple: #7d4fba;
  --workflow-green: #2f8759;
}

.workflow-tabs {
  display: flex;
  min-height: 54px;
  align-items: flex-end;
  gap: 28px;
  overflow-x: auto;
  border-bottom: 1px solid var(--color-border);
}

.workflow-tabs button {
  position: relative;
  min-width: max-content;
  padding: 17px 2px 14px;
  cursor: pointer;
  border: 0;
  color: var(--color-text-secondary);
  background: none;
  font-size: 13px;
}

.workflow-tabs button::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: var(--color-accent);
  content: '';
  transform: scaleX(0);
  transition: transform 160ms ease;
}

.workflow-tabs button.active {
  color: var(--color-accent);
  font-weight: 650;
}

.workflow-tabs button.active::after {
  transform: scaleX(1);
}

.overview-view {
  padding-top: 22px;
}

.metric-strip {
  display: grid;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
  grid-template-columns: repeat(4, 1fr);
}

.metric-strip > div {
  display: grid;
  padding: 17px 22px;
  border-right: 1px solid var(--color-border);
  grid-template-columns: 1fr auto;
  row-gap: 2px;
}

.metric-strip > div:last-child {
  border-right: 0;
}

.metric-strip span {
  align-self: center;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.metric-strip strong {
  grid-row: span 2;
  font-family: var(--font-mono);
  font-size: 27px;
  font-weight: 550;
}

.metric-strip small {
  color: var(--color-text-muted);
  font-size: 11px;
}

.pipeline-heading,
.section-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 0 15px;
}

.pipeline-heading h2,
.section-toolbar h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 650;
}

.pipeline-heading p,
.section-toolbar p {
  margin: 5px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.pipeline-canvas {
  min-height: 430px;
  padding: 34px;
  overflow: auto;
  border: 1px solid var(--color-border-strong);
  border-radius: 6px;
  background-color: #fbfcfd;
  background-image: radial-gradient(#cbd5db 0.8px, transparent 0.8px);
  background-size: 16px 16px;
}

.pipeline-lane {
  display: grid;
  min-width: 760px;
  align-items: center;
  margin-bottom: 38px;
  grid-template-columns: minmax(190px, 1fr) 76px minmax(210px, 1.15fr) 76px minmax(180px, 0.9fr);
}

.pipeline-node,
.pipeline-terminal {
  display: grid;
  min-height: 82px;
  place-content: center;
  padding: 12px 18px;
  cursor: pointer;
  border-radius: 6px;
  background: #fff;
  text-align: center;
  transition: box-shadow 150ms ease, transform 150ms ease;
}

.pipeline-node:hover,
.pipeline-terminal:hover {
  box-shadow: 0 7px 20px rgb(31 53 67 / 12%);
  transform: translateY(-2px);
}

.pipeline-node--match {
  border: 1px solid #70a5ec;
  color: #205cae;
  background: #edf5ff;
}

.pipeline-node--action {
  border: 1px solid #b690df;
  color: #694098;
  background: #f7f0ff;
}

.pipeline-node span {
  margin-bottom: 4px;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
}

.pipeline-node strong,
.pipeline-terminal span {
  overflow: hidden;
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pipeline-node small,
.pipeline-terminal small {
  margin-top: 5px;
  color: currentColor;
  font-size: 10px;
  opacity: 0.72;
}

.pipeline-link {
  position: relative;
  height: 1px;
  background: #91a2ad;
}

.pipeline-link::after {
  position: absolute;
  top: -3px;
  right: -1px;
  width: 7px;
  height: 7px;
  border-top: 1px solid #91a2ad;
  border-right: 1px solid #91a2ad;
  content: '';
  transform: rotate(45deg);
}

.pipeline-link i {
  position: absolute;
  top: -2px;
  left: 50%;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #91a2ad;
}

.pipeline-terminal {
  border: 1px solid #82bd99;
  color: #256c47;
  background: #eef9f2;
}

.pipeline-terminal svg {
  width: 18px;
  margin: 0 auto 6px;
}

.workflow-filters {
  border-bottom: 1px solid var(--color-border);
}

.table-subtitle {
  max-width: 340px;
  margin: 3px 0 0;
  overflow: hidden;
  color: var(--color-text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rule-summary {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.step-chain {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.step-chain span {
  padding: 3px 7px;
  border: 1px solid #d9cbea;
  border-radius: 4px;
  color: #65458b;
  background: #faf7fd;
  font-size: 11px;
}

.form-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: 1fr 1fr;
}

.form-grid--three {
  grid-template-columns: 1fr 1fr 1fr;
}

.drawer-form :deep(.el-select),
.drawer-form :deep(.el-input-number),
.dialog-form :deep(.el-select) {
  width: 100%;
}

.editor-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 3px 0 10px;
}

.editor-heading strong {
  font-size: 13px;
}

.condition-list {
  display: grid;
  gap: 8px;
  margin-bottom: 20px;
}

.condition-row {
  display: grid;
  align-items: center;
  gap: 7px;
  grid-template-columns: 24px 1.1fr 1fr 1fr 30px;
}

.sequence,
.step-editor__rail span {
  display: grid;
  width: 22px;
  height: 22px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 10px;
}

.empty-value {
  display: grid;
  min-height: 32px;
  place-items: center;
  color: var(--color-text-muted);
  font-size: 11px;
}

.step-editor {
  margin-bottom: 20px;
}

.step-editor__item {
  display: grid;
  gap: 10px;
  grid-template-columns: 24px 1fr;
}

.step-editor__rail {
  display: flex;
  align-items: center;
  flex-direction: column;
}

.step-editor__rail i {
  width: 1px;
  min-height: 98px;
  background: var(--color-border-strong);
}

.step-editor__body {
  padding-bottom: 12px;
}

.step-editor__title {
  display: grid;
  align-items: center;
  gap: 6px;
  margin-bottom: 7px;
  grid-template-columns: 1fr 30px;
}

.test-controls {
  display: grid;
  gap: 8px;
  grid-template-columns: 1fr 1.3fr auto;
}

.test-result {
  display: flex;
  gap: 30px;
  margin: 18px 0 12px;
  padding: 15px 0;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}

.test-result div {
  display: grid;
}

.test-result strong {
  font-family: var(--font-mono);
  font-size: 24px;
}

.test-result span {
  color: var(--color-text-muted);
  font-size: 11px;
}

@media (max-width: 760px) {
  .metric-strip {
    grid-template-columns: 1fr 1fr;
  }

  .metric-strip > div:nth-child(2) {
    border-right: 0;
  }

  .pipeline-canvas {
    padding: 20px;
  }

  .form-grid,
  .form-grid--three,
  .test-controls {
    grid-template-columns: 1fr;
  }

  .condition-row {
    padding-bottom: 12px;
    border-bottom: 1px solid var(--color-border);
    grid-template-columns: 24px 1fr 30px;
  }

  .condition-row :deep(.el-select),
  .condition-row :deep(.el-input),
  .condition-row .empty-value {
    grid-column: 2 / 3;
  }

  .condition-row :deep(.el-button) {
    grid-row: 1;
    grid-column: 3;
  }
}
</style>
