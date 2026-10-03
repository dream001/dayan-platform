<script setup lang="ts">
import {
  Delete,
  Download,
  Edit,
  Plus,
  Refresh,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  batchCreateDictionaries,
  batchDeleteDictionaries,
  createDictionary,
  deleteDictionary,
  exportDictionaries,
  getDictionaries,
  getDictionaryOverview,
  getDictionaryProjectOptions,
  updateDictionary,
} from '@/services/dictionaries'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'
import type {
  DictionaryItem,
  DictionaryPayload,
  DictionaryProjectOption,
  DictionaryScope,
  DictionarySort,
  DictionaryType,
  SortDirection,
} from '@/types/dictionary'
import { formatDateTime } from '@/utils/format'

const allTypes: DictionaryType[] = [
  'SKILL',
  'OBJECT',
  'TARGET',
  'ADVERBIAL',
  'INVALID',
  'TAG',
  'TAG_CATEGORY',
]

const { t } = useI18n()
const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const items = ref<DictionaryItem[]>([])
const total = ref(0)
const counts = ref<Partial<Record<DictionaryType, number>>>({})
const canManageGlobal = ref(false)
const projectOptions = ref<DictionaryProjectOption[]>([])
const selectedIds = ref<number[]>([])
const activeType = ref<DictionaryType>('SKILL')
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  scope: undefined as DictionaryScope | undefined,
  projectId: undefined as number | undefined,
  sort: 'CREATED_AT' as DictionarySort,
  direction: 'DESC' as SortDirection,
})

const editorOpen = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const createMode = ref<'single' | 'batch'>('single')
const editingItem = ref<DictionaryItem | null>(null)
const saving = ref(false)
const batchContent = ref('')
const form = reactive<DictionaryPayload>({
  dictionaryType: 'SKILL',
  scope: 'SHARED',
  projectId: null,
  englishText: '',
  chineseText: '',
  japaneseText: '',
})

