<script setup lang="ts">
import {
  Connection,
  Delete,
  Edit,
  Key,
  Plus,
  Refresh,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  changeAiModelStatus,
  createAiModel,
  deleteAiModel,
  getAiModels,
  testAiModel,
  updateAiModel,
} from '@/services/ai-models'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'
import type { AiModel, AiModelPayload, ModelType } from '@/types/ai-model'
import { formatDateTime } from '@/utils/format'

interface ProviderPreset {
  manufacturer: string
  accessAddress: string
  modelUrl: string
}

const providerPresets: ProviderPreset[] = [
  {
    manufacturer: '豆包',
    accessAddress: 'https://ark.cn-beijing.volces.com',
    modelUrl: 'https://ark.cn-beijing.volces.com/api/v3/chat/completions',
  },
  {
    manufacturer: '阿里云百炼（千问）',
    accessAddress: 'https://dashscope.aliyuncs.com',
    modelUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions',
  },
  {
    manufacturer: 'DeepSeek',
    accessAddress: 'https://api.deepseek.com',
    modelUrl: 'https://api.deepseek.com/chat/completions',
  },
  {
    manufacturer: 'OpenAI',
    accessAddress: 'https://api.openai.com',
    modelUrl: 'https://api.openai.com/v1/chat/completions',
  },
]

const modelTypes: ModelType[] = [
  'CHAT',
  'EMBEDDING',
  'MULTIMODAL',
  'RERANK',
  'IMAGE',
  'AUDIO',
]

const { t } = useI18n()
const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const models = ref<AiModel[]>([])
const total = ref(0)
const testingId = ref<number | null>(null)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  modelType: undefined as ModelType | undefined,
  enabled: undefined as boolean | undefined,
})

