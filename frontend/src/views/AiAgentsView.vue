<script setup lang="ts">
import {
  ChatDotRound,
  Delete,
  EditPen,
  Plus,
  Promotion,
  Refresh,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  createAiAgent,
  debugAiAgent,
  deleteAiAgent,
  getAiAgents,
  updateAiAgent,
} from '@/services/ai-agents'
import { getAiModels } from '@/services/ai-models'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'
import type { AiAgent, AiAgentPayload } from '@/types/ai-agent'
import type { AiModel } from '@/types/ai-model'

interface DebugEntry {
  role: 'user' | 'assistant'
  content: string
  latencyMs?: number
  modelName?: string
}

const { t } = useI18n()
const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const debugging = ref(false)
const error = ref('')
const agents = ref<AiAgent[]>([])
const models = ref<AiModel[]>([])
const selectedId = ref<number | null>(null)
const creating = ref(false)
const activeTab = ref<'config' | 'debug'>('config')
const debugMessage = ref('')
const debugEntries = ref<DebugEntry[]>([])
const draft = reactive<AiAgentPayload>({
  name: '',
  description: '',
  systemPrompt: '',
  modelId: null,
  temperature: 0.7,
  maxTokens: 2048,
  enabled: true,
})

const selectedAgent = computed(
  () => agents.value.find((agent) => agent.id === selectedId.value) ?? null,
)

const availableModels = computed(
  () => models.value.filter(
    (model) => model.enabled && ['CHAT', 'MULTIMODAL'].includes(model.modelType),
  ),
)

const canSave = computed(() => creating.value
  ? auth.hasPermission('basic:agent:create')
  : auth.hasPermission('basic:agent:update'))

function assignDraft(agent?: AiAgent) {
  Object.assign(draft, agent
    ? {
        name: agent.name,
        description: agent.description ?? '',
        systemPrompt: agent.systemPrompt,
        modelId: agent.modelId,
        temperature: Number(agent.temperature),
        maxTokens: agent.maxTokens,
        enabled: agent.enabled,
      }
    : {
        name: '',
        description: '',
        systemPrompt: '',
        modelId: availableModels.value[0]?.id ?? null,
        temperature: 0.7,
        maxTokens: 2048,
        enabled: true,
      })
}

function selectAgent(agent: AiAgent) {
  selectedId.value = agent.id
  creating.value = false
  activeTab.value = 'config'
  debugEntries.value = []
  assignDraft(agent)
}