const canManage = computed(() => auth.hasPermission('data:dict:manage'))
const visibleTypes = computed(() => allTypes.filter(
  (type) => type !== 'TAG_CATEGORY' || canManageGlobal.value,
))
const selectedCount = computed(() => selectedIds.value.length)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await getDictionaries({
      page: query.page,
      size: query.size,
      type: activeType.value,
      keyword: query.keyword.trim() || undefined,
      scope: query.scope,
      projectId: query.scope === 'PROJECT' ? query.projectId : undefined,
      sort: query.sort,
      direction: query.direction,
    })
    items.value = page.items
    total.value = page.total
    selectedIds.value = []
  } catch (reason) {
    error.value = getErrorMessage(reason, t('dictionaries.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function initialize() {
  loading.value = true
  error.value = ''
  try {
    const [overview, projects] = await Promise.all([
      getDictionaryOverview(),
      getDictionaryProjectOptions(),
    ])
    counts.value = overview.counts
    canManageGlobal.value = overview.canManageGlobal
    projectOptions.value = projects
    if (!visibleTypes.value.includes(activeType.value)) activeType.value = 'SKILL'
    await load()
  } catch (reason) {
    error.value = getErrorMessage(reason, t('dictionaries.loadFailed'))
    loading.value = false
  }
}

async function refreshAll() {
  const overview = await getDictionaryOverview()
  counts.value = overview.counts
  canManageGlobal.value = overview.canManageGlobal
  await load()
}

function selectType(type: string | number) {
  activeType.value = type as DictionaryType
  query.page = 1
  void load()
}

function search() {
  query.page = 1
  void load()
}

function resetFilters() {
  query.keyword = ''
  query.scope = undefined
  query.projectId = undefined
  query.sort = 'CREATED_AT'
  query.direction = 'DESC'
  search()
}

function resetForm() {
  Object.assign(form, {
    dictionaryType: activeType.value,
    scope: 'SHARED',
    projectId: null,
    englishText: '',
    chineseText: '',
    japaneseText: '',
  })
  batchContent.value = ''
  editingItem.value = null
  createMode.value = 'single'
}

function openCreate() {
  resetForm()
  editorMode.value = 'create'
  editorOpen.value = true
}

function openEdit(item: DictionaryItem) {
  editorMode.value = 'edit'
  createMode.value = 'single'
  editingItem.value = item
  Object.assign(form, {
    dictionaryType: item.dictionaryType,
    scope: item.scope,
    projectId: item.projectId,
    englishText: item.englishText,
    chineseText: item.chineseText,
    japaneseText: item.japaneseText ?? '',
  })
  editorOpen.value = true
}

function changeScope(scope: DictionaryScope) {
  if (scope !== 'PROJECT') form.projectId = null
}

function insertPlaceholder(placeholder: '{A}' | '{B}') {
  form.englishText = `${form.englishText}${form.englishText ? ' ' : ''}${placeholder}`
  form.chineseText = `${form.chineseText}${placeholder}`
}

async function save() {
  if (form.scope === 'PROJECT' && !form.projectId) {
    ElMessage.warning(t('dictionaries.projectRequired'))
    return
  }
  if (createMode.value === 'batch') {
    if (!batchContent.value.trim()) {
      ElMessage.warning(t('dictionaries.batchRequired'))
      return
    }
  } else if (!form.englishText.trim() || !form.chineseText.trim()) {
    ElMessage.warning(t('dictionaries.languageRequired'))
    return
  }

  saving.value = true
  try {
    if (editorMode.value === 'edit' && editingItem.value) {
      await updateDictionary(editingItem.value.id, normalizedPayload())
      ElMessage.success(t('dictionaries.updated'))
    } else if (createMode.value === 'batch') {
      const result = await batchCreateDictionaries({
        dictionaryType: activeType.value,
        scope: form.scope,
        projectId: form.scope === 'PROJECT' ? form.projectId : null,
        content: batchContent.value,
      })
      ElMessage.success(t('dictionaries.batchCreated', { count: result.created }))
    } else {
      await createDictionary(normalizedPayload())
      ElMessage.success(t('dictionaries.created'))
    }
    editorOpen.value = false
    await refreshAll()
  } catch (reason) {
    notifyError(reason, t('dictionaries.saveFailed'))
  } finally {
    saving.value = false
  }
}

function normalizedPayload(): DictionaryPayload {
  return {
    ...form,
    dictionaryType: activeType.value,
    projectId: form.scope === 'PROJECT' ? form.projectId : null,
    englishText: form.englishText.trim(),
    chineseText: form.chineseText.trim(),
    japaneseText: form.japaneseText.trim(),
  }
}

async function remove(item: DictionaryItem) {
  const confirmed = await confirmAction(
    t('dictionaries.deleteConfirm', { value: item.englishText }),
    t('dictionaries.deleteTitle'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    await deleteDictionary(item.id)
    ElMessage.success(t('dictionaries.deleted'))
    await refreshAll()
  } catch (reason) {
    notifyError(reason, t('dictionaries.deleteFailed'))
  }
}

async function removeSelected() {
  const confirmed = await confirmAction(
    t('dictionaries.batchDeleteConfirm', { count: selectedCount.value }),
    t('dictionaries.batchDelete'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    const result = await batchDeleteDictionaries(selectedIds.value)
    ElMessage.success(t('dictionaries.batchDeleted', {
      deleted: result.deleted,
      skipped: result.skipped,
    }))
    await refreshAll()
  } catch (reason) {
    notifyError(reason, t('dictionaries.deleteFailed'))
  }
}

async function exportCurrent() {
  try {
    const blob = await exportDictionaries(activeType.value)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `dictionary-${activeType.value.toLowerCase()}.csv`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (reason) {
    notifyError(reason, t('dictionaries.exportFailed'))
  }
}

function updateSelection(rows: DictionaryItem[]) {
  selectedIds.value = rows.map((row) => row.id)
}

function sortTable({ prop, order }: { prop: string; order: string | null }) {
  const fields: Record<string, DictionarySort> = {
    useCount: 'USE_COUNT',
    englishText: 'ENGLISH',
    chineseText: 'CHINESE',
    japaneseText: 'JAPANESE',
    createdAt: 'CREATED_AT',
  }
  query.sort = fields[prop] ?? 'CREATED_AT'
  query.direction = order === 'ascending' ? 'ASC' : 'DESC'
  search()
}

function scopeLabel(item: DictionaryItem) {
  if (item.scope === 'PROJECT') return item.projectName ?? t('dictionaries.scopes.PROJECT')
  return t(`dictionaries.scopes.${item.scope}`)
}

function scopeTagType(scope: DictionaryScope) {
  if (scope === 'GLOBAL') return 'warning'
  if (scope === 'PROJECT') return 'success'
  return 'info'
}

onMounted(initialize)
</script>

<template>
  <section class="admin-page dictionaries-page">
    <PageHeader
      :title="t('dictionaries.title')"
      :eyebrow="t('dictionaries.eyebrow')"
      :description="t('dictionaries.description')"
    >
      <template #actions>
        <el-tooltip :content="t('dictionaries.refresh')">
          <el-button
            :icon="Refresh"
            circle
            :loading="loading"
            :aria-label="t('dictionaries.refresh')"
            @click="refreshAll"
          />
        </el-tooltip>
        <el-button
          :icon="Download"
          @click="exportCurrent"
        >
          {{ t('dictionaries.export') }}
        </el-button>
        <el-button
          v-if="canManage"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          {{ t('dictionaries.create') }}
        </el-button>
      </template>
    </PageHeader>

    <el-tabs
      v-model="activeType"
      class="dictionary-tabs"
      @tab-change="selectType"
    >
      <el-tab-pane
        v-for="type in visibleTypes"
        :key="type"
        :name="type"
      >
        <template #label>
          <span class="dictionary-tab">
            {{ t(`dictionaries.types.${type}`) }}
            <small>{{ counts[type] ?? 0 }}</small>
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <form
      class="filter-bar dictionary-filters"
      @submit.prevent="search"
    >
      <el-input
        v-model="query.keyword"
        clearable
        :placeholder="t('dictionaries.keyword')"
      />
      <el-select
        v-model="query.scope"
        clearable
        :placeholder="t('dictionaries.allScopes')"
        @change="changeScope"
      >
        <el-option
          v-for="scope in ['SHARED', 'PROJECT', 'GLOBAL']"
          :key="scope"
          :label="t(`dictionaries.scopes.${scope}`)"
          :value="scope"
          :disabled="scope === 'GLOBAL' && !canManageGlobal"
        />
      </el-select>
      <el-select
        v-if="query.scope === 'PROJECT'"
        v-model="query.projectId"
        clearable
        filterable
        :placeholder="t('dictionaries.allProjects')"
      >
        <el-option
          v-for="project in projectOptions"
          :key="project.id"
          :label="project.name"
          :value="project.id"
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
      <el-button
        v-if="canManage && selectedCount"
        type="danger"
        plain
        :icon="Delete"
        @click="removeSelected"
      >
        {{ t('dictionaries.deleteSelected', { count: selectedCount }) }}
      </el-button>
    </form>

    <StatePanel
      v-if="loading && !items.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !items.length"
      state="error"
      :title="t('dictionaries.loadFailed')"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!items.length"
      state="empty"
      :title="t('dictionaries.empty')"
      :description="t('dictionaries.emptyDesc')"
    />
    <template v-else>
      <div class="table-shell">
        <el-table
          v-loading="loading"
          :data="items"
          row-key="id"
          @selection-change="updateSelection"
          @sort-change="sortTable"
        >
          <el-table-column
            v-if="canManage"
            type="selection"
            width="44"
            :selectable="(row: DictionaryItem) => row.canDelete"
          />
          <el-table-column
            prop="useCount"
            :label="t('dictionaries.useCount')"
            width="96"
            sortable="custom"
            align="right"
          />
          <el-table-column
            prop="englishText"
            :label="t('dictionaries.english')"
            min-width="190"
            sortable="custom"
          >
            <template #default="{ row }">
              <strong class="dictionary-primary">{{ row.englishText }}</strong>
            </template>
          </el-table-column>
          <el-table-column
            prop="chineseText"
            :label="t('dictionaries.chinese')"
            min-width="180"
            sortable="custom"
          />
          <el-table-column
            prop="japaneseText"
            :label="t('dictionaries.japanese')"
            min-width="180"
            sortable="custom"
          >
            <template #default="{ row }">
              {{ row.japaneseText || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('dictionaries.scope')"
            min-width="145"
          >
            <template #default="{ row }">
              <el-tag
                :type="scopeTagType(row.scope)"
                effect="plain"
                size="small"
              >
                {{ scopeLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="createdAt"
            :label="t('dictionaries.createdAt')"
            min-width="170"
            sortable="custom"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('common.operation')"
            width="94"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="table-actions">
                <el-tooltip :content="row.canEdit ? t('common.edit') : t('dictionaries.noPermission')">
                  <el-button
                    :icon="Edit"
                    circle
                    text
                    :disabled="!row.canEdit"
                    :aria-label="t('common.edit')"
                    @click="openEdit(row)"
                  />
                </el-tooltip>
                <el-tooltip :content="row.canDelete ? t('common.delete') : t('dictionaries.noPermission')">
                  <el-button
                    :icon="Delete"
                    circle
                    text
                    type="danger"
                    :disabled="!row.canDelete"
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
      :title="editorMode === 'create' ? t('dictionaries.create') : t('dictionaries.edit')"
      size="560px"
      destroy-on-close
    >
      <el-form
        class="drawer-form"
        label-position="top"
        @submit.prevent="save"
      >
        <el-segmented
          v-if="editorMode === 'create'"
          v-model="createMode"
          class="create-mode"
          :options="[
            { label: t('dictionaries.singleMode'), value: 'single' },
            { label: t('dictionaries.batchMode'), value: 'batch' },
          ]"
        />

        <div class="scope-selector">
          <span>{{ t('dictionaries.scope') }}</span>
          <el-radio-group
            v-model="form.scope"
            :disabled="editingItem?.scope === 'GLOBAL'"
            @change="changeScope"
          >
            <el-radio-button value="SHARED">
              {{ t('dictionaries.scopes.SHARED') }}
            </el-radio-button>
            <el-radio-button value="PROJECT">
              {{ t('dictionaries.scopes.PROJECT') }}
            </el-radio-button>
            <el-radio-button
              value="GLOBAL"
              :disabled="!canManageGlobal"
            >
              {{ t('dictionaries.scopes.GLOBAL') }}
            </el-radio-button>
          </el-radio-group>
        </div>

        <el-form-item
          v-if="form.scope === 'PROJECT'"
          :label="t('dictionaries.project')"
          required
        >
          <el-select
            v-model="form.projectId"
            filterable
            :placeholder="t('dictionaries.selectProject')"
          >
            <el-option
              v-for="project in projectOptions"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
        </el-form-item>

        <template v-if="createMode === 'single'">
          <div
            v-if="activeType === 'SKILL'"
            class="placeholder-tools"
          >
            <span>{{ t('dictionaries.placeholders') }}</span>
            <el-button
              size="small"
              @click="insertPlaceholder('{A}')"
            >
              {'{A}'}
            </el-button>
            <el-button
              size="small"
              @click="insertPlaceholder('{B}')"
            >
              {'{B}'}
            </el-button>
          </div>
          <el-form-item
            :label="t('dictionaries.english')"
            required
          >
            <el-input
              v-model="form.englishText"
              maxlength="300"
            />
          </el-form-item>
          <el-form-item
            :label="t('dictionaries.chinese')"
            required
          >
            <el-input
              v-model="form.chineseText"
              maxlength="300"
            />
          </el-form-item>
          <el-form-item :label="t('dictionaries.japanese')">
            <el-input
              v-model="form.japaneseText"
              maxlength="300"
            />
          </el-form-item>
        </template>
        <el-form-item
          v-else
          :label="t('dictionaries.batchContent')"
          required
        >
          <el-input
            v-model="batchContent"
            type="textarea"
            :rows="12"
            maxlength="30000"
            :placeholder="t('dictionaries.batchPlaceholder')"
          />
          <small class="field-hint">{{ t('dictionaries.batchHint') }}</small>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
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
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.dictionary-tabs {
  margin-top: 8px;
}

.dictionary-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.dictionary-tab {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.dictionary-tab small {
  min-width: 22px;
  padding: 1px 6px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
  line-height: 18px;
  text-align: center;
}

.dictionary-filters {
  border-bottom: 1px solid var(--color-border);
}

.dictionary-primary {
  color: var(--color-ink);
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 650;
}

.create-mode {
  width: 100%;
  margin-bottom: 24px;
}

.scope-selector {
  display: grid;
  gap: 10px;
  margin-bottom: 22px;
}

.scope-selector > span {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.scope-selector :deep(.el-radio-group) {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.scope-selector :deep(.el-radio-button__inner) {
  width: 100%;
}

.drawer-form :deep(.el-select) {
  width: 100%;
}

.placeholder-tools {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 0 16px;
  border-top: 1px solid var(--color-border);
}

.placeholder-tools span {
  margin-right: auto;
  color: var(--color-text-muted);
  font-size: 11px;
}

.field-hint {
  margin-top: 7px;
  color: var(--color-text-muted);
  font-size: 11px;
  line-height: 1.6;
}

@media (max-width: 700px) {
  .dictionary-tabs {
    overflow-x: auto;
  }

  .dictionary-tabs :deep(.el-tabs__nav-wrap) {
    min-width: 650px;
  }

  .scope-selector :deep(.el-radio-group) {
    grid-template-columns: 1fr;
  }

  .scope-selector :deep(.el-radio-button__inner) {
    border-left: 1px solid var(--el-border-color);
  }
}
</style>
