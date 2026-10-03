<script setup lang="ts">
import {
  Connection as ConnectionIcon,
  Delete,
  Edit,
  Plus,
  Refresh,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  changeMqttConnectionStatus,
  changeMqttSubscriptionStatus,
  deleteMqttConnection,
  deleteMqttSubscription,
  getMqttConnections,
  getMqttOverview,
  getMqttSubscriptions,
  saveMqttConnection,
  saveMqttSubscription,
  testMqttConnection,
} from '@/services/mqtt'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'
import type {
  MqttConnection,
  MqttConnectionPayload,
  MqttConnectionStatus,
  MqttOverview,
  MqttSubscription,
  MqttSubscriptionPayload,
} from '@/types/mqtt'
import { formatDateTime } from '@/utils/format'

const { t } = useI18n()
const auth = useAuthStore()
const activeTab = ref<'connections' | 'subscriptions'>('connections')
const loading = ref(false)
const error = ref('')
const connections = ref<MqttConnection[]>([])
const subscriptions = ref<MqttSubscription[]>([])
const overview = ref<MqttOverview>({
  connectionCount: 0,
  enabledConnectionCount: 0,
  availableConnectionCount: 0,
  subscriptionCount: 0,
  enabledSubscriptionCount: 0,
})
const keyword = ref('')
const subscriptionConnectionId = ref<number>()
const testingIds = ref<number[]>([])
const connectionEditorOpen = ref(false)
const subscriptionEditorOpen = ref(false)
const editingConnectionId = ref<number | null>(null)
const editingSubscriptionId = ref<number | null>(null)
const saving = ref(false)
const connectionForm = reactive<MqttConnectionPayload>(emptyConnection())
const subscriptionForm = reactive<MqttSubscriptionPayload>(emptySubscription())

const filteredConnections = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return connections.value
  return connections.value.filter((item) =>
    [item.name, item.clientId, item.brokerUrl].some((text) =>
      text.toLowerCase().includes(value),
    ),
  )
})

const filteredSubscriptions = computed(() =>
  subscriptions.value.filter((item) =>
    subscriptionConnectionId.value == null
      || item.connectionId === subscriptionConnectionId.value,
  ),
)

function emptyConnection(): MqttConnectionPayload {
  return {
    name: '',
    clientId: '',
    brokerUrl: 'tcp://localhost:1883',
    username: '',
    password: '',
    tlsEnabled: false,
    cleanSession: true,
    keepAliveSeconds: 60,
    connectionTimeoutSeconds: 10,
    enabled: true,
  }
}

