<script setup lang="ts">
import { Delete, Edit, Key, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElTree } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  createRole,
  deleteRole,
  getMenus,
  getRole,
  getRoles,
  grantRolePermissions,
  updateRole,
} from '@/services/admin'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import type { MenuNode, RolePayload, RoleSummary } from '@/types/admin'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const error = ref('')
const roles = ref<RoleSummary[]>([])
const menus = ref<MenuNode[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  enabled: undefined as boolean | undefined,
})
const drawerOpen = ref(false)
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive<RolePayload>({ name: '', code: '', description: '', enabled: true })
const permissionDialogOpen = ref(false)
const permissionRole = ref<RoleSummary | null>(null)
const permissionTree = ref<InstanceType<typeof ElTree>>()
const checkedPermissionIds = ref<number[]>([])

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await getRoles({
      page: query.page,
      size: query.size,
      keyword: query.keyword.trim() || undefined,
      enabled: query.enabled,
    })
    roles.value = page.items
    total.value = page.total
  } catch (reason) {
    error.value = getErrorMessage(reason, '角色列表加载失败')
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
  query.enabled = undefined
  search()
}

function openCreate() {
  mode.value = 'create'
  editingId.value = null
  Object.assign(form, { name: '', code: '', description: '', enabled: true })
  drawerOpen.value = true
}

function openEdit(role: RoleSummary) {
  mode.value = 'edit'
  editingId.value = role.id
  Object.assign(form, {
    name: role.name,
    code: role.code,
    description: role.description ?? '',
    enabled: role.enabled,
  })
  drawerOpen.value = true
}

async function saveRole() {
  if (!form.name.trim() || !/^[A-Za-z][A-Za-z0-9_]*$/.test(form.code)) {
    ElMessage.warning('请填写名称，角色编码需以字母开头且仅含字母、数字和下划线')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      name: form.name.trim(),
      code: form.code.trim(),
      description: form.description.trim(),
    }
    if (mode.value === 'create') {
      await createRole(payload)
      ElMessage.success('角色已新增')
    } else if (editingId.value) {
      await updateRole(editingId.value, payload)
      ElMessage.success('角色已更新')
    }
    drawerOpen.value = false
    await load()
  } catch (reason) {
    notifyError(reason, '角色保存失败')
  } finally {
    saving.value = false
  }
}

async function removeRole(role: RoleSummary) {
  const confirmed = await confirmAction(
    `删除角色“${role.name}”？有关联用户或内置角色将无法删除。`,
    '删除角色',
    '删除',
  )
  if (!confirmed) return
  try {
    await deleteRole(role.id)
    ElMessage.success('角色已删除')
    await load()
  } catch (reason) {
    notifyError(reason, '角色删除失败')
  }
}

async function openPermissions(role: RoleSummary) {
  permissionRole.value = role
  permissionDialogOpen.value = true
  saving.value = true
  try {
    const [detail, tree] = await Promise.all([
      getRole(role.id),
      menus.length ? Promise.resolve(menus.value) : getMenus(),
    ])
    menus.value = tree
    checkedPermissionIds.value = detail.permissionIds
  } catch (reason) {
    permissionDialogOpen.value = false
    notifyError(reason, '权限数据加载失败')
  } finally {
    saving.value = false
  }
}

