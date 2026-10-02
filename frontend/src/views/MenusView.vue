<script setup lang="ts">
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { createMenu, deleteMenu, getMenus, updateMenu } from '@/services/admin'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import type { MenuNode, MenuPayload } from '@/types/admin'

const loading = ref(false)
const error = ref('')
const menus = ref<MenuNode[]>([])
const drawerOpen = ref(false)
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
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
    error.value = getErrorMessage(reason, '菜单权限树加载失败')
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
    ElMessage.warning('请填写名称，排序值不能小于 0')
    return
  }
  if (form.type === 'BUTTON' && (!form.parentId || !form.code.trim())) {
    ElMessage.warning('按钮权限必须选择上级菜单并填写权限码')
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
      ElMessage.success('菜单权限已新增')
    } else if (editingId.value) {
      await updateMenu(editingId.value, payload)
      ElMessage.success('菜单权限已更新')
    }
    drawerOpen.value = false
    await load()
  } catch (reason) {
    notifyError(reason, '菜单权限保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(node: MenuNode) {
  const confirmed = await confirmAction(
    `删除“${node.name}”？存在下级节点或角色引用时无法删除。`,
    '删除菜单权限',
    '删除',
  )
  if (!confirmed) return
  try {
    await deleteMenu(node.id)
    ElMessage.success('菜单权限已删除')
    await load()
  } catch (reason) {
    notifyError(reason, '菜单权限删除失败')
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <PageHeader
      title="菜单权限"
      eyebrow="System / Permissions"
      description="维护导航菜单与按钮权限码树。"
    >
      <template #actions>
        <el-tooltip content="刷新权限树">
          <el-button
            :icon="Refresh"
            circle
            aria-label="刷新权限树"
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
          新增节点
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
      title="权限树加载失败"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!menus.length"
      state="empty"
      title="暂无菜单权限"
      description="新增首个菜单节点以建立导航。"
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
          label="名称"
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
              {{ row.type === 'BUTTON' ? '按钮' : '菜单' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="code"
          label="权限码"
          min-width="210"
        >
          <template #default="{ row }">
            <span class="mono">{{ row.code || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="path"
          label="路径"
          min-width="160"
        >
          <template #default="{ row }">
            <span class="mono">{{ row.path || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="component"
          label="组件标识"
          min-width="170"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.component || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="sortOrder"
          label="排序"
          width="76"
          align="right"
        />
        <el-table-column
          label="状态"
          width="112"
        >
          <template #default="{ row }">
            <span :class="row.enabled ? 'status-on' : 'muted'">
              {{ row.enabled ? '启用' : '停用' }}
            </span>
            <span
              v-if="row.type === 'MENU' && !row.visible"
              class="muted"
            > · 隐藏</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="138"
          fixed="right"
        >
          <template #default="{ row }">
            <div class="table-actions">
              <el-tooltip content="新增下级节点">
                <el-button
                  v-permission="'system:permission:create'"
                  :icon="Plus"
                  circle
                  text
                  aria-label="新增下级节点"
                  :disabled="row.type === 'BUTTON'"
                  @click="openCreate(row)"
                />
              </el-tooltip>
              <el-tooltip content="编辑节点">
                <el-button
                  v-permission="'system:permission:update'"
                  :icon="Edit"
                  circle
                  text
                  aria-label="编辑节点"
                  @click="openEdit(row)"
                />
              </el-tooltip>
              <el-tooltip content="删除节点">
                <el-button
                  v-permission="'system:permission:delete'"
                  :icon="Delete"
                  circle
                  text
                  type="danger"
                  aria-label="删除节点"
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
      :title="mode === 'create' ? '新增菜单权限' : '编辑菜单权限'"
      size="440px"
    >
      <el-form
        class="drawer-form"
        label-position="top"
        @submit.prevent="save"
      >
        <el-form-item
          label="节点类型"
          required
        >
          <el-radio-group v-model="form.type">
            <el-radio-button value="MENU">
              菜单
            </el-radio-button>
            <el-radio-button value="BUTTON">
              按钮
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          label="上级菜单"
          :required="form.type === 'BUTTON'"
        >
          <el-tree-select
            v-model="form.parentId"
            :data="menuTree"
            :props="treeProps"
            check-strictly
            clearable
            placeholder="无（顶级菜单）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          label="名称"
          required
        >
          <el-input
            v-model="form.name"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item
          label="权限码"
          :required="form.type === 'BUTTON'"
        >
          <el-input
            v-model="form.code"
            maxlength="100"
            placeholder="例如 system:user:create"
          />
        </el-form-item>
        <template v-if="form.type === 'MENU'">
          <el-form-item label="路由路径">
            <el-input
              v-model="form.path"
              maxlength="255"
              placeholder="/system/users"
            />
          </el-form-item>
          <el-form-item label="组件标识">
            <el-input
              v-model="form.component"
              maxlength="255"
            />
          </el-form-item>
          <el-form-item label="图标标识">
            <el-input
              v-model="form.icon"
              maxlength="100"
            />
          </el-form-item>
        </template>
        <el-form-item label="排序">
          <el-input-number
            v-model="form.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item v-if="form.type === 'MENU'">
          <el-checkbox v-model="form.visible">
            在导航中显示
          </el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.enabled">
            启用节点
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

.node-type {
  margin-left: 8px;
}

.status-on {
  color: #287453;
}
</style>
