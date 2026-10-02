<script setup lang="ts">
import { Edit, Key, Plus, Refresh, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  assignUserRoles,
  changeUserStatus,
  createUser,
  getDepartments,
  getRoles,
  getUsers,
  resetUserPassword,
  updateUser,
} from '@/services/admin'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'
import type { DepartmentNode, RoleSummary, UserCreatePayload, UserSummary } from '@/types/admin'
import { formatDateTime } from '@/utils/format'

const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const users = ref<UserSummary[]>([])
const departments = ref<DepartmentNode[]>([])
const roles = ref<RoleSummary[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  departmentId: undefined as number | undefined,
  enabled: undefined as boolean | undefined,
})
const drawerOpen = ref(false)
const drawerMode = ref<'create' | 'edit'>('create')
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<UserCreatePayload>({
  departmentId: null,
  username: '',
  password: '',
  displayName: '',
  email: '',
  phone: '',
  enabled: true,
  roleIds: [],
})
const roleDialogOpen = ref(false)
const roleUser = ref<UserSummary | null>(null)
const selectedRoleIds = ref<number[]>([])
const passwordDialogOpen = ref(false)
const passwordUser = ref<UserSummary | null>(null)
const newPassword = ref('')

const departmentProps = { label: 'name', children: 'children', value: 'id' }
const canLoadDepartments = computed(() => auth.hasPermission('system:department:view'))
const canLoadRoles = computed(() => auth.hasPermission('system:role:view'))

function requestParams() {
  return {
    page: query.page,
    size: query.size,
    keyword: query.keyword.trim() || undefined,
    departmentId: query.departmentId,
    enabled: query.enabled,
  }
}

