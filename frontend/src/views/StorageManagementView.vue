<script setup lang="ts">
import {
  Connection,
  Delete,
  Edit,
  Files,
  Plus,
  Refresh,
  Star,
} from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  createStorage,
  deleteStorage,
  getStorageOverview,
  getStorages,
  setDefaultStorage,
  testStorage,
  updateStorage,
} from '@/services/storages'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'
import type {
  CloudStorage,
  CloudStorageOverview,
  CloudStoragePayload,
  StorageProvider,
  StorageStatus,
} from '@/types/storage'
import { formatBytes, formatDateTime } from '@/utils/format'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const providers: StorageProvider[] = [
  'TENCENT_COS',
  'ALIYUN_OSS',
  'HUAWEI_OBS',
  'AWS_S3',
  'AZURE_BLOB',
  'CLOUDFLARE_R2',
  'MINIO',
]

const loading = ref(false)
const error = ref('')
const storages = ref<CloudStorage[]>([])
const overview = ref<CloudStorageOverview>({
  total: 0,
  enabled: 0,
  available: 0,
  usageBytes: 0,
  objectCount: 0,
  providerCounts: {},
})
const filters = reactive({
  keyword: '',
  provider: '' as StorageProvider | '',
  status: '' as StorageStatus | '',
})
const editorOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const testingIds = ref<number[]>([])
const formRef = ref<FormInstance>()
const form = reactive<CloudStoragePayload>(emptyForm())

const rules: FormRules<CloudStoragePayload> = {
  storageKey: [
    { required: true, message: t('storages.validation.storageKey'), trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9-]*$/,
      message: t('storages.validation.storageKeyFormat'),
      trigger: 'blur',
    },
  ],
  name: [{ required: true, message: t('storages.validation.name'), trigger: 'blur' }],
  provider: [{ required: true, message: t('storages.validation.provider'), trigger: 'change' }],
  endpoint: [{ required: true, message: t('storages.validation.endpoint'), trigger: 'blur' }],
  bucket: [{ required: true, message: t('storages.validation.bucket'), trigger: 'blur' }],
}

const filteredStorages = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return storages.value.filter((storage) => {
    const keywordMatches = !keyword || [
      storage.name,
      storage.storageKey,
      storage.bucket,
      storage.endpoint,
    ].some((value) => value.toLowerCase().includes(keyword))
    return keywordMatches
      && (!filters.provider || storage.provider === filters.provider)
      && (!filters.status || storage.status === filters.status)
  })
})

const configuredProviderCount = computed(() => Object.keys(overview.value.providerCounts).length)

