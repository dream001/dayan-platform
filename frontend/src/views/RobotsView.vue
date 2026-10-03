<script setup lang="ts">
import {
  DataAnalysis,
  Delete,
  Edit,
  Link,
  Plus,
  Refresh,
  Search,
  Upload,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { deleteFile, getFilePreview, uploadFile } from '@/services/admin'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import { createRobot, deleteRobot, getRobots, updateRobot } from '@/services/robots'
import { useAuthStore } from '@/stores/auth'
import type {
  ActionMappingSupport,
  Robot,
  RobotPayload,
  RobotType,
} from '@/types/robot'

type RobotTypeFilter = RobotType | 'ALL'

const robotTypes: RobotType[] = [
  'HUMANOID',
  'MOBILE_MANIPULATOR',
  'DESKTOP_ARM',
  'MOBILE_BASE',
  'QUADRUPED',
  'INDUSTRIAL_ARM',
  'OTHER',
]
const mappingOptions: ActionMappingSupport[] = [
  'SUPPORTED',
  'COMING_SOON',
  'UNSUPPORTED',
]
const { locale, t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const robots = ref<Robot[]>([])
const activeType = ref<RobotTypeFilter>('ALL')
const keyword = ref('')
const editorOpen = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const iconInput = ref<HTMLInputElement>()
const iconFile = ref<File>()
const iconPreview = ref('')
const uploadProgress = ref(0)
const brokenImages = ref<Set<number>>(new Set())
const form = reactive<RobotPayload>({
  name: '',
  iconUrl: '',
  iconFileId: null,
  titleZh: '',
  titleEn: '',
  robotType: 'OTHER',
  actionMappingSupport: 'UNSUPPORTED',
  description: '',
  company: '',
  introductionUrl: '',
})

const canManage = computed(() => auth.hasPermission('basic:project:update'))
const visibleRobots = computed(() => {
  const needle = keyword.value.trim().toLocaleLowerCase()
  if (!needle) return robots.value
  return robots.value.filter((robot) => [
    robot.name,
    robot.titleZh,
    robot.titleEn,
    robot.company,
    robot.description,
  ].some((value) => value?.toLocaleLowerCase().includes(needle)))
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    robots.value = await getRobots(activeType.value === 'ALL' ? undefined : activeType.value)
  } catch (reason) {
    error.value = getErrorMessage(reason, t('robots.loadFailed'))
  } finally {
    loading.value = false
  }
}

function selectType(type: RobotTypeFilter) {
  activeType.value = type
  void load()
}

function displayName(robot: Robot) {
  if (locale.value.startsWith('zh')) return robot.titleZh || robot.titleEn || robot.name
  return robot.titleEn || robot.titleZh || robot.name
}

function resetForm() {
  releaseLocalPreview()
  Object.assign(form, {
    name: '',
    iconUrl: '',
    iconFileId: null,
    titleZh: '',
    titleEn: '',
    robotType: 'OTHER',
    actionMappingSupport: 'UNSUPPORTED',
    description: '',
    company: '',
    introductionUrl: '',
  })
  iconFile.value = undefined
  iconPreview.value = ''
  uploadProgress.value = 0
  editingId.value = null
}

function openCreate() {
  resetForm()
  editorMode.value = 'create'
  editorOpen.value = true
}

function openEdit(robot: Robot) {
  releaseLocalPreview()
  iconFile.value = undefined
  uploadProgress.value = 0
  editorMode.value = 'edit'
  editingId.value = robot.id
  Object.assign(form, {
    name: robot.name,
    iconUrl: robot.iconUrl,
    iconFileId: robot.iconFileId,
    titleZh: robot.titleZh ?? '',
    titleEn: robot.titleEn ?? '',
    robotType: robot.robotType,
    actionMappingSupport: robot.actionMappingSupport,
    description: robot.description ?? '',
    company: robot.company ?? '',
    introductionUrl: robot.introductionUrl ?? '',
  })
  iconPreview.value = robot.iconUrl
  editorOpen.value = true
}

function chooseIcon() {
  iconInput.value?.click()
}

function selectIcon(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/gif'].includes(file.type)) {
    ElMessage.warning(t('robots.iconType'))
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning(t('robots.iconSize'))
    return
  }
  releaseLocalPreview()
  iconFile.value = file
  iconPreview.value = URL.createObjectURL(file)
  uploadProgress.value = 0
}

function releaseLocalPreview() {
  if (iconPreview.value.startsWith('blob:')) {
    URL.revokeObjectURL(iconPreview.value)
  }
}

async function save() {
  if (!form.name.trim() || (!iconFile.value && !form.iconFileId && !form.iconUrl.trim())) {
    ElMessage.warning(t('robots.validation'))
    return
  }
  saving.value = true
  let uploadedFileId: number | null = null
  try {
    let iconUrl = form.iconUrl.trim()
    let iconFileId = form.iconFileId
    if (iconFile.value) {
      const uploaded = await uploadFile(iconFile.value, (value) => {
        uploadProgress.value = value
      })
      uploadedFileId = uploaded.id
      iconFileId = uploaded.id
      iconUrl = (await getFilePreview(uploaded.id)).url
    }
    const payload: RobotPayload = {
      ...form,
      name: form.name.trim(),
      iconUrl,
      iconFileId,
      titleZh: form.titleZh.trim(),
      titleEn: form.titleEn.trim(),
      description: form.description.trim(),
      company: form.company.trim(),
      introductionUrl: form.introductionUrl.trim(),
    }
    if (editorMode.value === 'create') {
      await createRobot(payload)
      ElMessage.success(t('robots.created'))
    } else if (editingId.value) {
      await updateRobot(editingId.value, payload)
      ElMessage.success(t('robots.updated'))
    }
    editorOpen.value = false
    releaseLocalPreview()
    iconFile.value = undefined
    await load()
  } catch (reason) {
    if (uploadedFileId != null) {
      try {
        await deleteFile(uploadedFileId)
      } catch {
        // The file service records recoverable deletion failures.
      }
    }
    notifyError(reason, t('robots.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function remove(robot: Robot) {
  const confirmed = await confirmAction(
    t('robots.deleteConfirm', { name: displayName(robot) }),
    t('robots.deleteTitle'),
    t('common.delete'),
  )
  if (!confirmed) return
  try {
    await deleteRobot(robot.id)
    ElMessage.success(t('robots.deleted'))
    await load()
  } catch (reason) {
    notifyError(reason, t('robots.deleteFailed'))
  }
}

function openDatasets(robot: Robot) {
  void router.push({ path: '/data/manage', query: { robot: robot.name } })
}

function markImageBroken(id: number) {
  brokenImages.value = new Set([...brokenImages.value, id])
}

onMounted(load)
onBeforeUnmount(releaseLocalPreview)
</script>

<template>
  <section class="admin-page robot-page">
    <PageHeader
      :title="t('robots.title')"
      :eyebrow="t('robots.eyebrow')"
      :description="t('robots.description')"
    >
      <template #actions>
        <el-tooltip :content="t('robots.refresh')">
          <el-button
            :icon="Refresh"
            circle
            :loading="loading"
            :aria-label="t('robots.refresh')"
            @click="load"
          />
        </el-tooltip>
        <el-button
          v-if="canManage"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          {{ t('robots.create') }}
        </el-button>
      </template>
    </PageHeader>

    <div class="robot-toolbar">
      <div
        class="type-tabs"
        role="tablist"
        :aria-label="t('robots.typeFilter')"
      >
        <button
          v-for="type in (['ALL', ...robotTypes] as RobotTypeFilter[])"
          :key="type"
          type="button"
          role="tab"
          :aria-selected="activeType === type"
          :class="{ active: activeType === type }"
          @click="selectType(type)"
        >
          {{ t(`robots.types.${type}`) }}
        </button>
      </div>
      <el-input
        v-model="keyword"
        class="robot-search"
        clearable
        :prefix-icon="Search"
        :placeholder="t('robots.search')"
      />
    </div>

    <StatePanel
      v-if="loading && !robots.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !robots.length"
      state="error"
      :title="t('robots.loadFailed')"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!visibleRobots.length"
      state="empty"
      :title="t('robots.empty')"
      :description="t('robots.emptyDesc')"
    />

    <div
      v-else
      v-loading="loading"
      class="robot-grid"
    >
      <article
        v-for="robot in visibleRobots"
        :key="robot.id"
        class="robot-card"
      >
        <button
          class="robot-visual"
          type="button"
          :aria-label="t('robots.openDatasets', { name: displayName(robot) })"
          @click="openDatasets(robot)"
        >
          <DataAnalysis class="robot-placeholder" />
          <img
            v-if="!brokenImages.has(robot.id)"
            :src="robot.iconUrl"
            :alt="displayName(robot)"
            loading="lazy"
            @error="markImageBroken(robot.id)"
          >
          <span
            class="mapping"
            :data-state="robot.actionMappingSupport"
          >
            {{ t(`robots.mapping.${robot.actionMappingSupport}`) }}
          </span>
        </button>

        <div class="robot-content">
          <div class="robot-heading">
            <div>
              <h2>{{ displayName(robot) }}</h2>
              <code>{{ robot.name }}</code>
            </div>
            <div
              v-if="canManage"
              class="robot-actions"
            >
              <el-tooltip :content="t('common.edit')">
                <el-button
                  :icon="Edit"
                  circle
                  text
                  :aria-label="t('common.edit')"
                  @click="openEdit(robot)"
                />
              </el-tooltip>
              <el-tooltip :content="t('common.delete')">
                <el-button
                  :icon="Delete"
                  circle
                  text
                  type="danger"
                  :aria-label="t('common.delete')"
                  @click="remove(robot)"
                />
              </el-tooltip>
            </div>
          </div>

          <div class="robot-meta">
            <el-tag
              size="small"
              effect="plain"
            >
              {{ t(`robots.types.${robot.robotType}`) }}
            </el-tag>
            <span v-if="robot.company">{{ robot.company }}</span>
          </div>
          <p v-if="robot.description">
            {{ robot.description }}
          </p>

          <div class="robot-footer">
            <button
              type="button"
              @click="openDatasets(robot)"
            >
              <strong>{{ robot.datasetCount }}</strong>
              <span>{{ t('robots.datasets') }}</span>
            </button>
            <a
              v-if="robot.introductionUrl"
              :href="robot.introductionUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              <Link />
              {{ t('robots.introduction') }}
            </a>
          </div>
        </div>
      </article>
    </div>

    <el-drawer
      v-model="editorOpen"
      :title="editorMode === 'create' ? t('robots.create') : t('robots.edit')"
      size="560px"
      destroy-on-close
    >
      <el-form
        label-position="top"
        @submit.prevent="save"
      >
        <div class="robot-form">
          <el-form-item
            :label="t('robots.fields.name')"
            required
          >
            <el-input
              v-model="form.name"
              maxlength="255"
              :disabled="editorMode === 'edit'"
            />
          </el-form-item>
          <el-form-item :label="t('robots.fields.type')">
            <el-select v-model="form.robotType">
              <el-option
                v-for="type in robotTypes"
                :key="type"
                :label="t(`robots.types.${type}`)"
                :value="type"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            class="wide"
            :label="t('robots.fields.icon')"
            required
          >
            <input
              ref="iconInput"
              class="robot-icon-input"
              type="file"
              accept="image/jpeg,image/png,image/gif"
              @change="selectIcon"
            >
            <button
              type="button"
              class="robot-icon-upload"
              @click="chooseIcon"
            >
              <img
                v-if="iconPreview"
                :src="iconPreview"
                :alt="t('robots.fields.icon')"
              >
              <span v-else>
                <el-icon><Upload /></el-icon>
                <strong>{{ t('robots.chooseIcon') }}</strong>
                <small>{{ t('robots.iconHint') }}</small>
              </span>
              <em v-if="iconPreview">
                <el-icon><Upload /></el-icon>
                {{ t('robots.replaceIcon') }}
              </em>
            </button>
            <el-progress
              v-if="saving && uploadProgress > 0 && uploadProgress < 100"
              :percentage="uploadProgress"
              :stroke-width="4"
              :show-text="false"
            />
          </el-form-item>
          <el-form-item :label="t('robots.fields.titleZh')">
            <el-input
              v-model="form.titleZh"
              maxlength="255"
            />
          </el-form-item>
          <el-form-item :label="t('robots.fields.titleEn')">
            <el-input
              v-model="form.titleEn"
              maxlength="255"
            />
          </el-form-item>
          <el-form-item :label="t('robots.fields.mapping')">
            <el-select v-model="form.actionMappingSupport">
              <el-option
                v-for="option in mappingOptions"
                :key="option"
                :label="t(`robots.mapping.${option}`)"
                :value="option"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('robots.fields.company')">
            <el-input
              v-model="form.company"
              maxlength="100"
            />
          </el-form-item>
          <el-form-item
            class="wide"
            :label="t('robots.fields.introductionUrl')"
          >
            <el-input
              v-model="form.introductionUrl"
              maxlength="1000"
            />
          </el-form-item>
          <el-form-item
            class="wide"
            :label="t('robots.fields.description')"
          >
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="4"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <div class="drawer-actions">
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
.robot-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 0;
}

.type-tabs {
  display: flex;
  min-width: 0;
  gap: 4px;
  overflow-x: auto;
}

.type-tabs button {
  flex: 0 0 auto;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--color-text-secondary);
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}

.type-tabs button:hover {
  color: var(--color-text-primary);
  background: var(--color-surface-muted);
}

.type-tabs button.active {
  color: var(--color-accent);
  border-color: color-mix(in srgb, var(--color-accent) 28%, transparent);
  background: color-mix(in srgb, var(--color-accent) 8%, transparent);
}

.robot-search {
  flex: 0 0 260px;
}

.robot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(270px, 1fr));
  gap: 16px;
  min-height: 220px;
}