async function loadUsers() {
  loading.value = true
  error.value = ''
  try {
    const page = await getUsers(requestParams())
    users.value = page.items
    total.value = page.total
  } catch (reason) {
    error.value = getErrorMessage(reason, '用户列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const tasks: Promise<void>[] = []
  if (canLoadDepartments.value) {
    tasks.push(getDepartments().then((value) => {
      departments.value = value
    }).catch(() => undefined))
  }
  if (canLoadRoles.value) {
    tasks.push(getRoles({ page: 1, size: 200 }).then((value) => {
      roles.value = value.items
    }).catch(() => undefined))
  }
  await Promise.all(tasks)
}

function search() {
  query.page = 1
  void loadUsers()
}

function resetFilters() {
  query.keyword = ''
  query.departmentId = undefined
  query.enabled = undefined
  search()
}

function resetForm() {
  Object.assign(form, {
    departmentId: null,
    username: '',
    password: '',
    displayName: '',
    email: '',
    phone: '',
    enabled: true,
    roleIds: [],
  })
  editingId.value = null
}

function openCreate() {
  resetForm()
  drawerMode.value = 'create'
  drawerOpen.value = true
}

function openEdit(user: UserSummary) {
  resetForm()
  drawerMode.value = 'edit'
  editingId.value = user.id
  Object.assign(form, {
    departmentId: user.departmentId,
    username: user.username,
    displayName: user.displayName,
    email: user.email ?? '',
    phone: user.phone ?? '',
    roleIds: user.roles.map((role) => role.id),
  })
  drawerOpen.value = true
}

async function saveUser() {
  if (!form.displayName.trim() || (drawerMode.value === 'create' && (!form.username.trim() || form.password.length < 12))) {
    ElMessage.warning('请完整填写必填项，新用户密码至少 12 个字符')
    return
  }
  saving.value = true
  try {
    if (drawerMode.value === 'create') {
      await createUser({
        ...form,
        username: form.username.trim(),
        displayName: form.displayName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
      })
      ElMessage.success('用户已新增')
    } else if (editingId.value) {
      await updateUser(editingId.value, {
        departmentId: form.departmentId,
        displayName: form.displayName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
      })
      ElMessage.success('用户资料已更新')
    }
    drawerOpen.value = false
    await loadUsers()
  } catch (reason) {
    notifyError(reason, '用户保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(user: UserSummary, enabled: boolean) {
  const action = enabled ? '启用' : '停用'
  const confirmed = await confirmAction(`${action}用户“${user.displayName}”？`, `${action}用户`, action)
  if (!confirmed) {
    user.enabled = !enabled
    return
  }
  try {
    await changeUserStatus(user.id, enabled)
    ElMessage.success(`用户已${action}`)
  } catch (reason) {
    user.enabled = !enabled
    notifyError(reason, `${action}用户失败`)
  }
}

function openRoleDialog(user: UserSummary) {
  roleUser.value = user
  selectedRoleIds.value = user.roles.map((role) => role.id)
  roleDialogOpen.value = true
}

async function saveRoles() {
  if (!roleUser.value) return
  saving.value = true
  try {
    await assignUserRoles(roleUser.value.id, selectedRoleIds.value)
    ElMessage.success('角色分配已保存')
    roleDialogOpen.value = false
    await loadUsers()
  } catch (reason) {
    notifyError(reason, '角色分配失败')
  } finally {
    saving.value = false
  }
}

function openPasswordDialog(user: UserSummary) {
  passwordUser.value = user
  newPassword.value = ''
  passwordDialogOpen.value = true
}

async function savePassword() {
  if (!passwordUser.value || newPassword.value.length < 12) {
    ElMessage.warning('新密码至少 12 个字符')
    return
  }
  saving.value = true
  try {
    await resetUserPassword(passwordUser.value.id, newPassword.value)
    ElMessage.success('密码已重置')
    passwordDialogOpen.value = false
  } catch (reason) {
    notifyError(reason, '密码重置失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void Promise.all([loadUsers(), loadOptions()])
})
</script>

<template>
  <section class="admin-page">
    <PageHeader
      title="用户管理"
      eyebrow="System / Users"
      description="查询账户，维护资料、状态和角色。"
    >
      <template #actions>
        <el-button
          v-permission="'system:user:create'"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          新增用户
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
        placeholder="用户名、姓名或邮箱"
      />
      <el-tree-select
        v-if="canLoadDepartments"
        v-model="query.departmentId"
        :data="departments"
        :props="departmentProps"
        check-strictly
        clearable
        placeholder="全部部门"
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
          @click="loadUsers"
        />
      </el-tooltip>
    </form>

    <StatePanel
      v-if="loading && !users.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !users.length"
      state="error"
      title="用户列表加载失败"
      :description="error"
      @retry="loadUsers"
    />
    <StatePanel
      v-else-if="!users.length"
      state="empty"
      title="未找到用户"
      description="调整筛选条件，或新增一个用户。"
    />
    <template v-else>
      <div class="table-shell">
        <el-table
          v-loading="loading"
          :data="users"
          row-key="id"
        >
          <el-table-column
            label="用户"
            min-width="190"
            fixed="left"
          >
            <template #default="{ row }">
              <div class="user-cell">
                <strong>{{ row.displayName }}</strong>
                <span>@{{ row.username }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            prop="departmentName"
            label="部门"
            min-width="130"
          >
            <template #default="{ row }">
              {{ row.departmentName || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="角色"
            min-width="180"
          >
            <template #default="{ row }">
              <span v-if="!row.roles.length">—</span>
              <el-tag
                v-for="role in row.roles"
                v-else
                :key="role.id"
                class="role-tag"
                size="small"
                effect="plain"
              >
                {{ role.name }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="email"
            label="邮箱"
            min-width="190"
          >
            <template #default="{ row }">
              {{ row.email || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="最近登录"
            min-width="165"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.lastLoginAt) }}
            </template>
          </el-table-column>
          <el-table-column
            label="状态"
            width="88"
          >
            <template #default="{ row }">
              <el-switch
                v-permission="'system:user:change-status'"
                v-model="row.enabled"
                aria-label="切换用户状态"
                @change="(value: boolean) => toggleStatus(row, value)"
              />
              <el-tag
                v-if="!auth.hasPermission('system:user:change-status')"
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
            width="142"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="table-actions">
                <el-tooltip content="编辑用户">
                  <el-button
                    v-permission="'system:user:update'"
                    :icon="Edit"
                    circle
                    text
                    aria-label="编辑用户"
                    @click="openEdit(row)"
                  />
                </el-tooltip>
                <el-tooltip content="分配角色">
                  <el-button
                    v-permission="'system:user:assign-role'"
                    :icon="UserFilled"
                    circle
                    text
                    aria-label="分配角色"
                    @click="openRoleDialog(row)"
                  />
                </el-tooltip>
                <el-tooltip content="重置密码">
                  <el-button
                    v-permission="'system:user:reset-password'"
                    :icon="Key"
                    circle
                    text
                    aria-label="重置密码"
                    @click="openPasswordDialog(row)"
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
          @change="loadUsers"
        />
      </div>
    </template>

    <el-drawer
      v-model="drawerOpen"
      :title="drawerMode === 'create' ? '新增用户' : '编辑用户'"
      size="440px"
      destroy-on-close
    >
      <el-form
        class="drawer-form"
        label-position="top"
        @submit.prevent="saveUser"
      >
        <el-form-item
          v-if="drawerMode === 'create'"
          label="用户名"
          required
        >
          <el-input
            v-model="form.username"
            maxlength="64"
            placeholder="字母、数字、点、横线或下划线"
          />
        </el-form-item>
        <el-form-item
          v-if="drawerMode === 'create'"
          label="初始密码"
          required
        >
          <el-input
            v-model="form.password"
            type="password"
            show-password
            minlength="12"
            maxlength="72"
            autocomplete="new-password"
            placeholder="至少 12 个字符"
          />
        </el-form-item>
        <el-form-item
          label="显示名称"
          required
        >
          <el-input
            v-model="form.displayName"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item
          v-if="canLoadDepartments"
          label="所属部门"
        >
          <el-tree-select
            v-model="form.departmentId"
            :data="departments"
            :props="departmentProps"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input
            v-model="form.email"
            type="email"
            maxlength="254"
          />
        </el-form-item>
        <el-form-item label="电话">
          <el-input
            v-model="form.phone"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item
          v-if="drawerMode === 'create' && canLoadRoles"
          label="初始角色"
        >
          <el-select
            v-model="form.roleIds"
            multiple
            style="width: 100%"
          >
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="role.name"
              :value="role.id"
              :disabled="!role.enabled"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="drawerMode === 'create'">
          <el-checkbox v-model="form.enabled">
            创建后立即启用
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
            @click="saveUser"
          >
            保存
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="roleDialogOpen"
      title="分配角色"
      width="440px"
    >
      <p class="dialog-hint">
        为 {{ roleUser?.displayName }} 选择角色，保存后权限将在重新认证后生效。
      </p>
      <el-checkbox-group
        v-if="roles.length"
        v-model="selectedRoleIds"
        class="role-options"
      >
        <el-checkbox
          v-for="role in roles"
          :key="role.id"
          :value="role.id"
          :disabled="!role.enabled"
        >
          {{ role.name }}（{{ role.code }}）
        </el-checkbox>
      </el-checkbox-group>
      <el-empty
        v-else
        description="无可分配角色或缺少角色查看权限"
        :image-size="64"
      />
      <template #footer>
        <el-button @click="roleDialogOpen = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="saveRoles"
        >
          保存分配
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="passwordDialogOpen"
      title="重置密码"
      width="420px"
    >
      <p class="dialog-hint">
        为 {{ passwordUser?.displayName }} 设置新密码。
      </p>
      <el-input
        v-model="newPassword"
        type="password"
        show-password
        minlength="12"
        maxlength="72"
        autocomplete="new-password"
        placeholder="至少 12 个字符"
      />
      <template #footer>
        <el-button @click="passwordDialogOpen = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="savePassword"
        >
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.user-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-cell strong {
  font-size: 13px;
  font-weight: 620;
}

.user-cell span {
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.role-tag {
  margin: 2px 4px 2px 0;
}

.dialog-hint {
  margin: 0 0 18px;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.role-options {
  display: grid;
  max-height: 320px;
  gap: 10px;
  overflow: auto;
}
</style>