const editorOpen = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive<AiModelPayload>({
  manufacturer: '',
  name: '',
  accessAddress: '',
  modelUrl: '',
  modelType: 'CHAT',
  accessKey: '',
  secretKey: '',
  enabled: true,
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await getAiModels({
      page: query.page,
      size: query.size,
      keyword: query.keyword.trim() || undefined,
      modelType: query.modelType,
      enabled: query.enabled,
    })
    models.value = page.items
    total.value = page.total
  } catch (reason) {
    error.value = getErrorMessage(reason, t('aiModels.loadFailed'))
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
  query.modelType = undefined
  query.enabled = undefined
  search()
}

function resetForm() {
  Object.assign(form, {
    manufacturer: providerPresets[0].manufacturer,
    name: '',
    accessAddress: providerPresets[0].accessAddress,
    modelUrl: providerPresets[0].modelUrl,
    modelType: 'CHAT',
    accessKey: '',
    secretKey: '',
    enabled: true,
  })
  editingId.value = null
}

function openCreate() {
  resetForm()
  editorMode.value = 'create'
  editorOpen.value = true
}

function openEdit(model: AiModel) {
  editorMode.value = 'edit'
  editingId.value = model.id
  Object.assign(form, {
    manufacturer: model.manufacturer,
    name: model.name,
    accessAddress: model.accessAddress,
    modelUrl: model.modelUrl,
    modelType: model.modelType,
    accessKey: '',
    secretKey: '',
    enabled: model.enabled,
  })
  editorOpen.value = true
}

function selectProvider(manufacturer: string) {
  const preset = providerPresets.find((item) => item.manufacturer === manufacturer)
  if (!preset) return
  form.accessAddress = preset.accessAddress
  form.modelUrl = preset.modelUrl
}

async function save() {
  if (
    !form.manufacturer.trim()
    || !form.name.trim()
    || !form.accessAddress.trim()
    || !form.modelUrl.trim()
  ) {
    ElMessage.warning(t('aiModels.validation'))
    return
  }
  if (editorMode.value === 'create' && !form.accessKey.trim() && !form.secretKey.trim()) {
    ElMessage.warning(t('aiModels.credentialRequired'))
    return
  }

  saving.value = true
  const payload = {
    ...form,
    manufacturer: form.manufacturer.trim(),
    name: form.name.trim(),
    accessAddress: form.accessAddress.trim(),
    modelUrl: form.modelUrl.trim(),
    accessKey: form.accessKey.trim(),
    secretKey: form.secretKey.trim(),
  }
  try {
    if (editorMode.value === 'create') {
      await createAiModel(payload)
      ElMessage.success(t('aiModels.created'))
    } else if (editingId.value) {
      await updateAiModel(editingId.value, payload)
      ElMessage.success(t('aiModels.updated'))
    }
    editorOpen.value = false
    await load()
  } catch (reason) {
    notifyError(reason, t('aiModels.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function toggleStatus(model: AiModel, enabled: boolean) {
  try {
    await changeAiModelStatus(model.id, enabled)
    ElMessage.success(t(enabled ? 'aiModels.enabled' : 'aiModels.disabled'))
  } catch (reason) {
    model.enabled = !enabled
    notifyError(reason, t('aiModels.statusFailed'))
  }
}

async function testConnection(model: AiModel) {
  testingId.value = model.id
  try {
    const result = await testAiModel(model.id)
    if (result.success) {
      ElMessage.success(t('aiModels.testSucceeded', { latency: result.latencyMs }))
    } else {
      ElMessage.warning(result.message)
    }
    await load()
  } catch (reason) {
    notifyError(reason, t('aiModels.testFailed'))
  } finally {
    testingId.value = null
  }
}

async function remove(model: AiModel) {
  const confirmed = await confirmAction(
    t('aiModels.deleteConfirm', { name: model.name }),
    t('aiModels.deleteTitle'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    await deleteAiModel(model.id)
    ElMessage.success(t('aiModels.deleted'))
    if (models.value.length === 1 && query.page > 1) query.page -= 1
    await load()
  } catch (reason) {
    notifyError(reason, t('aiModels.deleteFailed'))
  }
}

function statusType(status: AiModel['lastTestStatus']) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

onMounted(load)
</script>

<template>
  <section class="admin-page model-page">
    <PageHeader
      :title="t('aiModels.title')"
      :eyebrow="t('aiModels.eyebrow')"
      :description="t('aiModels.description')"
    >
      <template #actions>
        <el-tooltip :content="t('aiModels.refresh')">
          <el-button
            :icon="Refresh"
            circle
            :loading="loading"
            :aria-label="t('aiModels.refresh')"
            @click="load"
          />
        </el-tooltip>
        <el-button
          v-permission="'basic:model:create'"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          {{ t('aiModels.create') }}
        </el-button>
      </template>
    </PageHeader>

    <div class="provider-strip">
      <span>{{ t('aiModels.mainstreamProviders') }}</span>
      <strong
        v-for="provider in providerPresets"
        :key="provider.manufacturer"
      >
        {{ provider.manufacturer }}
      </strong>
    </div>

    <form
      class="filter-bar"
      @submit.prevent="search"
    >
      <el-input
        v-model="query.keyword"
        clearable
        :placeholder="t('aiModels.keyword')"
      />
      <el-select
        v-model="query.modelType"
        clearable
        :placeholder="t('aiModels.allTypes')"
      >
        <el-option
          v-for="type in modelTypes"
          :key="type"
          :label="t(`aiModels.types.${type}`)"
          :value="type"
        />
      </el-select>
      <el-select
        v-model="query.enabled"
        clearable
        :placeholder="t('aiModels.allStatuses')"
      >
        <el-option
          :label="t('aiModels.statusEnabled')"
          :value="true"
        />
        <el-option
          :label="t('aiModels.statusDisabled')"
          :value="false"
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
    </form>

    <StatePanel
      v-if="loading && !models.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !models.length"
      state="error"
      :title="t('aiModels.loadFailed')"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!models.length"
      state="empty"
      :title="t('aiModels.empty')"
      :description="t('aiModels.emptyDesc')"
    />
    <template v-else>
      <div class="table-shell">
        <el-table
          v-loading="loading"
          :data="models"
          row-key="id"
        >
          <el-table-column
            :label="t('aiModels.model')"
            min-width="220"
            fixed="left"
          >
            <template #default="{ row }">
              <div class="model-identity">
                <span class="model-identity__signal" />
                <div>
                  <strong>{{ row.name }}</strong>
                  <small>{{ row.manufacturer }}</small>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('aiModels.type')"
            width="128"
          >
            <template #default="{ row }">
              <el-tag
                size="small"
                effect="plain"
              >
                {{ t(`aiModels.types.${row.modelType}`) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('aiModels.endpoint')"
            min-width="280"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span class="endpoint">{{ row.modelUrl }}</span>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('aiModels.credentials')"
            width="130"
          >
            <template #default="{ row }">
              <div class="credential-state">
                <el-icon><Key /></el-icon>
                <span>
                  {{ row.accessKeyConfigured ? 'AK' : '' }}
                  {{ row.accessKeyConfigured && row.secretKeyConfigured ? ' / ' : '' }}
                  {{ row.secretKeyConfigured ? 'SK' : '' }}
                  {{ !row.accessKeyConfigured && !row.secretKeyConfigured
                    ? t('aiModels.notConfigured')
                    : '' }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('aiModels.lastTest')"
            min-width="175"
          >
            <template #default="{ row }">
              <div class="test-state">
                <el-tag
                  :type="statusType(row.lastTestStatus)"
                  effect="plain"
                  size="small"
                >
                  {{ t(`aiModels.testStatuses.${row.lastTestStatus}`) }}
                </el-tag>
                <small v-if="row.lastTestedAt">
                  {{ row.lastTestLatencyMs }} ms · {{ formatDateTime(row.lastTestedAt) }}
                </small>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('common.status')"
            width="92"
          >
            <template #default="{ row }">
              <el-switch
                v-model="row.enabled"
                :disabled="!auth.hasPermission('basic:model:update')"
                @change="(value: string | number | boolean) => toggleStatus(row, Boolean(value))"
              />
            </template>
          </el-table-column>
          <el-table-column
            :label="t('common.operation')"
            width="136"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="table-actions">
                <el-tooltip :content="t('aiModels.test')">
                  <el-button
                    v-permission="'basic:model:test'"
                    :icon="Connection"
                    circle
                    text
                    :loading="testingId === row.id"
                    :disabled="!row.enabled"
                    :aria-label="t('aiModels.test')"
                    @click="testConnection(row)"
                  />
                </el-tooltip>
                <el-tooltip :content="t('common.edit')">
                  <el-button
                    v-permission="'basic:model:update'"
                    :icon="Edit"
                    circle
                    text
                    :aria-label="t('common.edit')"
                    @click="openEdit(row)"
                  />
                </el-tooltip>
                <el-tooltip :content="t('common.delete')">
                  <el-button
                    v-permission="'basic:model:delete'"
                    :icon="Delete"
                    circle
                    text
                    type="danger"
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
      :title="editorMode === 'create' ? t('aiModels.create') : t('aiModels.edit')"
      size="560px"
      destroy-on-close
    >
      <el-form
        class="drawer-form"
        label-position="top"
        @submit.prevent="save"
      >
        <div class="model-form-grid">
          <el-form-item
            :label="t('aiModels.manufacturer')"
            required
          >
            <el-select
              v-model="form.manufacturer"
              filterable
              allow-create
              default-first-option
              @change="selectProvider"
            >
              <el-option
                v-for="provider in providerPresets"
                :key="provider.manufacturer"
                :label="provider.manufacturer"
                :value="provider.manufacturer"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            :label="t('aiModels.type')"
            required
          >
            <el-select v-model="form.modelType">
              <el-option
                v-for="type in modelTypes"
                :key="type"
                :label="t(`aiModels.types.${type}`)"
                :value="type"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            class="model-form-grid__wide"
            :label="t('aiModels.name')"
            required
          >
            <el-input
              v-model="form.name"
              maxlength="160"
              :placeholder="t('aiModels.namePlaceholder')"
            />
          </el-form-item>
          <el-form-item
            class="model-form-grid__wide"
            :label="t('aiModels.accessAddress')"
            required
          >
            <el-input
              v-model="form.accessAddress"
              maxlength="500"
              placeholder="https://api.example.com"
            />
          </el-form-item>
          <el-form-item
            class="model-form-grid__wide"
            :label="t('aiModels.modelUrl')"
            required
          >
            <el-input
              v-model="form.modelUrl"
              maxlength="1000"
              placeholder="https://api.example.com/v1/chat/completions"
            />
          </el-form-item>
          <el-form-item label="AK">
            <el-input
              v-model="form.accessKey"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="editorMode === 'edit' ? t('aiModels.keepCredential') : ''"
            />
          </el-form-item>
          <el-form-item label="SK">
            <el-input
              v-model="form.secretKey"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="editorMode === 'edit' ? t('aiModels.keepCredential') : ''"
            />
          </el-form-item>
          <el-form-item class="model-form-grid__wide">
            <el-checkbox v-model="form.enabled">
              {{ t('aiModels.enableModel') }}
            </el-checkbox>
          </el-form-item>
        </div>
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
.provider-strip {
  display: flex;
  min-height: 52px;
  align-items: center;
  gap: 18px;
  overflow-x: auto;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

.provider-strip span {
  color: var(--color-text-muted);
  font-size: 11px;
}

.provider-strip strong {
  color: var(--color-text-secondary);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
}

.model-identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 11px;
}

.model-identity__signal {
  width: 4px;
  height: 34px;
  flex: 0 0 auto;
  background: linear-gradient(#12748a 0 50%, #d39b35 50% 100%);
}

.model-identity div,
.test-state {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.model-identity strong {
  overflow: hidden;
  color: var(--color-ink);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-identity small,
.test-state small {
  color: var(--color-text-muted);
  font-size: 10px;
}

.endpoint {
  color: var(--color-text-secondary);
  font-family: var(--font-mono);
  font-size: 10px;
}

.credential-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-secondary);
  font-family: var(--font-mono);
  font-size: 11px;
}

.model-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.model-form-grid__wide {
  grid-column: 1 / -1;
}

.model-form-grid :deep(.el-select) {
  width: 100%;
}

@media (max-width: 700px) {
  .provider-strip {
    gap: 14px;
  }

  .model-form-grid {
    grid-template-columns: 1fr;
  }

  .model-form-grid__wide {
    grid-column: auto;
  }
}
</style>