function emptyForm(): CloudStoragePayload {
  return {
    storageKey: '',
    name: '',
    provider: 'MINIO',
    endpoint: '',
    region: '',
    bucket: '',
    accessKey: '',
    secretKey: '',
    enabled: true,
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [items, summary] = await Promise.all([getStorages(), getStorageOverview()])
    storages.value = items
    overview.value = summary
  } catch (reason) {
    error.value = getErrorMessage(reason, t('storages.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, emptyForm())
  editorOpen.value = true
}

function openEdit(storage: CloudStorage) {
  editingId.value = storage.id
  Object.assign(form, {
    storageKey: storage.storageKey,
    name: storage.name,
    provider: storage.provider,
    endpoint: storage.endpoint,
    region: storage.region ?? '',
    bucket: storage.bucket,
    accessKey: '',
    secretKey: '',
    enabled: storage.enabled,
  })
  editorOpen.value = true
}

function applyProviderDefaults(provider: StorageProvider) {
  if (form.endpoint) return
  const endpoints: Partial<Record<StorageProvider, string>> = {
    AWS_S3: 'https://s3.amazonaws.com',
    AZURE_BLOB: 'https://account.blob.core.windows.net',
    MINIO: 'http://localhost:9000',
  }
  form.endpoint = endpoints[provider] ?? ''
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (editingId.value === null && (!form.accessKey.trim() || !form.secretKey)) {
    ElMessage.warning(t('storages.validation.credentials'))
    return
  }
  saving.value = true
  try {
    if (editingId.value === null) {
      await createStorage(form)
      ElMessage.success(t('storages.created'))
    } else {
      await updateStorage(editingId.value, form)
      ElMessage.success(t('storages.updated'))
    }
    editorOpen.value = false
    await load()
  } catch (reason) {
    notifyError(reason, t('storages.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function testConnection(storage: CloudStorage) {
  testingIds.value = [...testingIds.value, storage.id]
  try {
    const result = await testStorage(storage.id)
    ElMessage({
      type: result.status === 'AVAILABLE' ? 'success' : 'error',
      message: result.status === 'AVAILABLE'
        ? t('storages.testSucceeded')
        : t('storages.testFailedMessage', { message: result.lastCheckMessage ?? '' }),
    })
    await load()
  } catch (reason) {
    notifyError(reason, t('storages.testFailed'))
  } finally {
    testingIds.value = testingIds.value.filter((id) => id !== storage.id)
  }
}

async function makeDefault(storage: CloudStorage) {
  try {
    await setDefaultStorage(storage.id)
    ElMessage.success(t('storages.defaultUpdated'))
    await load()
  } catch (reason) {
    notifyError(reason, t('storages.defaultFailed'))
  }
}

async function remove(storage: CloudStorage) {
  const confirmed = await confirmAction(
    t('storages.deleteConfirm', { name: storage.name }),
    t('storages.deleteStorage'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    await deleteStorage(storage.id)
    ElMessage.success(t('storages.deleted'))
    await load()
  } catch (reason) {
    notifyError(reason, t('storages.deleteFailed'))
  }
}

function statusType(status: StorageStatus) {
  if (status === 'AVAILABLE') return 'success'
  if (status === 'UNAVAILABLE') return 'danger'
  return 'info'
}

function resetFilters() {
  filters.keyword = ''
  filters.provider = ''
  filters.status = ''
}

onMounted(load)
</script>

<template>
  <section class="admin-page storage-page">
    <PageHeader
      :title="t('storages.title')"
      eyebrow="Cloud Storage"
      :description="t('storages.description')"
    >
      <template #actions>
        <el-button
          v-if="auth.hasPermission('file:view')"
          :icon="Files"
          @click="router.push('/files')"
        >
          {{ t('storages.fileManagement') }}
        </el-button>
        <el-button
          v-permission="'basic:storage:create'"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          {{ t('storages.add') }}
        </el-button>
      </template>
    </PageHeader>

    <div class="storage-summary">
      <div>
        <span>{{ t('storages.totalConnections') }}</span>
        <strong>{{ overview.total }}</strong>
        <small>{{ t('storages.providerCount', { count: configuredProviderCount }) }}</small>
      </div>
      <div>
        <span>{{ t('storages.availableConnections') }}</span>
        <strong>{{ overview.available }} / {{ overview.enabled }}</strong>
        <small>{{ t('storages.enabledConnections') }}</small>
      </div>
      <div>
        <span>{{ t('storages.totalUsage') }}</span>
        <strong>{{ formatBytes(overview.usageBytes) }}</strong>
        <small>{{ t('storages.objectCount', { count: overview.objectCount }) }}</small>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="filters.keyword"
        clearable
        :placeholder="t('storages.keywordPlaceholder')"
      />
      <el-select
        v-model="filters.provider"
        clearable
        :placeholder="t('storages.provider')"
      >
        <el-option
          v-for="provider in providers"
          :key="provider"
          :label="t(`storages.providers.${provider}`)"
          :value="provider"
        />
      </el-select>
      <el-select
        v-model="filters.status"
        clearable
        :placeholder="t('common.status')"
      >
        <el-option
          v-for="status in ['NEVER', 'AVAILABLE', 'UNAVAILABLE']"
          :key="status"
          :label="t(`storages.statuses.${status}`)"
          :value="status"
        />
      </el-select>
      <el-button @click="resetFilters">
        {{ t('common.reset') }}
      </el-button>
      <el-tooltip :content="t('common.refresh')">
        <el-button
          circle
          :icon="Refresh"
          :aria-label="t('common.refresh')"
          @click="load"
        />
      </el-tooltip>
    </div>

    <StatePanel
      v-if="loading && !storages.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !storages.length"
      state="error"
      :title="t('storages.loadFailed')"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!filteredStorages.length"
      state="empty"
      :title="t('storages.empty')"
      :description="t('storages.emptyDescription')"
    />
    <div
      v-else
      class="table-shell"
    >
      <el-table
        v-loading="loading"
        :data="filteredStorages"
        row-key="id"
      >
        <el-table-column
          :label="t('storages.connection')"
          min-width="230"
          fixed="left"
        >
          <template #default="{ row }">
            <div class="storage-identity">
              <span class="provider-mark">{{ t(`storages.providerMarks.${row.provider}`) }}</span>
              <span>
                <strong>{{ row.name }}</strong>
                <small class="mono">{{ row.storageKey }}</small>
              </span>
              <el-tag
                v-if="row.defaultStorage"
                size="small"
                type="warning"
                effect="plain"
              >
                {{ t('storages.default') }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('storages.provider')"
          width="150"
        >
          <template #default="{ row }">
            {{ t(`storages.providers.${row.provider}`) }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('storages.bucket')"
          min-width="190"
        >
          <template #default="{ row }">
            <strong class="bucket-name">{{ row.bucket }}</strong>
            <small class="endpoint">{{ row.endpoint }}</small>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('storages.usage')"
          width="130"
          align="right"
        >
          <template #default="{ row }">
            <strong>{{ formatBytes(row.usageBytes) }}</strong>
            <small>{{ t('storages.objectCount', { count: row.objectCount }) }}</small>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.status')"
          width="150"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="statusType(row.status)"
              effect="plain"
            >
              {{ t(`storages.statuses.${row.status}`) }}
            </el-tag>
            <small class="last-check">
              {{ row.lastCheckedAt ? formatDateTime(row.lastCheckedAt) : t('storages.notChecked') }}
            </small>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('storages.latency')"
          width="95"
          align="right"
        >
          <template #default="{ row }">
            {{ row.lastCheckLatencyMs == null ? '—' : `${row.lastCheckLatencyMs} ms` }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.operation')"
          width="174"
          fixed="right"
        >
          <template #default="{ row }">
            <div class="table-actions">
              <el-tooltip :content="t('storages.testConnection')">
                <el-button
                  v-permission="'basic:storage:test'"
                  circle
                  text
                  :icon="Connection"
                  :loading="testingIds.includes(row.id)"
                  :aria-label="t('storages.testConnection')"
                  @click="testConnection(row)"
                />
              </el-tooltip>
              <el-tooltip :content="t('storages.setDefault')">
                <el-button
                  v-if="!row.defaultStorage"
                  v-permission="'basic:storage:default'"
                  circle
                  text
                  :icon="Star"
                  :disabled="!row.enabled"
                  :aria-label="t('storages.setDefault')"
                  @click="makeDefault(row)"
                />
              </el-tooltip>
              <el-tooltip :content="t('common.edit')">
                <el-button
                  v-permission="'basic:storage:update'"
                  circle
                  text
                  :icon="Edit"
                  :aria-label="t('common.edit')"
                  @click="openEdit(row)"
                />
              </el-tooltip>
              <el-tooltip :content="t('common.delete')">
                <el-button
                  v-if="!row.defaultStorage"
                  v-permission="'basic:storage:delete'"
                  circle
                  text
                  type="danger"
                  :icon="Delete"
                  :aria-label="t('common.delete')"
                  @click="remove(row)"
                />
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer
      v-model="editorOpen"
      :title="editingId === null ? t('storages.createTitle') : t('storages.editTitle')"
      size="min(520px, 94vw)"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        class="drawer-form"
        :model="form"
        :rules="rules"
        label-position="top"
      >
        <div class="form-grid">
          <el-form-item
            :label="t('storages.name')"
            prop="name"
          >
            <el-input
              v-model="form.name"
              maxlength="120"
            />
          </el-form-item>
          <el-form-item
            :label="t('storages.storageKey')"
            prop="storageKey"
          >
            <el-input
              v-model="form.storageKey"
              :disabled="editingId !== null"
              maxlength="64"
            />
          </el-form-item>
        </div>
        <el-form-item
          :label="t('storages.provider')"
          prop="provider"
        >
          <el-select
            v-model="form.provider"
            @change="applyProviderDefaults"
          >
            <el-option
              v-for="provider in providers"
              :key="provider"
              :label="t(`storages.providers.${provider}`)"
              :value="provider"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          :label="t('storages.endpoint')"
          prop="endpoint"
        >
          <el-input
            v-model="form.endpoint"
            maxlength="500"
            placeholder="https://"
          />
        </el-form-item>
        <div class="form-grid">
          <el-form-item
            :label="t('storages.region')"
            prop="region"
          >
            <el-input
              v-model="form.region"
              maxlength="100"
              placeholder="ap-southeast-1"
            />
          </el-form-item>
          <el-form-item
            :label="form.provider === 'AZURE_BLOB' ? t('storages.container') : t('storages.bucket')"
            prop="bucket"
          >
            <el-input
              v-model="form.bucket"
              maxlength="255"
            />
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item :label="form.provider === 'AZURE_BLOB' ? t('storages.accountName') : 'Access Key'">
            <el-input
              v-model="form.accessKey"
              maxlength="500"
              :placeholder="editingId === null ? '' : t('storages.keepCredential')"
            />
          </el-form-item>
          <el-form-item :label="form.provider === 'AZURE_BLOB' ? t('storages.accountKey') : 'Secret Key'">
            <el-input
              v-model="form.secretKey"
              type="password"
              show-password
              maxlength="1000"
              :placeholder="editingId === null ? '' : t('storages.keepCredential')"
            />
          </el-form-item>
        </div>
        <el-form-item :label="t('storages.enabled')">
          <el-switch v-model="form.enabled" />
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
.storage-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-bottom: 1px solid var(--color-border-strong);
}

.storage-summary > div {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 20px 24px 18px 0;
}

.storage-summary > div + div {
  padding-left: 24px;
  border-left: 1px solid var(--color-border);
}

.storage-summary span,
.storage-summary small {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.storage-summary strong {
  font-size: 24px;
  font-weight: 650;
}

.storage-identity {
  display: flex;
  align-items: center;
  gap: 10px;
}

.storage-identity > span:nth-child(2) {
  display: grid;
  min-width: 0;
}

.storage-identity strong,
.bucket-name {
  font-size: 13px;
  font-weight: 650;
}

.storage-identity small,
.endpoint,
.last-check,
.table-shell td small {
  display: block;
  margin-top: 3px;
  color: var(--color-text-muted);
  font-size: 11px;
}

.provider-mark {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border: 1px solid var(--color-border-strong);
  border-radius: 6px;
  color: var(--color-accent);
  background: #f4f8f9;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
}

.endpoint {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.drawer-form :deep(.el-select) {
  width: 100%;
}

@media (max-width: 720px) {
  .storage-summary {
    grid-template-columns: 1fr;
  }

  .storage-summary > div {
    padding: 14px 0;
  }

  .storage-summary > div + div {
    padding-left: 0;
    border-top: 1px solid var(--color-border);
    border-left: 0;
  }

  .form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
