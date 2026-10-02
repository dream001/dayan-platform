<script setup lang="ts">
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
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

const loading = ref(false)
const error = ref('')
const departments = ref<DepartmentNode[]>([])
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
    error.value = getErrorMessage(reason, '部门树加载失败')
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
    ElMessage.warning('请填写部门名称和编码，排序值不能小于 0')
    return
  }
  saving.value = true
  try {
    const payload = { ...form, name: form.name.trim(), code: form.code.trim() }
    if (mode.value === 'create') {
      await createDepartment(payload)
      ElMessage.success('部门已新增')
    } else if (editingId.value) {
      await updateDepartment(editingId.value, payload)
      ElMessage.success('部门已更新')
    }
    drawerOpen.value = false
    await load()
  } catch (reason) {
    notifyError(reason, '部门保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(node: DepartmentNode) {
  const confirmed = await confirmAction(
    `删除部门“${node.name}”？存在子部门或用户时无法删除。`,
    '删除部门',
    '删除',
  )
  if (!confirmed) return
  try {
    await deleteDepartment(node.id)
    ElMessage.success('部门已删除')
    await load()
  } catch (reason) {
    notifyError(reason, '部门删除失败')
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <PageHeader
      title="部门管理"
      eyebrow="System / Departments"
      description="维护组织层级、状态与排序。"
    >
      <template #actions>
        <el-tooltip content="刷新部门树">
          <el-button
            :icon="Refresh"
            circle
            aria-label="刷新部门树"
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
          新增部门
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
      title="部门树加载失败"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!departments.length"
      state="empty"
      title="暂无部门"
      description="新增首个部门以建立组织结构。"
    />
    <div
      v-else
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
          label="部门名称"
          min-width="240"
        />
        <el-table-column
          prop="code"
          label="部门编码"
          min-width="180"
        >
          <template #default="{ row }">
            <span class="mono">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="sortOrder"
          label="排序"
          width="84"
          align="right"
        />
        <el-table-column
          label="状态"
          width="90"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.enabled ? 'success' : 'info'"
              effect="plain"
            >
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="138"
          fixed="right"
        >
          <template #default="{ row }">
            <div class="table-actions">
              <el-tooltip content="新增下级部门">
                <el-button
                  v-permission="'system:department:create'"
                  :icon="Plus"
                  circle
                  text
                  aria-label="新增下级部门"
                  @click="openCreate(row)"
                />
              </el-tooltip>
              <el-tooltip content="编辑部门">
                <el-button
                  v-permission="'system:department:update'"
                  :icon="Edit"
                  circle
                  text
                  aria-label="编辑部门"
                  @click="openEdit(row)"
                />
              </el-tooltip>
              <el-tooltip content="删除部门">
                <el-button
                  v-permission="'system:department:delete'"
                  :icon="Delete"
                  circle
                  text
                  type="danger"
                  aria-label="删除部门"
                  @click="remove(row)"
                />
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer
      v-model="drawerOpen"
      :title="mode === 'create' ? '新增部门' : '编辑部门'"
      size="420px"
    >
      <el-form
        class="drawer-form"
        label-position="top"
        @submit.prevent="save"
      >
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="departments"
            :props="treeProps"
            check-strictly
            clearable
            :disabled="mode === 'edit'"
            placeholder="无（顶级部门）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          label="部门名称"
          required
        >
          <el-input
            v-model="form.name"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item
          label="部门编码"
          required
        >
          <el-input
            v-model="form.code"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            v-model="form.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.enabled">
            启用部门
          </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerOpen = false">
            取消
          </el-button>
          <el-button
            type="primary"
            :loading="saving"
            @click="save"
          >
            保存
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
</style>
