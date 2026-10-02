<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Connection, Delete, Edit, List, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import OrganizationChart from '@/components/OrganizationChart.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  createDepartment,
  deleteDepartment,
  getDepartments,
  updateDepartment,
} from '@/services/admin'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import type { DepartmentNode, DepartmentPayload } from '@/types/admin'

const { t } = useI18n()

const loading = ref(false)
const error = ref('')
const departments = ref<DepartmentNode[]>([])
const viewMode = ref<'table' | 'chart'>('chart')
const drawerOpen = ref(false)
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive<DepartmentPayload>({
  parentId: null,
  name: '',
  code: '',
  sortOrder: 0,
  enabled: true,
})
const treeProps = { label: 'name', children: 'children', value: 'id' }

async function load() {
  loading.value = true
  error.value = ''
  try {
    departments.value = await getDepartments()
  } catch (reason) {
    error.value = getErrorMessage(reason, t('departments.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(parent: DepartmentNode | null = null) {
  mode.value = 'create'
  editingId.value = null
  Object.assign(form, {
    parentId: parent?.id ?? null,
    name: '',
    code: '',
    sortOrder: 0,
    enabled: true,
  })
  drawerOpen.value = true
}

function openEdit(node: DepartmentNode) {
  mode.value = 'edit'
  editingId.value = node.id
  Object.assign(form, {
    parentId: node.parentId,
    name: node.name,
    code: node.code,
    sortOrder: node.sortOrder,
    enabled: node.enabled,
  })
  drawerOpen.value = true
}

async function save() {
  if (!form.name.trim() || !form.code.trim() || form.sortOrder < 0) {
    ElMessage.warning(t('departments.validation'))
    return
  }
  saving.value = true
  try {
    const payload = { ...form, name: form.name.trim(), code: form.code.trim() }
    if (mode.value === 'create') {
      await createDepartment(payload)
      ElMessage.success(t('departments.created'))
    } else if (editingId.value) {
      await updateDepartment(editingId.value, payload)
      ElMessage.success(t('departments.updated'))
    }
    drawerOpen.value = false
    await load()
  } catch (reason) {
    notifyError(reason, t('departments.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function remove(node: DepartmentNode) {
  const confirmed = await confirmAction(
    t('departments.deleteConfirm', { name: node.name }),
    t('departments.deleteTitle'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    await deleteDepartment(node.id)
    ElMessage.success(t('departments.deleted'))
    await load()
  } catch (reason) {
    notifyError(reason, t('departments.deleteFailed'))
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <PageHeader
      :title="t('departments.title')"
      :eyebrow="t('departments.eyebrow')"
      :description="t('departments.description')"
    >
      <template #actions>
        <el-radio-group
          v-model="viewMode"
          class="view-switcher"
          size="small"
          :aria-label="t('departments.viewMode')"
        >
          <el-radio-button value="table">
            <el-icon><List /></el-icon>
            <span>{{ t('departments.tableView') }}</span>
          </el-radio-button>
          <el-radio-button value="chart">
            <el-icon><Connection /></el-icon>
            <span>{{ t('departments.chartView') }}</span>
          </el-radio-button>
        </el-radio-group>
        <el-tooltip :content="t('departments.refreshTree')">
          <el-button
            :icon="Refresh"
            circle
            :aria-label="t('departments.refreshTree')"
            :loading="loading"
            @click="load"
          />
        </el-tooltip>
        <el-button
          v-permission="'system:department:create'"
          type="primary"
          :icon="Plus"
          @click="openCreate()"
        >
          {{ t('departments.create') }}
        </el-button>
      </template>
    </PageHeader>

    <StatePanel
      v-if="loading && !departments.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error"
      state="error"
      :title="t('departments.loadFailed')"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!departments.length"
      state="empty"
      :title="t('departments.empty')"
      :description="t('departments.emptyDesc')"
    />
    <div
      v-else-if="viewMode === 'table'"
      class="tree-table"
    >
      <el-table
        :data="departments"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column
          prop="name"
          :label="t('departments.name')"
          min-width="240"
        />
        <el-table-column
          prop="code"
          :label="t('departments.code')"
          min-width="180"
        >
          <template #default="{ row }">
            <span class="mono">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="sortOrder"
          :label="t('departments.sort')"
          width="84"
          align="right"
        />
        <el-table-column
          :label="t('common.status')"
          width="90"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.enabled ? 'success' : 'info'"
              effect="plain"
            >
              {{ row.enabled ? t('departments.statusEnabled') : t('departments.statusDisabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.operation')"
          width="138"
          fixed="right"
        >
          <template #default="{ row }">
            <div class="table-actions">
              <el-tooltip :content="t('departments.addChild')">
                <el-button
                  v-permission="'system:department:create'"
                  :icon="Plus"
                  circle
                  text
                  :aria-label="t('departments.addChild')"
                  @click="openCreate(row)"
                />
              </el-tooltip>
              <el-tooltip :content="t('departments.edit')">
                <el-button
                  v-permission="'system:department:update'"
                  :icon="Edit"
                  circle
                  text
                  :aria-label="t('departments.edit')"
                  @click="openEdit(row)"
                />
              </el-tooltip>
              <el-tooltip :content="t('departments.delete')">
                <el-button
                  v-permission="'system:department:delete'"
                  :icon="Delete"
                  circle
                  text
                  type="danger"
                  :aria-label="t('departments.delete')"
                  @click="remove(row)"
                />
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <OrganizationChart
      v-else
      :departments="departments"
      @create-child="openCreate"
      @delete="remove"
      @edit="openEdit"
    />

    <el-drawer
      v-model="drawerOpen"
      :title="mode === 'create' ? t('departments.create') : t('departments.edit')"
      size="420px"
    >
      <el-form
        class="drawer-form"
        label-position="top"
        @submit.prevent="save"
      >
        <el-form-item :label="t('departments.parentField')">
          <el-tree-select
            v-model="form.parentId"
            :data="departments"
            :props="treeProps"
            check-strictly
            clearable
            :disabled="mode === 'edit'"
            :placeholder="t('departments.noParent')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          :label="t('departments.nameField')"
          required
        >
          <el-input
            v-model="form.name"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item
          :label="t('departments.codeField')"
          required
        >
          <el-input
            v-model="form.code"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item :label="t('departments.sort')">
          <el-input-number
            v-model="form.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.enabled">
            {{ t('departments.enable') }}
          </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerOpen = false">
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
.tree-table {
  margin-top: 22px;
  overflow: hidden;
  border-top: 1px solid var(--color-border);
}

.view-switcher :deep(.el-radio-button__inner) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
}

@media (max-width: 700px) {
  .view-switcher span {
    display: none;
  }
}
</style>