.robot-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}

.robot-card:hover {
  border-color: var(--color-border-strong);
  box-shadow: 0 12px 28px rgb(20 35 45 / 9%);
  transform: translateY(-2px);
}

.robot-visual {
  position: relative;
  display: grid;
  width: 100%;
  aspect-ratio: 16 / 10;
  padding: 0;
  overflow: hidden;
  place-items: center;
  border: 0;
  border-bottom: 1px solid var(--color-border);
  background: #eef2f3;
  cursor: pointer;
}

.robot-visual img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.robot-placeholder {
  width: 52px;
  color: var(--color-text-tertiary);
}

.mapping {
  position: absolute;
  right: 10px;
  bottom: 10px;
  padding: 4px 8px;
  border-radius: 4px;
  color: #fff;
  background: rgb(46 62 69 / 82%);
  font-size: 11px;
}

.mapping[data-state='SUPPORTED'] {
  background: rgb(16 118 92 / 88%);
}

.mapping[data-state='COMING_SOON'] {
  background: rgb(176 113 25 / 90%);
}

.robot-content {
  padding: 15px;
}

.robot-heading {
  display: flex;
  min-height: 48px;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.robot-heading h2 {
  margin: 0 0 4px;
  overflow-wrap: anywhere;
  font-size: 16px;
  font-weight: 650;
  letter-spacing: 0;
}

.robot-heading code {
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.robot-actions {
  display: flex;
  flex: 0 0 auto;
}

.robot-meta {
  display: flex;
  min-height: 30px;
  align-items: center;
  gap: 8px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.robot-content > p {
  display: -webkit-box;
  min-height: 38px;
  margin: 8px 0 0;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.robot-footer {
  display: flex;
  min-height: 42px;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

.robot-footer button,
.robot-footer a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: 0;
  color: var(--color-text-secondary);
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  text-decoration: none;
}

.robot-footer button:hover,
.robot-footer a:hover {
  color: var(--color-accent);
}

.robot-footer strong {
  color: var(--color-text-primary);
  font-family: var(--font-mono);
  font-size: 20px;
  line-height: 1;
}

.robot-footer svg {
  width: 14px;
}

.robot-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.robot-form .wide {
  grid-column: 1 / -1;
}

.robot-icon-input {
  display: none;
}

.robot-icon-upload {
  position: relative;
  display: grid;
  width: 100%;
  min-height: 188px;
  padding: 0;
  overflow: hidden;
  place-items: center;
  border: 1px dashed var(--color-border-strong);
  border-radius: 6px;
  color: var(--color-text-secondary);
  background: #f7f9f9;
  cursor: pointer;
}

.robot-icon-upload:hover {
  border-color: var(--color-accent);
  background: #f1f8f8;
}

.robot-icon-upload > span {
  display: grid;
  justify-items: center;
  gap: 8px;
}

.robot-icon-upload > span .el-icon {
  color: var(--color-accent);
  font-size: 28px;
}

.robot-icon-upload strong {
  color: var(--color-text-primary);
  font-size: 13px;
}

.robot-icon-upload small {
  color: var(--color-text-muted);
  font-size: 11px;
}

.robot-icon-upload img {
  width: 100%;
  height: 188px;
  object-fit: contain;
  background: #eef2f3;
}

.robot-icon-upload em {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 9px;
  border-radius: 4px;
  color: #fff;
  background: rgb(26 42 48 / 78%);
  font-size: 11px;
  font-style: normal;
}

.robot-icon-upload + .el-progress {
  width: 100%;
  margin-top: 8px;
}

.robot-form :deep(.el-select) {
  width: 100%;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 760px) {
  .robot-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .robot-search {
    flex-basis: auto;
    width: 100%;
  }

  .robot-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .robot-form {
    grid-template-columns: 1fr;
  }

  .robot-form .wide {
    grid-column: auto;
  }
}
</style>
