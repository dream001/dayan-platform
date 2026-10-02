<script setup lang="ts">
import { Delete, Edit, Key, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElTree } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()
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
    error.value = getErrorMessage(reason, t('roles.loadFailed'))
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
    ElMessage.warning(t('roles.validation'))
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
      ElMessage.success(t('roles.created'))
    } else if (editingId.value) {
      await updateRole(editingId.value, payload)
      ElMessage.success(t('roles.updated'))
    }
    drawerOpen.value = false
    await load()
  } catch (reason) {
    notifyError(reason, t('roles.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function removeRole(role: RoleSummary) {
  const confirmed = await confirmAction(
    t('roles.deleteConfirm', { name: role.name }),
    t('roles.deleteTitle'),
    t('roles.delete'),
  )
  if (!confirmed) return
  try {
    await deleteRole(role.id)
    ElMessage.success(t('roles.deleted'))
    await load()
  } catch (reason) {
    notifyError(reason, t('roles.deleteFailed'))
  }
}

async function openPermissions(role: RoleSummary) {
  permissionRole.value = role
  permissionDialogOpen.value = true
  saving.value = true
  try {
    const [detail, tree] = await Promise.all([
      getRole(role.id),
      menus.value.length ? Promise.resolve(menus.value) : getMenus(),
    ])
    menus.value = tree
    checkedPermissionIds.value = detail.permissionIds
  } catch (reason) {
    permissionDialogOpen.value = false
    notifyError(reason, t('roles.permissionLoadFailed'))
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
    ElMessage.success(t('roles.granted'))
    permissionDialogOpen.value = false
  } catch (reason) {
    notifyError(reason, t('roles.grantFailed'))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <PageHeader
      :title="t('roles.title')"
      :eyebrow="t('roles.eyebrow')"
      :description="t('roles.description')"
    >
      <template #actions>
        <el-button
          v-permission="'system:role:create'"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          {{ t('roles.createRole') }}
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
        :placeholder="t('roles.keywordPlaceholder')"
      />
      <el-select
        v-model="query.enabled"
        clearable
        :placeholder="t('roles.allStatus')"
      >
        <el-option
          :label="t('roles.enabled')"
          :value="true"
        />
        <el-option
          :label="t('roles.disabled')"
          :value="false"
        />
      </el-select>
      <el-button
        type="primary"
        native-type="submit"
      >
        {{ t('roles.search') }}
      </el-button>
      <el-button @click="resetFilters">
        {{ t('roles.reset') }}
      </el-button>
      <el-tooltip :content="t('roles.refreshList')">
        <el-button
          :icon="Refresh"
          circle
          :aria-label="t('roles.refreshList')"
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
      :title="t('roles.loadFailed')"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!roles.length"
      state="empty"
      :title="t('roles.notFound')"
      :description="t('roles.notFoundDesc')"
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
            :label="t('roles.name')"
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
                {{ t('roles.builtIn') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="code"
            :label="t('roles.code')"
            min-width="170"
          >
            <template #default="{ row }">
              <span class="mono">{{ row.code }}</span>
            </template>
          </el-table-column>
          <el-table-column
            prop="description"
            :label="t('roles.descriptionCol')"
            min-width="240"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.description || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="userCount"
            :label="t('roles.userCount')"
            width="90"
            align="right"
          />
          <el-table-column
            :label="t('roles.status')"
            width="88"
          >
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.enabled ? 'success' : 'info'"
                effect="plain"
              >
                {{ row.enabled ? t('roles.enable') : t('roles.disable') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('roles.createdAt')"
            min-width="165"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('roles.operation')"
            width="136"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="table-actions">
                <el-tooltip :content="t('roles.editRole')">
                  <el-button
                    v-permission="'system:role:update'"
                    :icon="Edit"
                    circle
                    text
                    :aria-label="t('roles.editRole')"
                    @click="openEdit(row)"
                  />
                </el-tooltip>
                <el-tooltip :content="row.code === 'SUPER_ADMIN' ? t('roles.superAdminHint') : t('roles.grant')">
                  <span>
                    <el-button
                      v-permission="'system:role:grant'"
                      :icon="Key"
                      circle
                      text
                      :aria-label="t('roles.grantAria')"
                      :disabled="row.code === 'SUPER_ADMIN'"
                      @click="openPermissions(row)"
                    />
                  </span>
                </el-tooltip>
                <el-tooltip :content="row.builtIn ? t('roles.builtInNoDelete') : t('roles.deleteTitle')">
                  <span>
                    <el-button
                      v-permission="'system:role:delete'"
                      :icon="Delete"
                      circle
                      text
                      type="danger"
                      :aria-label="t('roles.deleteTitle')"
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
      :title="mode === 'create' ? t('roles.drawerCreate') : t('roles.drawerEdit')"
      size="420px"
    >
      <el-form
        class="drawer-form"
        label-position="top"
        @submit.prevent="saveRole"
      >
        <el-form-item
          :label="t('roles.name')"
          required
        >
          <el-input
            v-model="form.name"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item
          :label="t('roles.code')"
          required
        >
          <el-input
            v-model="form.code"
            maxlength="64"
            :placeholder="t('roles.codePlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('roles.descriptionCol')">
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
            {{ t('roles.enableRole') }}
          </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerOpen = false">
            {{ t('roles.cancel') }}
          </el-button>
          <el-button
            type="primary"
            :loading="saving"
            @click="saveRole"
          >
            {{ t('roles.save') }}
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="permissionDialogOpen"
      :title="t('roles.grantTitle', { name: permissionRole?.name ?? '' })"
      width="560px"
    >
      <p class="permission-hint">
        {{ t('roles.grantHint') }}
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
            <small>{{ data.type === 'BUTTON' ? t('roles.button') : t('roles.menu') }} · {{ data.code || t('roles.noCode') }}</small>
          </span>
        </template>
      </el-tree>
      <template #footer>
        <el-button @click="permissionDialogOpen = false">
          {{ t('roles.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="savePermissions"
        >
          {{ t('roles.saveGrant') }}
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