function emptySubscription(): MqttSubscriptionPayload {
  return {
    connectionId: connections.value[0]?.id ?? null,
    topicFilter: '',
    qos: 1,
    description: '',
    enabled: true,
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [connectionItems, subscriptionItems, summary] = await Promise.all([
      getMqttConnections(),
      getMqttSubscriptions(),
      getMqttOverview(),
    ])
    connections.value = connectionItems
    subscriptions.value = subscriptionItems
    overview.value = summary
  } catch (reason) {
    error.value = getErrorMessage(reason, t('mqtt.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openConnectionEditor(connection?: MqttConnection) {
  editingConnectionId.value = connection?.id ?? null
  Object.assign(connectionForm, connection
    ? {
        name: connection.name,
        clientId: connection.clientId,
        brokerUrl: connection.brokerUrl,
        username: '',
        password: '',
        tlsEnabled: connection.tlsEnabled,
        cleanSession: connection.cleanSession,
        keepAliveSeconds: connection.keepAliveSeconds,
        connectionTimeoutSeconds: connection.connectionTimeoutSeconds,
        enabled: connection.enabled,
      }
    : emptyConnection())
  connectionEditorOpen.value = true
}

function syncTlsWithBroker() {
  const secure = /^(ssl|wss):\/\//i.test(connectionForm.brokerUrl.trim())
  const insecure = /^(tcp|ws):\/\//i.test(connectionForm.brokerUrl.trim())
  if (secure) connectionForm.tlsEnabled = true
  if (insecure) connectionForm.tlsEnabled = false
}

async function saveConnection() {
  if (
    !connectionForm.name.trim()
    || !connectionForm.clientId.trim()
    || !connectionForm.brokerUrl.trim()
  ) {
    ElMessage.warning(t('mqtt.connectionRequired'))
    return
  }
  saving.value = true
  try {
    await saveMqttConnection(editingConnectionId.value, {
      ...connectionForm,
      name: connectionForm.name.trim(),
      clientId: connectionForm.clientId.trim(),
      brokerUrl: connectionForm.brokerUrl.trim(),
      username: connectionForm.username.trim(),
    })
    ElMessage.success(t('mqtt.connectionSaved'))
    connectionEditorOpen.value = false
    await load()
  } catch (reason) {
    notifyError(reason, t('mqtt.connectionSaveFailed'))
  } finally {
    saving.value = false
  }
}

async function toggleConnection(connection: MqttConnection, enabled: boolean) {
  try {
    await changeMqttConnectionStatus(connection.id, enabled)
    await load()
  } catch (reason) {
    connection.enabled = !enabled
    notifyError(reason, t('mqtt.statusFailed'))
  }
}

async function testConnection(connection: MqttConnection) {
  testingIds.value = [...testingIds.value, connection.id]
  try {
    const result = await testMqttConnection(connection.id)
    ElMessage({
      type: result.status === 'AVAILABLE' ? 'success' : 'error',
      message: result.status === 'AVAILABLE'
        ? t('mqtt.testSucceeded')
        : t('mqtt.testFailedMessage', { message: result.lastCheckMessage ?? '' }),
    })
    await load()
  } catch (reason) {
    notifyError(reason, t('mqtt.testFailed'))
  } finally {
    testingIds.value = testingIds.value.filter((id) => id !== connection.id)
  }
}

async function removeConnection(connection: MqttConnection) {
  if (!await confirmAction(
    t('mqtt.deleteConnectionConfirm', { name: connection.name }),
    t('mqtt.deleteConnection'),
    t('common.delete'),
  )) return
  try {
    await deleteMqttConnection(connection.id)
    ElMessage.success(t('mqtt.deleted'))
    await load()
  } catch (reason) {
    notifyError(reason, t('mqtt.deleteFailed'))
  }
}

function openSubscriptionEditor(subscription?: MqttSubscription) {
  editingSubscriptionId.value = subscription?.id ?? null
  Object.assign(subscriptionForm, subscription
    ? {
        connectionId: subscription.connectionId,
        topicFilter: subscription.topicFilter,
        qos: subscription.qos,
        description: subscription.description ?? '',
        enabled: subscription.enabled,
      }
    : emptySubscription())
  subscriptionEditorOpen.value = true
}

async function saveSubscription() {
  if (!subscriptionForm.connectionId || !subscriptionForm.topicFilter.trim()) {
    ElMessage.warning(t('mqtt.subscriptionRequired'))
    return
  }
  saving.value = true
  try {
    await saveMqttSubscription(editingSubscriptionId.value, {
      ...subscriptionForm,
      topicFilter: subscriptionForm.topicFilter.trim(),
      description: subscriptionForm.description.trim(),
    })
    ElMessage.success(t('mqtt.subscriptionSaved'))
    subscriptionEditorOpen.value = false
    await load()
  } catch (reason) {
    notifyError(reason, t('mqtt.subscriptionSaveFailed'))
  } finally {
    saving.value = false
  }
}

async function toggleSubscription(subscription: MqttSubscription, enabled: boolean) {
  try {
    await changeMqttSubscriptionStatus(subscription.id, enabled)
    await load()
  } catch (reason) {
    subscription.enabled = !enabled
    notifyError(reason, t('mqtt.statusFailed'))
  }
}

async function removeSubscription(subscription: MqttSubscription) {
  if (!await confirmAction(
    t('mqtt.deleteSubscriptionConfirm', { topic: subscription.topicFilter }),
    t('mqtt.deleteSubscription'),
    t('common.delete'),
  )) return
  try {
    await deleteMqttSubscription(subscription.id)
    ElMessage.success(t('mqtt.deleted'))
    await load()
  } catch (reason) {
    notifyError(reason, t('mqtt.deleteFailed'))
  }
}

function statusType(status: MqttConnectionStatus) {
  if (status === 'AVAILABLE') return 'success'
  if (status === 'UNAVAILABLE') return 'danger'
  return 'info'
}

onMounted(load)
</script>

<template>
  <section class="admin-page mqtt-page">
    <PageHeader
      :title="t('mqtt.title')"
      eyebrow="MQTT CONTROL PLANE"
      :description="t('mqtt.description')"
    >
      <template #actions>
        <el-tooltip :content="t('common.refresh')">
          <el-button
            :icon="Refresh"
            circle
            :loading="loading"
            :aria-label="t('common.refresh')"
            @click="load"
          />
        </el-tooltip>
        <el-button
          v-if="activeTab === 'connections'"
          v-permission="'basic:mqtt:manage'"
          type="primary"
          :icon="Plus"
          @click="openConnectionEditor()"
        >
          {{ t('mqtt.addConnection') }}
        </el-button>
        <el-button
          v-else
          v-permission="'basic:mqtt:manage'"
          type="primary"
          :icon="Plus"
          :disabled="!connections.length"
          @click="openSubscriptionEditor()"
        >
          {{ t('mqtt.addSubscription') }}
        </el-button>
      </template>
    </PageHeader>

    <div class="mqtt-summary">
      <div>
        <span>{{ t('mqtt.connections') }}</span>
        <strong>{{ overview.connectionCount }}</strong>
        <small>{{ t('mqtt.enabledCount', { count: overview.enabledConnectionCount }) }}</small>
      </div>
      <div>
        <span>{{ t('mqtt.availableConnections') }}</span>
        <strong>{{ overview.availableConnectionCount }}</strong>
        <small>{{ t('mqtt.lastProbeState') }}</small>
      </div>
      <div>
        <span>{{ t('mqtt.subscriptions') }}</span>
        <strong>{{ overview.subscriptionCount }}</strong>
        <small>{{ t('mqtt.enabledCount', { count: overview.enabledSubscriptionCount }) }}</small>
      </div>
    </div>

    <div class="mqtt-tabs">
      <button
        type="button"
        :class="{ active: activeTab === 'connections' }"
        @click="activeTab = 'connections'"
      >
        {{ t('mqtt.connectionTab') }}
      </button>
      <button
        type="button"
        :class="{ active: activeTab === 'subscriptions' }"
        @click="activeTab = 'subscriptions'"
      >
        {{ t('mqtt.subscriptionTab') }}
      </button>
    </div>

    <StatePanel
      v-if="loading && !connections.length && !subscriptions.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !connections.length && !subscriptions.length"
      state="error"
      :title="t('mqtt.loadFailed')"
      :description="error"
      @retry="load"
    />

    <template v-else-if="activeTab === 'connections'">
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          clearable
          :placeholder="t('mqtt.searchConnections')"
        />
      </div>
      <StatePanel
        v-if="!filteredConnections.length"
        state="empty"
        :title="t('mqtt.noConnections')"
        :description="t('mqtt.noConnectionsDescription')"
      />
      <div
        v-else
        class="table-shell"
      >
        <el-table
          :data="filteredConnections"
          row-key="id"
        >
          <el-table-column
            :label="t('mqtt.connection')"
            min-width="220"
          >
            <template #default="{ row }">
              <div class="mqtt-identity">
                <span class="mqtt-identity__signal" />
                <div>
                  <strong>{{ row.name }}</strong>
                  <small class="mono">{{ row.clientId }}</small>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('mqtt.brokerUrl')"
            min-width="260"
          >
            <template #default="{ row }">
              <span class="mono endpoint">{{ row.brokerUrl }}</span>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('mqtt.protocol')"
            width="105"
          >
            <template #default="{ row }">
              {{ row.tlsEnabled ? 'MQTTS' : 'MQTT' }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('mqtt.subscriptionCount')"
            width="105"
            align="right"
            prop="subscriptionCount"
          />
          <el-table-column
            :label="t('common.status')"
            width="150"
          >
            <template #default="{ row }">
              <el-tag
                :type="statusType(row.status)"
                effect="plain"
                size="small"
              >
                {{ t(`mqtt.statuses.${row.status}`) }}
              </el-tag>
              <small class="last-check">
                {{ row.lastCheckedAt ? formatDateTime(row.lastCheckedAt) : t('mqtt.notChecked') }}
              </small>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('mqtt.enabled')"
            width="90"
          >
            <template #default="{ row }">
              <el-switch
                v-model="row.enabled"
                :disabled="!auth.hasPermission('basic:mqtt:manage')"
                @change="toggleConnection(row, Boolean($event))"
              />
            </template>
          </el-table-column>
          <el-table-column
            :label="t('common.operation')"
            width="138"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="table-actions">
                <el-tooltip :content="t('mqtt.testConnection')">
                  <el-button
                    v-permission="'basic:mqtt:test'"
                    :icon="ConnectionIcon"
                    circle
                    text
                    :loading="testingIds.includes(row.id)"
                    :aria-label="t('mqtt.testConnection')"
                    @click="testConnection(row)"
                  />
                </el-tooltip>
                <el-tooltip :content="t('common.edit')">
                  <el-button
                    v-permission="'basic:mqtt:manage'"
                    :icon="Edit"
                    circle
                    text
                    :aria-label="t('common.edit')"
                    @click="openConnectionEditor(row)"
                  />
                </el-tooltip>
                <el-tooltip :content="t('common.delete')">
                  <el-button
                    v-permission="'basic:mqtt:manage'"
                    :icon="Delete"
                    circle
                    text
                    type="danger"
                    :aria-label="t('common.delete')"
                    @click="removeConnection(row)"
                  />
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <template v-else>
      <div class="filter-bar">
        <el-select
          v-model="subscriptionConnectionId"
          clearable
          :placeholder="t('mqtt.allConnections')"
        >
          <el-option
            v-for="connection in connections"
            :key="connection.id"
            :label="connection.name"
            :value="connection.id"
          />
        </el-select>
      </div>
      <StatePanel
        v-if="!filteredSubscriptions.length"
        state="empty"
        :title="t('mqtt.noSubscriptions')"
        :description="t('mqtt.noSubscriptionsDescription')"
      />
      <div
        v-else
        class="table-shell"
      >
        <el-table
          :data="filteredSubscriptions"
          row-key="id"
        >
          <el-table-column
            :label="t('mqtt.topicFilter')"
            min-width="250"
          >
            <template #default="{ row }">
              <code class="topic-filter">{{ row.topicFilter }}</code>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('mqtt.connection')"
            min-width="180"
            prop="connectionName"
          />
          <el-table-column
            label="QoS"
            width="90"
            align="center"
            prop="qos"
          />
          <el-table-column
            :label="t('mqtt.descriptionLabel')"
            min-width="220"
            prop="description"
          />
          <el-table-column
            :label="t('mqtt.enabled')"
            width="90"
          >
            <template #default="{ row }">
              <el-switch
                v-model="row.enabled"
                :disabled="!auth.hasPermission('basic:mqtt:manage')"
                @change="toggleSubscription(row, Boolean($event))"
              />
            </template>
          </el-table-column>
          <el-table-column
            :label="t('common.operation')"
            width="100"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="table-actions">
                <el-tooltip :content="t('common.edit')">
                  <el-button
                    v-permission="'basic:mqtt:manage'"
                    :icon="Edit"
                    circle
                    text
                    :aria-label="t('common.edit')"
                    @click="openSubscriptionEditor(row)"
                  />
                </el-tooltip>
                <el-tooltip :content="t('common.delete')">
                  <el-button
                    v-permission="'basic:mqtt:manage'"
                    :icon="Delete"
                    circle
                    text
                    type="danger"
                    :aria-label="t('common.delete')"
                    @click="removeSubscription(row)"
                  />
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <el-drawer
      v-model="connectionEditorOpen"
      :title="editingConnectionId == null ? t('mqtt.createConnection') : t('mqtt.editConnection')"
      size="min(560px, 94vw)"
      destroy-on-close
    >
      <el-form
        class="drawer-form"
        :model="connectionForm"
        label-position="top"
      >
        <div class="form-grid">
          <el-form-item
            :label="t('mqtt.name')"
            required
          >
            <el-input
              v-model="connectionForm.name"
              maxlength="120"
            />
          </el-form-item>
          <el-form-item
            label="Client ID"
            required
          >
            <el-input
              v-model="connectionForm.clientId"
              maxlength="128"
            />
          </el-form-item>
        </div>
        <el-form-item
          :label="t('mqtt.brokerUrl')"
          required
        >
          <el-input
            v-model="connectionForm.brokerUrl"
            maxlength="500"
            placeholder="tcp://broker.example.com:1883"
            @change="syncTlsWithBroker"
          />
        </el-form-item>
        <div class="form-grid">
          <el-form-item :label="t('mqtt.username')">
            <el-input
              v-model="connectionForm.username"
              maxlength="500"
              :placeholder="editingConnectionId == null ? '' : t('mqtt.keepCredential')"
            />
          </el-form-item>
          <el-form-item :label="t('mqtt.password')">
            <el-input
              v-model="connectionForm.password"
              type="password"
              show-password
              maxlength="1000"
              :placeholder="editingConnectionId == null ? '' : t('mqtt.keepCredential')"
            />
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item :label="t('mqtt.keepAlive')">
            <el-input-number
              v-model="connectionForm.keepAliveSeconds"
              :min="5"
              :max="3600"
            />
          </el-form-item>
          <el-form-item :label="t('mqtt.timeout')">
            <el-input-number
              v-model="connectionForm.connectionTimeoutSeconds"
              :min="1"
              :max="120"
            />
          </el-form-item>
        </div>
        <div class="switch-grid">
          <el-checkbox v-model="connectionForm.tlsEnabled">
            {{ t('mqtt.tls') }}
          </el-checkbox>
          <el-checkbox v-model="connectionForm.cleanSession">
            {{ t('mqtt.cleanSession') }}
          </el-checkbox>
          <el-checkbox v-model="connectionForm.enabled">
            {{ t('mqtt.enabled') }}
          </el-checkbox>
        </div>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="connectionEditorOpen = false">
            {{ t('common.cancel') }}
          </el-button>
          <el-button
            type="primary"
            :loading="saving"
            @click="saveConnection"
          >
            {{ t('common.save') }}
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer
      v-model="subscriptionEditorOpen"
      :title="editingSubscriptionId == null ? t('mqtt.createSubscription') : t('mqtt.editSubscription')"
      size="min(500px, 94vw)"
      destroy-on-close
    >
      <el-form
        class="drawer-form"
        :model="subscriptionForm"
        label-position="top"
      >
        <el-form-item
          :label="t('mqtt.connection')"
          required
        >
          <el-select
            v-model="subscriptionForm.connectionId"
            filterable
          >
            <el-option
              v-for="connection in connections"
              :key="connection.id"
              :label="connection.name"
              :value="connection.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          :label="t('mqtt.topicFilter')"
          required
        >
          <el-input
            v-model="subscriptionForm.topicFilter"
            maxlength="500"
            placeholder="robots/+/telemetry/#"
          />
        </el-form-item>
        <el-form-item label="QoS">
          <el-segmented
            v-model="subscriptionForm.qos"
            :options="[0, 1, 2]"
          />
        </el-form-item>
        <el-form-item :label="t('mqtt.descriptionLabel')">
          <el-input
            v-model="subscriptionForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-checkbox v-model="subscriptionForm.enabled">
          {{ t('mqtt.enabled') }}
        </el-checkbox>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="subscriptionEditorOpen = false">
            {{ t('common.cancel') }}
          </el-button>
          <el-button
            type="primary"
            :loading="saving"
            @click="saveSubscription"
          >
            {{ t('common.save') }}
          </el-button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.mqtt-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-block: 1px solid var(--color-border-strong);
}

.mqtt-summary > div {
  display: grid;
  gap: 4px;
  padding: 18px 24px;
}

.mqtt-summary > div + div {
  border-left: 1px solid var(--color-border);
}

.mqtt-summary span,
.mqtt-summary small {
  color: var(--color-text-muted);
  font-size: 11px;
}

.mqtt-summary strong {
  color: var(--color-ink);
  font-family: var(--font-mono);
  font-size: 25px;
}

.mqtt-tabs {
  display: flex;
  margin-top: 18px;
  border-bottom: 1px solid var(--color-border);
}

.mqtt-tabs button {
  min-width: 132px;
  padding: 12px 18px;
  cursor: pointer;
  border: 0;
  border-bottom: 2px solid transparent;
  color: var(--color-text-secondary);
  background: transparent;
}

.mqtt-tabs button:hover,
.mqtt-tabs button.active {
  color: var(--color-accent);
  border-bottom-color: var(--color-accent);
}

.mqtt-identity {
  display: flex;
  align-items: center;
  gap: 11px;
}

.mqtt-identity__signal {
  width: 4px;
  height: 34px;
  background: var(--color-accent);
}

.mqtt-identity div,
.last-check {
  display: flex;
  flex-direction: column;
}

.mqtt-identity strong {
  color: var(--color-ink);
}

.mqtt-identity small,
.last-check {
  margin-top: 3px;
  color: var(--color-text-muted);
  font-size: 10px;
}

.endpoint,
.topic-filter {
  color: var(--color-text-secondary);
  font-family: var(--font-mono);
  font-size: 11px;
}

.topic-filter {
  color: var(--color-accent);
}

.form-grid,
.switch-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.switch-grid {
  grid-template-columns: repeat(3, auto);
  justify-content: start;
  gap: 20px;
}

.drawer-form :deep(.el-select),
.drawer-form :deep(.el-input-number) {
  width: 100%;
}

@media (max-width: 700px) {
  .mqtt-summary,
  .form-grid,
  .switch-grid {
    grid-template-columns: 1fr;
  }

  .mqtt-summary > div + div {
    border-top: 1px solid var(--color-border);
    border-left: 0;
  }

  .mqtt-tabs button {
    flex: 1;
    min-width: 0;
  }
}
</style>
