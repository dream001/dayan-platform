<script setup lang="ts">
import { Edit, Key, Plus, Refresh, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()
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
    error.value = getErrorMessage(reason, t('users.loadFailed'))
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
    ElMessage.warning(t('users.validationRequired'))
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
      ElMessage.success(t('users.created'))
    } else if (editingId.value) {
      await updateUser(editingId.value, {
        departmentId: form.departmentId,
        displayName: form.displayName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
      })
      ElMessage.success(t('users.updated'))
    }
    drawerOpen.value = false
    await loadUsers()
  } catch (reason) {
    notifyError(reason, t('users.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function toggleStatus(user: UserSummary, enabled: boolean) {
  const action = enabled ? t('users.enable') : t('users.disable')
  const confirmed = await confirmAction(
    t('users.toggleConfirm', { action, name: user.displayName }),
    t('users.toggleTitle', { action }),
    action,
  )
  if (!confirmed) {
    user.enabled = !enabled
    return
  }
  try {
    await changeUserStatus(user.id, enabled)
    ElMessage.success(t('users.toggled', { action }))
  } catch (reason) {
    user.enabled = !enabled
    notifyError(reason, t('users.toggleFailed', { action }))
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
    ElMessage.success(t('users.rolesSaved'))
    roleDialogOpen.value = false
    await loadUsers()
  } catch (reason) {
    notifyError(reason, t('users.roleAssignFailed'))
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
    ElMessage.warning(t('users.passwordTooShort'))
    return
  }
  saving.value = true
  try {
    await resetUserPassword(passwordUser.value.id, newPassword.value)
    ElMessage.success(t('users.passwordReset'))
    passwordDialogOpen.value = false
  } catch (reason) {
    notifyError(reason, t('users.passwordResetFailed'))
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
      :title="t('users.title')"
      :eyebrow="t('users.eyebrow')"
      :description="t('users.description')"
    >
      <template #actions>
        <el-button
          v-permission="'system:user:create'"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          {{ t('users.createUser') }}
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
        :placeholder="t('users.keywordPlaceholder')"
      />
      <el-tree-select
        v-if="canLoadDepartments"
        v-model="query.departmentId"
        :data="departments"
        :props="departmentProps"
        check-strictly
        clearable
        :placeholder="t('users.allDepartments')"
      />
      <el-select
        v-model="query.enabled"
        clearable
        :placeholder="t('users.allStatus')"
      >
        <el-option
          :label="t('users.enabled')"
          :value="true"
        />
        <el-option
          :label="t('users.disabled')"
          :value="false"
        />
      </el-select>
      <el-button
        type="primary"
        native-type="submit"
      >
        {{ t('users.search') }}
      </el-button>
      <el-button @click="resetFilters">
        {{ t('users.reset') }}
      </el-button>
      <el-tooltip :content="t('users.refreshList')">
        <el-button
          :icon="Refresh"
          circle
          :aria-label="t('users.refreshList')"
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
      :title="t('users.loadFailed')"
      :description="error"
      @retry="loadUsers"
    />
    <StatePanel
      v-else-if="!users.length"
      state="empty"
      :title="t('users.notFound')"
      :description="t('users.notFoundDesc')"
    />
    <template v-else>
      <div class="table-shell">
        <el-table
          v-loading="loading"
          :data="users"
          row-key="id"
        >
          <el-table-column
            :label="t('users.user')"
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
            :label="t('users.department')"
            min-width="130"
          >
            <template #default="{ row }">
              {{ row.departmentName || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('users.roles')"
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
            :label="t('users.email')"
            min-width="190"
          >
            <template #default="{ row }">
              {{ row.email || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('users.lastLogin')"
            min-width="165"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.lastLoginAt) }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('users.status')"
            width="88"
          >
            <template #default="{ row }">
              <el-switch
                v-model="row.enabled"
                v-permission="'system:user:change-status'"
                :aria-label="t('users.toggleStatus')"
                @change="(value: boolean) => toggleStatus(row, value)"
              />
              <el-tag
                v-if="!auth.hasPermission('system:user:change-status')"
                size="small"
                :type="row.enabled ? 'success' : 'info'"
                effect="plain"
              >
                {{ row.enabled ? t('users.enable') : t('users.disable') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('users.operation')"
            width="142"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="table-actions">
                <el-tooltip :content="t('users.editUser')">
                  <el-button
                    v-permission="'system:user:update'"
                    :icon="Edit"
                    circle
                    text
                    :aria-label="t('users.editUser')"
                    @click="openEdit(row)"
                  />
                </el-tooltip>
                <el-tooltip :content="t('users.assignRole')">
                  <el-button
                    v-permission="'system:user:assign-role'"
                    :icon="UserFilled"
                    circle
                    text
                    :aria-label="t('users.assignRole')"
                    @click="openRoleDialog(row)"
                  />
                </el-tooltip>
                <el-tooltip :content="t('users.resetPassword')">
                  <el-button
                    v-permission="'system:user:reset-password'"
                    :icon="Key"
                    circle
                    text
                    :aria-label="t('users.resetPassword')"
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
      :title="drawerMode === 'create' ? t('users.drawerCreate') : t('users.drawerEdit')"
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
          :label="t('users.username')"
          required
        >
          <el-input
            v-model="form.username"
            maxlength="64"
            :placeholder="t('users.usernamePattern')"
          />
        </el-form-item>
        <el-form-item
          v-if="drawerMode === 'create'"
          :label="t('users.initialPassword')"
          required
        >
          <el-input
            v-model="form.password"
            type="password"
            show-password
            minlength="12"
            maxlength="72"
            autocomplete="new-password"
            :placeholder="t('users.minPassword')"
          />
        </el-form-item>
        <el-form-item
          :label="t('users.displayName')"
          required
        >
          <el-input
            v-model="form.displayName"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item
          v-if="canLoadDepartments"
          :label="t('users.departmentField')"
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
        <el-form-item :label="t('users.email')">
          <el-input
            v-model="form.email"
            type="email"
            maxlength="254"
          />
        </el-form-item>
        <el-form-item :label="t('users.phone')">
          <el-input
            v-model="form.phone"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item
          v-if="drawerMode === 'create' && canLoadRoles"
          :label="t('users.initialRoles')"
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
            {{ t('users.enableOnCreate') }}
          </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerOpen = false">
            {{ t('users.cancel') }}
          </el-button>
          <el-button
            type="primary"
            :loading="saving"
            @click="saveUser"
          >
            {{ t('users.save') }}
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="roleDialogOpen"
      :title="t('users.assignTitle')"
      width="440px"
    >
      <p class="dialog-hint">
        {{ t('users.assignHint', { name: roleUser?.displayName }) }}
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
        :description="t('users.noRoles')"
        :image-size="64"
      />
      <template #footer>
        <el-button @click="roleDialogOpen = false">
          {{ t('users.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="saveRoles"
        >
          {{ t('users.saveAssignment') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="passwordDialogOpen"
      :title="t('users.passwordTitle')"
      width="420px"
    >
      <p class="dialog-hint">
        {{ t('users.passwordHint', { name: passwordUser?.displayName }) }}
      </p>
      <el-input
        v-model="newPassword"
        type="password"
        show-password
        minlength="12"
        maxlength="72"
        autocomplete="new-password"
        :placeholder="t('users.minPassword')"
      />
      <template #footer>
        <el-button @click="passwordDialogOpen = false">
          {{ t('users.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="savePassword"
        >
          {{ t('users.confirmReset') }}
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