function startCreate() {
  selectedId.value = null
  creating.value = true
  activeTab.value = 'config'
  debugEntries.value = []
  assignDraft()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [agentItems, modelPage] = await Promise.all([
      getAiAgents(),
      getAiModels({ page: 1, size: 200, enabled: true }),
    ])
    agents.value = agentItems
    models.value = modelPage.items
    const current = agents.value.find((agent) => agent.id === selectedId.value)
    if (current) {
      assignDraft(current)
    } else if (agents.value.length) {
      selectAgent(agents.value[0])
    } else {
      startCreate()
    }
  } catch (reason) {
    error.value = getErrorMessage(reason, t('aiAgents.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!draft.name.trim() || !draft.systemPrompt.trim() || !draft.modelId) {
    ElMessage.warning(t('aiAgents.validation'))
    return
  }
  saving.value = true
  try {
    const payload: AiAgentPayload = {
      ...draft,
      name: draft.name.trim(),
      description: draft.description.trim(),
      systemPrompt: draft.systemPrompt.trim(),
    }
    const saved = creating.value
      ? await createAiAgent(payload)
      : await updateAiAgent(selectedId.value as number, payload)
    ElMessage.success(t(creating.value ? 'aiAgents.created' : 'aiAgents.updated'))
    creating.value = false
    await load()
    const current = agents.value.find((agent) => agent.id === saved.id)
    if (current) selectAgent(current)
  } catch (reason) {
    notifyError(reason, t('aiAgents.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function remove(agent: AiAgent) {
  const confirmed = await confirmAction(
    t('aiAgents.deleteConfirm', { name: agent.name }),
    t('aiAgents.deleteTitle'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    await deleteAiAgent(agent.id)
    ElMessage.success(t('aiAgents.deleted'))
    selectedId.value = null
    await load()
  } catch (reason) {
    notifyError(reason, t('aiAgents.deleteFailed'))
  }
}

function openDebug(agent: AiAgent) {
  if (selectedId.value !== agent.id) {
    selectedId.value = agent.id
    creating.value = false
    assignDraft(agent)
    debugEntries.value = []
  }
  activeTab.value = 'debug'
}

async function sendDebug() {
  const message = debugMessage.value.trim()
  const agent = selectedAgent.value
  if (!message || !agent) return
  debugEntries.value.push({ role: 'user', content: message })
  debugMessage.value = ''
  debugging.value = true
  try {
    const result = await debugAiAgent(agent.id, message)
    debugEntries.value.push({
      role: 'assistant',
      content: result.content,
      latencyMs: result.latencyMs,
      modelName: result.modelName,
    })
  } catch (reason) {
    notifyError(reason, t('aiAgents.debugFailed'))
  } finally {
    debugging.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page agent-page">
    <PageHeader
      :title="t('aiAgents.title')"
      :eyebrow="t('aiAgents.eyebrow')"
      :description="t('aiAgents.description')"
    >
      <template #actions>
        <el-tooltip :content="t('aiAgents.refresh')">
          <el-button
            :icon="Refresh"
            circle
            :loading="loading"
            :aria-label="t('aiAgents.refresh')"
            @click="load"
          />
        </el-tooltip>
        <el-button
          v-permission="'basic:agent:create'"
          type="primary"
          :icon="Plus"
          @click="startCreate"
        >
          {{ t('aiAgents.create') }}
        </el-button>
      </template>
    </PageHeader>

    <StatePanel
      v-if="loading && !agents.length && !creating"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !agents.length"
      state="error"
      :title="t('aiAgents.loadFailed')"
      :description="error"
      @retry="load"
    />
    <div
      v-else
      class="agent-workspace"
    >
      <aside class="agent-list">
        <div class="agent-list__heading">
          <span>{{ t('aiAgents.agentList') }}</span>
          <strong>{{ agents.length }}</strong>
        </div>
        <button
          v-for="agent in agents"
          :key="agent.id"
          class="agent-item"
          :class="{ 'agent-item--active': selectedId === agent.id && !creating }"
          type="button"
          @click="selectAgent(agent)"
        >
          <span
            class="agent-item__status"
            :class="{ 'agent-item__status--enabled': agent.enabled }"
          />
          <span class="agent-item__body">
            <strong>{{ agent.name }}</strong>
            <small>{{ agent.modelManufacturer }} · {{ agent.modelName }}</small>
          </span>
          <el-icon>
            <EditPen />
          </el-icon>
        </button>
        <button
          v-if="!agents.length"
          class="agent-list__empty"
          type="button"
          @click="startCreate"
        >
          <el-icon :size="22">
            <Plus />
          </el-icon>
          <span>{{ t('aiAgents.empty') }}</span>
        </button>
      </aside>

      <main class="agent-editor">
        <div class="agent-editor__bar">
          <div>
            <span>{{ creating ? t('aiAgents.newAgent') : selectedAgent?.name }}</span>
            <small v-if="!creating && selectedAgent">
              {{ selectedAgent.modelManufacturer }} / {{ selectedAgent.modelName }}
            </small>
          </div>
          <div
            v-if="selectedAgent"
            class="agent-editor__actions"
          >
            <el-button
              v-permission="'basic:agent:debug'"
              :icon="ChatDotRound"
              :disabled="!selectedAgent.enabled"
              @click="openDebug(selectedAgent)"
            >
              {{ t('aiAgents.goDebug') }}
            </el-button>
            <el-tooltip :content="t('common.delete')">
              <el-button
                v-permission="'basic:agent:delete'"
                :icon="Delete"
                circle
                text
                type="danger"
                :aria-label="t('common.delete')"
                @click="remove(selectedAgent)"
              />
            </el-tooltip>
          </div>
        </div>

        <el-tabs v-model="activeTab">
          <el-tab-pane
            :label="t('aiAgents.configuration')"
            name="config"
          >
            <el-form
              class="agent-form"
              label-position="top"
              @submit.prevent="save"
            >
              <div class="agent-form__row">
                <el-form-item
                  :label="t('aiAgents.name')"
                  required
                >
                  <el-input
                    v-model="draft.name"
                    maxlength="120"
                    :placeholder="t('aiAgents.namePlaceholder')"
                  />
                </el-form-item>
                <el-form-item
                  :label="t('aiAgents.model')"
                  required
                >
                  <el-select
                    v-model="draft.modelId"
                    filterable
                    :placeholder="t('aiAgents.selectModel')"
                  >
                    <el-option
                      v-for="model in availableModels"
                      :key="model.id"
                      :label="`${model.manufacturer} / ${model.name}`"
                      :value="model.id"
                    />
                  </el-select>
                  <small
                    v-if="!availableModels.length"
                    class="field-note field-note--danger"
                  >
                    {{ t('aiAgents.noModels') }}
                  </small>
                </el-form-item>
              </div>
              <el-form-item :label="t('aiAgents.descriptionLabel')">
                <el-input
                  v-model="draft.description"
                  maxlength="500"
                  show-word-limit
                  :placeholder="t('aiAgents.descriptionPlaceholder')"
                />
              </el-form-item>
              <el-form-item
                :label="t('aiAgents.systemPrompt')"
                required
              >
                <el-input
                  v-model="draft.systemPrompt"
                  class="prompt-editor"
                  type="textarea"
                  :rows="12"
                  maxlength="20000"
                  show-word-limit
                  resize="vertical"
                  :placeholder="t('aiAgents.promptPlaceholder')"
                />
              </el-form-item>
              <div class="agent-form__parameters">
                <el-form-item :label="t('aiAgents.temperature')">
                  <el-slider
                    v-model="draft.temperature"
                    :min="0"
                    :max="2"
                    :step="0.1"
                    show-input
                    :show-input-controls="false"
                  />
                </el-form-item>
                <el-form-item :label="t('aiAgents.maxTokens')">
                  <el-input-number
                    v-model="draft.maxTokens"
                    :min="1"
                    :max="32768"
                    :step="256"
                    controls-position="right"
                  />
                </el-form-item>
                <el-form-item :label="t('common.status')">
                  <el-switch
                    v-model="draft.enabled"
                    :active-text="t('aiAgents.statusEnabled')"
                    :inactive-text="t('aiAgents.statusDisabled')"
                  />
                </el-form-item>
              </div>
              <div class="agent-form__footer">
                <el-button
                  type="primary"
                  :loading="saving"
                  :disabled="!canSave || !availableModels.length"
                  @click="save"
                >
                  {{ t('common.save') }}
                </el-button>
              </div>
            </el-form>
          </el-tab-pane>

          <el-tab-pane
            :label="t('aiAgents.debug')"
            name="debug"
            :disabled="!selectedAgent || creating"
          >
            <div class="debug-console">
              <div class="debug-console__meta">
                <span>{{ selectedAgent?.modelName }}</span>
                <span>temperature {{ selectedAgent?.temperature }}</span>
                <span>max_tokens {{ selectedAgent?.maxTokens }}</span>
              </div>
              <div class="debug-messages">
                <div
                  v-if="!debugEntries.length"
                  class="debug-empty"
                >
                  <el-icon :size="28">
                    <ChatDotRound />
                  </el-icon>
                  <strong>{{ t('aiAgents.debugEmpty') }}</strong>
                </div>
                <article
                  v-for="(entry, index) in debugEntries"
                  :key="index"
                  class="debug-message"
                  :class="`debug-message--${entry.role}`"
                >
                  <span>{{ entry.role === 'user' ? t('aiAgents.you') : selectedAgent?.name }}</span>
                  <p>{{ entry.content }}</p>
                  <small v-if="entry.latencyMs">
                    {{ entry.modelName }} · {{ entry.latencyMs }} ms
                  </small>
                </article>
              </div>
              <form
                class="debug-composer"
                @submit.prevent="sendDebug"
              >
                <el-input
                  v-model="debugMessage"
                  type="textarea"
                  :rows="3"
                  maxlength="20000"
                  resize="none"
                  :placeholder="t('aiAgents.debugPlaceholder')"
                />
                <el-button
                  v-permission="'basic:agent:debug'"
                  type="primary"
                  :icon="Promotion"
                  :loading="debugging"
                  :disabled="!debugMessage.trim() || !selectedAgent?.enabled"
                  circle
                  :aria-label="t('aiAgents.send')"
                  native-type="submit"
                />
              </form>
            </div>
          </el-tab-pane>
        </el-tabs>
      </main>
    </div>
  </section>
</template>

<style scoped>
.agent-workspace {
  display: grid;
  min-height: 620px;
  margin-top: 22px;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
}

.agent-list {
  min-width: 0;
  padding: 14px 10px;
  border-right: 1px solid var(--color-border);
  background: #f4f7f8;
}

.agent-list__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 8px 13px;
  color: var(--color-text-muted);
  font-size: 11px;
  font-weight: 650;
}

.agent-list__heading strong {
  color: var(--color-accent);
  font-family: var(--font-mono);
}

.agent-item {
  display: grid;
  width: 100%;
  min-height: 64px;
  align-items: center;
  gap: 10px;
  padding: 10px 9px;
  cursor: pointer;
  border: 0;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-primary);
  background: transparent;
  grid-template-columns: 5px minmax(0, 1fr) 16px;
  text-align: left;
}

.agent-item:hover,
.agent-item--active {
  background: #fff;
}

.agent-item--active {
  box-shadow: inset 2px 0 var(--color-accent);
}

.agent-item__status {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #a9b3b9;
}

.agent-item__status--enabled {
  background: #2b9478;
  box-shadow: 0 0 0 3px rgb(43 148 120 / 12%);
}

.agent-item__body {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.agent-item__body strong,
.agent-item__body small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-item__body strong {
  font-size: 13px;
}

.agent-item__body small {
  color: var(--color-text-muted);
  font-size: 10px;
}

.agent-item > .el-icon {
  color: var(--color-text-muted);
}

.agent-list__empty {
  display: flex;
  width: 100%;
  min-height: 150px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 9px;
  cursor: pointer;
  border: 1px dashed var(--color-border-strong);
  color: var(--color-text-muted);
  background: transparent;
}

.agent-editor {
  min-width: 0;
  padding: 18px 24px 24px;
}

.agent-editor__bar {
  display: flex;
  min-height: 44px;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.agent-editor__bar > div:first-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.agent-editor__bar span {
  overflow: hidden;
  color: var(--color-ink);
  font-size: 15px;
  font-weight: 680;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-editor__bar small {
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.agent-editor__actions {
  display: flex;
  flex: 0 0 auto;
  gap: 4px;
}

.agent-form {
  padding-top: 12px;
}

.agent-form__row,
.agent-form__parameters {
  display: grid;
  gap: 16px;
}

.agent-form__row {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.agent-form__parameters {
  align-items: end;
  grid-template-columns: minmax(240px, 1fr) minmax(150px, 180px) minmax(150px, 180px);
}

.agent-form :deep(.el-select),
.agent-form :deep(.el-input-number) {
  width: 100%;
}

.prompt-editor :deep(textarea) {
  color: #22343d;
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.75;
}

.field-note {
  display: block;
  margin-top: 6px;
  font-size: 11px;
}

.field-note--danger {
  color: var(--color-danger);
}

.agent-form__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-top: 18px;
  border-top: 1px solid var(--color-border);
}

.debug-console {
  display: grid;
  min-height: 500px;
  grid-template-rows: auto minmax(300px, 1fr) auto;
  background: #f8fafb;
}

.debug-console__meta {
  display: flex;
  min-height: 38px;
  align-items: center;
  gap: 18px;
  padding: 0 14px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.debug-console__meta span:first-child {
  color: var(--color-accent);
  font-weight: 650;
}

.debug-messages {
  display: flex;
  max-height: 470px;
  overflow-y: auto;
  padding: 22px;
  flex-direction: column;
  gap: 16px;
}

.debug-empty {
  display: flex;
  min-height: 280px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
  color: var(--color-text-muted);
}

.debug-empty strong {
  font-size: 13px;
  font-weight: 550;
}

.debug-message {
  width: min(82%, 720px);
  padding: 12px 14px;
  border-left: 3px solid #cfdae0;
  background: #fff;
}

.debug-message--user {
  align-self: flex-end;
  border-right: 3px solid var(--color-accent);
  border-left: 0;
  background: #edf6f7;
}

.debug-message span,
.debug-message small {
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 9px;
}

.debug-message p {
  margin: 7px 0 0;
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.debug-message small {
  display: block;
  margin-top: 7px;
}

.debug-composer {
  display: grid;
  align-items: end;
  gap: 10px;
  padding: 14px;
  border-top: 1px solid var(--color-border);
  background: #fff;
  grid-template-columns: minmax(0, 1fr) 36px;
}

@media (max-width: 820px) {
  .agent-workspace {
    min-height: 0;
    grid-template-columns: 1fr;
  }

  .agent-list {
    display: flex;
    overflow-x: auto;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .agent-list__heading {
    min-width: 86px;
  }

  .agent-item {
    width: 210px;
    flex: 0 0 auto;
    border-right: 1px solid var(--color-border);
    border-bottom: 0;
  }

  .agent-list__empty {
    min-width: 180px;
    min-height: 64px;
    flex-direction: row;
  }

  .agent-editor {
    padding: 16px;
  }

  .agent-form__row,
  .agent-form__parameters {
    grid-template-columns: 1fr;
    gap: 0;
  }
}

@media (max-width: 520px) {
  .agent-editor__bar {
    align-items: stretch;
    flex-direction: column;
  }

  .agent-editor__actions {
    justify-content: space-between;
  }

  .debug-console__meta {
    align-items: flex-start;
    padding: 10px 12px;
    flex-direction: column;
    gap: 4px;
  }

  .debug-messages {
    padding: 14px;
  }

  .debug-message {
    width: 94%;
  }
}
</style>