async function savePermissions() {
  if (!permissionRole.value) return
  saving.value = true
  try {
    const ids = (permissionTree.value?.getCheckedKeys(false) ?? []) as number[]
    await grantRolePermissions(permissionRole.value.id, ids)
    ElMessage.success('角色权限已保存')
    permissionDialogOpen.value = false
  } catch (reason) {
    notifyError(reason, '角色授权失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <PageHeader
      title="角色管理"
      eyebrow="System / Roles"
      description="维护角色及其菜单、按钮权限。"
    >
      <template #actions>
        <el-button
          v-permission="'system:role:create'"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          新增角色
        </el-button>
      </template>
    </PageHeader>

    <form
      class="filter-bar"
      @submit.prevent="search"
    >
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="角色名称或编码"
      />
      <el-select
        v-model="query.enabled"
        clearable
        placeholder="全部状态"
      >
        <el-option
          label="已启用"
          :value="true"
        />
        <el-option
          label="已停用"
          :value="false"
        />
      </el-select>
      <el-button
        type="primary"
        native-type="submit"
      >
        查询
      </el-button>
      <el-button @click="resetFilters">
        重置
      </el-button>
      <el-tooltip content="刷新列表">
        <el-button
          :icon="Refresh"
          circle
          aria-label="刷新列表"
          @click="load"
        />
      </el-tooltip>
    </form>

    <StatePanel
      v-if="loading && !roles.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !roles.length"
      state="error"
      title="角色列表加载失败"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!roles.length"
      state="empty"
      title="未找到角色"
      description="调整筛选条件，或新增一个角色。"
    />
    <template v-else>
      <div class="table-shell">
        <el-table
          v-loading="loading"
          :data="roles"
          row-key="id"
        >
          <el-table-column
            prop="name"
            label="角色名称"
            min-width="160"
            fixed="left"
          >
            <template #default="{ row }">
              <strong>{{ row.name }}</strong>
              <el-tag
                v-if="row.builtIn"
                size="small"
                effect="plain"
                class="built-in-tag"
              >
                内置
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="code"
            label="角色编码"
            min-width="170"
          >
            <template #default="{ row }">
              <span class="mono">{{ row.code }}</span>
            </template>
          </el-table-column>
          <el-table-column
            prop="description"
            label="说明"
            min-width="240"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.description || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="userCount"
            label="用户数"
            width="90"
            align="right"
          />
          <el-table-column
            label="状态"
            width="88"
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
            label="创建时间"
            min-width="165"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="136"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="table-actions">
                <el-tooltip content="编辑角色">
                  <el-button
                    v-permission="'system:role:update'"
                    :icon="Edit"
                    circle
                    text
                    aria-label="编辑角色"
                    @click="openEdit(row)"
                  />
                </el-tooltip>
                <el-tooltip content="菜单和按钮授权">
                  <el-button
                    v-permission="'system:role:grant'"
                    :icon="Key"
                    circle
                    text
                    aria-label="角色授权"
                    @click="openPermissions(row)"
                  />
                </el-tooltip>
                <el-tooltip :content="row.builtIn ? '内置角色不可删除' : '删除角色'">
                  <span>
                    <el-button
                      v-permission="'system:role:delete'"
                      :icon="Delete"
                      circle
                      text
                      type="danger"
                      aria-label="删除角色"
                      :disabled="row.builtIn"
                      @click="removeRole(row)"
                    />
                  </span>
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
      v-model="drawerOpen"
      :title="mode === 'create' ? '新增角色' : '编辑角色'"
      size="420px"
    >
      <el-form
        class="drawer-form"
        label-position="top"
        @submit.prevent="saveRole"
      >
        <el-form-item
          label="角色名称"
          required
        >
          <el-input
            v-model="form.name"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item
          label="角色编码"
          required
        >
          <el-input
            v-model="form.code"
            maxlength="64"
            placeholder="例如 DATA_OPERATOR"
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.enabled">
            启用角色
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
            @click="saveRole"
          >
            保存
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="permissionDialogOpen"
      :title="`角色授权 · ${permissionRole?.name ?? ''}`"
      width="560px"
    >
      <p class="permission-hint">
        勾选角色可访问的菜单与按钮权限。树节点按后端权限数据实时生成。
      </p>
      <el-skeleton
        v-if="saving && !menus.length"
        :rows="6"
        animated
      />
      <el-tree
        v-else
        ref="permissionTree"
        class="permission-tree"
        :data="menus"
        node-key="id"
        show-checkbox
        check-strictly
        default-expand-all
        :default-checked-keys="checkedPermissionIds"
        :props="{ label: 'name', children: 'children' }"
      >
        <template #default="{ data }">
          <span class="permission-node">
            <span>{{ data.name }}</span>
            <small>{{ data.type === 'BUTTON' ? '按钮' : '菜单' }} · {{ data.code || '无权限码' }}</small>
          </span>
        </template>
      </el-tree>
      <template #footer>
        <el-button @click="permissionDialogOpen = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="savePermissions"
        >
          保存授权
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.built-in-tag {
  margin-left: 8px;
}

.permission-hint {
  margin: 0 0 16px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.permission-tree {
  max-height: 55vh;
  overflow: auto;
}

.permission-node {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 10px;
}

.permission-node small {
  overflow: hidden;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
