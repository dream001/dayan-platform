<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ArrowDown, ArrowUp, Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { createMenu, deleteMenu, getMenus, reorderMenus, updateMenu } from '@/services/admin'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import type { MenuNode, MenuPayload } from '@/types/admin'

const { t } = useI18n()

const loading = ref(false)
const error = ref('')
const menus = ref<MenuNode[]>([])
const drawerOpen = ref(false)
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const sorting = ref(false)
const form = reactive<MenuPayload>({
  parentId: null,
  type: 'MENU',
  name: '',
  code: '',
  path: '',
  component: '',
  icon: '',
  sortOrder: 0,
  visible: true,
  enabled: true,
})
const treeProps = { label: 'name', children: 'children', value: 'id' }
const menuTree = computed(() => {
  const onlyMenus = (items: MenuNode[]): MenuNode[] => items
    .filter((item) => item.type === 'MENU' && item.id !== editingId.value)
    .map((item) => ({ ...item, children: onlyMenus(item.children) }))
  return onlyMenus(menus.value)
})

watch(() => form.type, (type) => {
  if (type === 'BUTTON') {
    form.visible = false
    form.path = ''
    form.component = ''
    form.icon = ''
  }
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    menus.value = await getMenus()
  } catch (reason) {
    error.value = getErrorMessage(reason, t('menus.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(parent: MenuNode | null = null, type: 'MENU' | 'BUTTON' = 'MENU') {
  mode.value = 'create'
  editingId.value = null
  Object.assign(form, {
    parentId: parent?.id ?? null,
    type,
    name: '',
    code: '',
    path: '',
    component: '',
    icon: '',
    sortOrder: 0,
    visible: type === 'MENU',
    enabled: true,
  })
  drawerOpen.value = true
}

function openEdit(node: MenuNode) {
  mode.value = 'edit'
  editingId.value = node.id
  Object.assign(form, {
    parentId: node.parentId,
    type: node.type,
    name: node.name,
    code: node.code ?? '',
    path: node.path ?? '',
    component: node.component ?? '',
    icon: node.icon ?? '',
    sortOrder: node.sortOrder,
    visible: node.visible,
    enabled: node.enabled,
  })
  drawerOpen.value = true
}

async function save() {
  if (!form.name.trim() || form.sortOrder < 0) {
    ElMessage.warning(t('menus.validationName'))
    return
  }
  if (form.type === 'BUTTON' && (!form.parentId || !form.code.trim())) {
    ElMessage.warning(t('menus.buttonRequiresParent'))
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      name: form.name.trim(),
      code: form.code.trim(),
      path: form.path.trim(),
      component: form.component.trim(),
      icon: form.icon.trim(),
    }
    if (mode.value === 'create') {
      await createMenu(payload)
      ElMessage.success(t('menus.created'))
    } else if (editingId.value) {
      await updateMenu(editingId.value, payload)
      ElMessage.success(t('menus.updated'))
    }
    drawerOpen.value = false
    await load()
  } catch (reason) {
    notifyError(reason, t('menus.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function remove(node: MenuNode) {
  const confirmed = await confirmAction(
    t('menus.deleteConfirm', { name: node.name }),
    t('menus.deleteTitle'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    await deleteMenu(node.id)
    ElMessage.success(t('menus.deleted'))
    await load()
  } catch (reason) {
    notifyError(reason, t('menus.deleteFailed'))
  }
}

function findSiblings(items: MenuNode[], id: number): MenuNode[] | undefined {
  if (items.some((item) => item.id === id)) return items
  for (const item of items) {
    const siblings = findSiblings(item.children, id)
    if (siblings) return siblings
  }
  return undefined
}

function canMove(node: MenuNode, offset: -1 | 1) {
  const siblings = findSiblings(menus.value, node.id)
  if (!siblings) return false
  const index = siblings.findIndex((item) => item.id === node.id)
  return index + offset >= 0 && index + offset < siblings.length
}

async function moveNode(node: MenuNode, offset: -1 | 1) {
  const siblings = findSiblings(menus.value, node.id)
  if (!siblings) return
  const index = siblings.findIndex((item) => item.id === node.id)
  const targetIndex = index + offset
  if (targetIndex < 0 || targetIndex >= siblings.length) return

  const ids = siblings.map((item) => item.id)
  const currentId = ids[index]!
  ids[index] = ids[targetIndex]!
  ids[targetIndex] = currentId
  sorting.value = true
  try {
    menus.value = await reorderMenus({ parentId: node.parentId, ids })
  } catch (reason) {
    notifyError(reason, t('menus.reorderFailed'))
  } finally {
    sorting.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <PageHeader
      :title="t('menus.title')"
      :eyebrow="t('menus.eyebrow')"
      :description="t('menus.description')"
    >
      <template #actions>
        <el-tooltip :content="t('menus.refreshTree')">
          <el-button
            :icon="Refresh"
            circle
            :aria-label="t('menus.refreshTree')"
            :loading="loading"
            @click="load"
          />
        </el-tooltip>
        <el-button
          v-permission="'system:permission:create'"
          type="primary"
          :icon="Plus"
          @click="openCreate()"
        >
          {{ t('menus.createNode') }}
        </el-button>
      </template>
    </PageHeader>

    <StatePanel
      v-if="loading && !menus.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error"
      state="error"
      :title="t('menus.loadFailed')"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!menus.length"
      state="empty"
      :title="t('menus.empty')"
      :description="t('menus.emptyDesc')"
    />
    <div
      v-else
      class="tree-table"
    >
      <el-table
        :data="menus"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column
          prop="name"
          :label="t('menus.name')"
          min-width="230"
          fixed="left"
        >
          <template #default="{ row }">
            <span>{{ row.name }}</span>
            <el-tag
              class="node-type"
              size="small"
              :type="row.type === 'BUTTON' ? 'info' : undefined"
              effect="plain"
            >
              {{ row.type === 'BUTTON' ? t('menus.typeButton') : t('menus.typeMenu') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="code"
          :label="t('menus.code')"
          min-width="210"
        >
          <template #default="{ row }">
            <span class="mono">{{ row.code || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="path"
          :label="t('menus.path')"
          min-width="160"
        >
          <template #default="{ row }">
            <span class="mono">{{ row.path || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="component"
          :label="t('menus.component')"
          min-width="170"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.component || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="sortOrder"
          :label="t('menus.sort')"
          width="76"
          align="right"
        />
        <el-table-column
          :label="t('common.status')"
          width="112"
        >
          <template #default="{ row }">
            <span :class="row.enabled ? 'status-on' : 'muted'">
              {{ row.enabled ? t('menus.statusEnabled') : t('menus.statusDisabled') }}
            </span>
            <span
              v-if="row.type === 'MENU' && !row.visible"
              class="muted"
            > · {{ t('menus.hidden') }}</span>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.operation')"
          width="220"
          fixed="right"
        >
          <template #default="{ row }">
            <div class="table-actions">
              <el-tooltip :content="t('menus.moveUp')">
                <el-button
                  v-permission="'system:permission:sort'"
                  :icon="ArrowUp"
                  circle
                  text
                  :aria-label="t('menus.moveUpNode')"
                  :disabled="sorting || !canMove(row, -1)"
                  @click="moveNode(row, -1)"
                />
              </el-tooltip>
              <el-tooltip :content="t('menus.moveDown')">
                <el-button
                  v-permission="'system:permission:sort'"
                  :icon="ArrowDown"
                  circle
                  text
                  :aria-label="t('menus.moveDownNode')"
                  :disabled="sorting || !canMove(row, 1)"
                  @click="moveNode(row, 1)"
                />
              </el-tooltip>
              <el-tooltip :content="t('menus.addChild')">
                <el-button
                  v-permission="'system:permission:create'"
                  :icon="Plus"
                  circle
                  text
                  :aria-label="t('menus.addChild')"
                  :disabled="row.type === 'BUTTON'"
                  @click="openCreate(row)"
                />
              </el-tooltip>
              <el-tooltip :content="t('menus.editNode')">
                <el-button
                  v-permission="'system:permission:update'"
                  :icon="Edit"
                  circle
                  text
                  :aria-label="t('menus.editNode')"
                  @click="openEdit(row)"
                />
              </el-tooltip>
              <el-tooltip :content="t('menus.deleteNode')">
                <el-button
                  v-permission="'system:permission:delete'"
                  :icon="Delete"
                  circle
                  text
                  type="danger"
                  :aria-label="t('menus.deleteNode')"
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
      :title="mode === 'create' ? t('menus.drawerCreate') : t('menus.drawerEdit')"
      size="440px"
    >
      <el-form
        class="drawer-form"
        label-position="top"
        @submit.prevent="save"
      >
        <el-form-item
          :label="t('menus.nodeType')"
          required
        >
          <el-radio-group v-model="form.type">
            <el-radio-button value="MENU">
              {{ t('menus.typeMenu') }}
            </el-radio-button>
            <el-radio-button value="BUTTON">
              {{ t('menus.typeButton') }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          :label="t('menus.parentMenu')"
          :required="form.type === 'BUTTON'"
        >
          <el-tree-select
            v-model="form.parentId"
            :data="menuTree"
            :props="treeProps"
            check-strictly
            clearable
            :placeholder="t('menus.noParent')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          :label="t('menus.name')"
          required
        >
          <el-input
            v-model="form.name"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item
          :label="t('menus.code')"
          :required="form.type === 'BUTTON'"
        >
          <el-input
            v-model="form.code"
            maxlength="100"
            :placeholder="t('menus.codePlaceholder')"
          />
        </el-form-item>
        <template v-if="form.type === 'MENU'">
          <el-form-item :label="t('menus.pathField')">
            <el-input
              v-model="form.path"
              maxlength="255"
              placeholder="/system/users"
            />
          </el-form-item>
          <el-form-item :label="t('menus.component')">
            <el-input
              v-model="form.component"
              maxlength="255"
            />
          </el-form-item>
          <el-form-item :label="t('menus.iconField')">
            <el-input
              v-model="form.icon"
              maxlength="100"
            />
          </el-form-item>
        </template>
        <el-form-item :label="t('menus.sort')">
          <el-input-number
            v-model="form.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item v-if="form.type === 'MENU'">
          <el-checkbox v-model="form.visible">
            {{ t('menus.showInNav') }}
          </el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.enabled">
            {{ t('menus.enableNode') }}
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

.node-type {
  margin-left: 8px;
}

.status-on {
  color: #287453;
}
</style>
