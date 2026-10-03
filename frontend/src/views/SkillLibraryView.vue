<script setup lang="ts">
import {
  ArrowLeft,
  CollectionTag,
  Connection,
  EditPen,
  FullScreen,
  Picture,
  Plus,
  Reading,
  Refresh,
  TrendCharts,
  VideoCamera,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  getSkillLibrary,
  getSkillProjects,
  getSkillSamples,
  saveSkillAsset,
} from '@/services/skill-library'
import { getErrorMessage, notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'
import type {
  SkillAssetPayload,
  SkillLibrary,
  SkillCategory,
  SkillMediaType,
  SkillProject,
  SkillSample,
  SkillSummary,
} from '@/types/skill-library'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const auth = useAuthStore()

const projects = ref<SkillProject[]>([])
const projectId = ref<number>()
const library = ref<SkillLibrary>()
const loading = ref(true)
const loadingSamples = ref(false)
const error = ref('')
const keyword = ref('')
const category = ref<'ALL' | SkillCategory>('ALL')
const mediaType = ref<SkillMediaType>('COLOR')
const samples = ref<SkillSample[]>([])
const samplePage = ref(1)
const sampleTotal = ref(0)
const preview = ref<SkillSample>()
const loadSentinel = ref<HTMLElement>()
const editorOpen = ref(false)
const editingKey = ref('')
const saving = ref(false)
const skillForm = reactive<SkillAssetPayload>({
  nameZh: '',
  nameEn: '',
  description: '',
  category: 'BASIC',
  difficulty: 'BEGINNER',
  status: 'DRAFT',
  currentVersion: '1.0.0',
  usageScene: '',
  tags: [],
  template: false,
})
let observer: IntersectionObserver | null = null

const skillKey = computed(() =>
  typeof route.params.skill === 'string' ? route.params.skill : '',
)
const isDetail = computed(() => Boolean(skillKey.value))
const selectedSkill = computed(() =>
  library.value?.skills.find((skill) =>
    skill.key.toLowerCase() === skillKey.value.toLowerCase(),
  ),
)
const categoryOptions: Array<'ALL' | SkillCategory> = [
  'ALL',
  'BASIC',
  'COMPOSITE',
  'PROFESSIONAL',
]
const categoryCounts = computed(() =>
  Object.fromEntries(categoryOptions.map((value) => [
    value,
    value === 'ALL'
      ? (library.value?.skills.length ?? 0)
      : (library.value?.skills.filter((skill) => skill.category === value).length ?? 0),
  ])),
)
const publishedCount = computed(() =>
  library.value?.skills.filter((skill) => skill.status === 'PUBLISHED').length ?? 0,
)
const dependencyCount = computed(() =>
  library.value?.skills.reduce((total, skill) => total + skill.dependencyCount, 0) ?? 0,
)
const recentUsageCount = computed(() =>
  library.value?.skills.reduce((total, skill) => total + skill.recentUsageCount, 0) ?? 0,
)
const canManage = computed(() => auth.hasPermission('data:skill:manage'))
const filteredSkills = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  return (library.value?.skills ?? []).filter((skill) =>
    (category.value === 'ALL' || skill.category === category.value)
    && (!value
      || skill.name.toLowerCase().includes(value)
      || skill.key.toLowerCase().includes(value)
      || skill.tags.some((tag) => tag.toLowerCase().includes(value))),
  )
})
const hasMore = computed(() => samples.value.length < sampleTotal.value)

async function initialize() {
  loading.value = true
  error.value = ''
  try {
    projects.value = await getSkillProjects()
    const requestedProject = Number(route.query.project)
    projectId.value = projects.value.some((project) => project.id === requestedProject)
      ? requestedProject
      : undefined
    await loadLibrary()
    if (isDetail.value) await loadSamples(true)
  } catch (reason) {
    error.value = getErrorMessage(reason, t('skillLibrary.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function loadLibrary() {
  library.value = await getSkillLibrary(projectId.value, locale.value)
}

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    await loadLibrary()
    if (isDetail.value) await loadSamples(true)
  } catch (reason) {
    error.value = getErrorMessage(reason, t('skillLibrary.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function selectProject(value?: number) {
  projectId.value = value
  await router.replace({
    query: {
      ...route.query,
      project: value ? String(value) : undefined,
    },
  })
  await refresh()
}

async function loadSamples(reset = false) {
  if (!skillKey.value || loadingSamples.value) return
  if (reset) {
    samples.value = []
    samplePage.value = 1
    sampleTotal.value = 0
  }
  loadingSamples.value = true
  try {
    const page = await getSkillSamples(skillKey.value, {
      projectId: projectId.value,
      mediaType: mediaType.value,
      page: samplePage.value,
    })
    samples.value.push(...page.items)
    sampleTotal.value = page.total
    if (samples.value.length < page.total) samplePage.value += 1
  } catch (reason) {
    error.value = getErrorMessage(reason, t('skillLibrary.samplesFailed'))
  } finally {
    loadingSamples.value = false
    await nextTick()
    observeSentinel()
  }
}

async function switchMedia(value: string | number) {
  mediaType.value = value as SkillMediaType
  await loadSamples(true)
}

async function openSkill(key: string) {
  await router.push({
    path: `/data/skills/${encodeURIComponent(key)}`,
    query: route.query,
  })
}

async function backToLibrary() {
  await router.push({ path: '/data/skills', query: route.query })
}

function openEditor(skill?: SkillSummary) {
  editingKey.value = skill?.key ?? ''
  Object.assign(skillForm, {
    nameZh: skill?.name ?? '',
    nameEn: skill?.key ?? '',
    description: skill?.description ?? '',
    category: skill?.category ?? 'BASIC',
    difficulty: skill?.difficulty ?? 'BEGINNER',
    status: skill?.status ?? 'DRAFT',
    currentVersion: skill?.currentVersion ?? '1.0.0',
    usageScene: skill?.usageScene ?? '',
    tags: [...(skill?.tags ?? [])],
    template: false,
  })
  editorOpen.value = true
}

async function saveSkill() {
  const key = editingKey.value.trim().toLowerCase()
  if (!key || !skillForm.nameZh.trim() || !skillForm.nameEn.trim()) {
    ElMessage.warning(t('skillLibrary.required'))
    return
  }
  saving.value = true
  try {
    await saveSkillAsset(key, {
      ...skillForm,
      nameZh: skillForm.nameZh.trim(),
      nameEn: skillForm.nameEn.trim(),
      description: skillForm.description.trim(),
      usageScene: skillForm.usageScene.trim(),
      tags: skillForm.tags.map((tag) => tag.trim()).filter(Boolean),
    })
    editorOpen.value = false
    await loadLibrary()
    ElMessage.success(t('skillLibrary.saved'))
  } catch (reason) {
    notifyError(reason, t('skillLibrary.saveFailed'))
  } finally {
    saving.value = false
  }
}

function openPreview(sample: SkillSample) {
  if (sample.previewUrl) preview.value = sample
}

function observeSentinel() {
  observer?.disconnect()
  if (!loadSentinel.value || !hasMore.value) return
  observer = new IntersectionObserver(([entry]) => {
    if (entry?.isIntersecting && hasMore.value && !loadingSamples.value) {
      void loadSamples()
    }
  }, { rootMargin: '180px' })
  observer.observe(loadSentinel.value)
}

watch(locale, refresh)
watch(skillKey, async () => {
  preview.value = undefined
  mediaType.value = 'COLOR'
  if (isDetail.value) await loadSamples(true)
})

onMounted(initialize)
onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <section class="admin-page skill-library">
    <PageHeader
      :title="isDetail ? (selectedSkill?.name || skillKey) : t('skillLibrary.title')"
      :eyebrow="t('skillLibrary.eyebrow')"
      :description="isDetail ? t('skillLibrary.detailDescription') : t('skillLibrary.description')"
    >
      <template #actions>
        <el-button
          v-if="canManage"
          type="primary"
          :icon="Plus"
          @click="openEditor()"
        >
          {{ t('skillLibrary.create') }}
        </el-button>
        <el-select
          :model-value="projectId"
          class="project-select"
          :placeholder="t('skillLibrary.allProjects')"
          clearable
          filterable
          @update:model-value="selectProject"
        >
          <el-option
            v-for="project in projects"
            :key="project.id"
            :label="project.name"
            :value="project.id"
          >
            <span>{{ project.name }}</span>
            <small>{{ project.code }}</small>
          </el-option>
        </el-select>
        <el-tooltip :content="t('common.refresh')">
          <el-button
            :icon="Refresh"
            circle
            :loading="loading"
            :aria-label="t('common.refresh')"
            @click="refresh"
          />
        </el-tooltip>
      </template>
    </PageHeader>

    <StatePanel
      v-if="loading"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !library"
      state="error"
      :title="t('skillLibrary.loadFailed')"
      :description="error"
      @retry="initialize"
    />

    <template v-else-if="library">
      <template v-if="!isDetail">
        <div class="library-summary">
          <div class="summary-primary">
            <span>{{ t('skillLibrary.catalog') }}</span>
            <strong>{{ library.skillCount }}</strong>
            <span>{{ t('skillLibrary.skillUnit') }}</span>
          </div>
          <div class="summary-metrics">
            <p>
              <el-icon><CollectionTag /></el-icon>
              <span>{{ t('skillLibrary.published') }}</span>
              <strong>{{ publishedCount }}</strong>
            </p>
            <p>
              <el-icon><Connection /></el-icon>
              <span>{{ t('skillLibrary.dependencies') }}</span>
              <strong>{{ dependencyCount }}</strong>
            </p>
            <p>
              <el-icon><TrendCharts /></el-icon>
              <span>{{ t('skillLibrary.recentUsage') }}</span>
              <strong>{{ recentUsageCount }}</strong>
            </p>
          </div>
          <el-input
            v-model="keyword"
            clearable
            :placeholder="t('skillLibrary.searchPlaceholder')"
          />
        </div>

        <nav
          class="category-rail"
          :aria-label="t('skillLibrary.categories')"
        >
          <button
            v-for="item in categoryOptions"
            :key="item"
            type="button"
            :class="{ active: category === item }"
            @click="category = item"
          >
            <span>{{ t(`skillLibrary.category.${item}`) }}</span>
            <strong>{{ categoryCounts[item] }}</strong>
          </button>
        </nav>

        <section
          v-if="!library.skillCount"
          class="library-empty"
        >
          <div
            class="skill-chain"
            aria-hidden="true"
          >
            <i />
            <span />
            <i />
            <span />
            <i />
          </div>
          <div>
            <h2>{{ t('skillLibrary.empty') }}</h2>
            <p>{{ t('skillLibrary.emptyDescription') }}</p>
          </div>
          <div class="empty-actions">
            <RouterLink to="/data/dictionary">
              <el-icon><Reading /></el-icon>
              {{ t('skillLibrary.openDictionary') }}
            </RouterLink>
            <RouterLink to="/data/annotate-tasks">
              <el-icon><EditPen /></el-icon>
              {{ t('skillLibrary.openAnnotation') }}
            </RouterLink>
          </div>
        </section>

        <StatePanel
          v-else-if="!filteredSkills.length"
          state="empty"
          :title="t('skillLibrary.noMatch')"
          :description="t('skillLibrary.noMatchDescription')"
        />

        <div
          v-else
          class="skill-list"
        >
          <article
            v-for="(skill, index) in filteredSkills"
            :key="skill.key"
            class="skill-row"
          >
            <button
              class="skill-identity"
              type="button"
              @click="openSkill(skill.key)"
            >
              <span class="skill-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <span>
                <strong>
                  {{ skill.name }}
                  <i :class="`status-dot status-dot--${skill.status.toLowerCase()}`" />
                </strong>
                <small>{{ skill.key }}</small>
              </span>
              <em>
                {{ t(`skillLibrary.category.${skill.category}`) }}
                · {{ t(`skillLibrary.difficulty.${skill.difficulty}`) }}
              </em>
              <span class="skill-tags">
                <b
                  v-for="tag in skill.tags.slice(0, 3)"
                  :key="tag"
                >{{ tag }}</b>
              </span>
            </button>

            <div class="skill-samples">
              <button
                v-for="sample in skill.samples"
                :key="sample.annotationId"
                type="button"
                class="sample-tile"
                :aria-label="sample.description"
                @click="openPreview(sample)"
              >
                <img
                  v-if="sample.previewUrl && sample.contentType.startsWith('image/')"
                  :src="sample.previewUrl"
                  :alt="sample.description"
                >
                <video
                  v-else-if="sample.previewUrl && sample.contentType.startsWith('video/')"
                  :src="sample.previewUrl"
                  muted
                  playsinline
                  preload="metadata"
                />
                <span v-else>
                  <el-icon><Picture /></el-icon>
                </span>
                <i v-if="sample.contentType.startsWith('video/')">
                  <el-icon><VideoCamera /></el-icon>
                </i>
              </button>
              <div
                v-if="!skill.samples.length"
                class="sample-empty"
              >
                <el-icon><CollectionTag /></el-icon>
                <span>{{ t('skillLibrary.noVisualSamples') }}</span>
              </div>
            </div>

            <button
              class="skill-open"
              type="button"
              @click="openSkill(skill.key)"
            >
              <span>v{{ skill.currentVersion }} · {{ t('skillLibrary.versions', { count: skill.versionCount }) }}</span>
              <span>{{ t('skillLibrary.qualityRate', { value: skill.qualityRate }) }}</span>
              <span>{{ t('skillLibrary.projectScope', { count: skill.projectCount }) }}</span>
              <strong>{{ t('skillLibrary.open') }}</strong>
            </button>
          </article>
        </div>
      </template>

      <template v-else>
        <div class="detail-toolbar">
          <el-button
            :icon="ArrowLeft"
            text
            @click="backToLibrary"
          >
            {{ t('skillLibrary.back') }}
          </el-button>
          <el-tag
            closable
            effect="plain"
            @close="backToLibrary"
          >
            {{ selectedSkill?.name || skillKey }}
          </el-tag>
          <span>{{ selectedSkill?.key }}</span>
          <el-button
            v-if="canManage && selectedSkill"
            class="detail-toolbar__edit"
            :icon="EditPen"
            @click="openEditor(selectedSkill)"
          >
            {{ t('common.edit') }}
          </el-button>
        </div>

        <div
          v-if="selectedSkill"
          class="skill-facts"
        >
          <div>
            <span>{{ t('skillLibrary.categoryLabel') }}</span>
            <strong>{{ t(`skillLibrary.category.${selectedSkill.category}`) }}</strong>
          </div>
          <div>
            <span>{{ t('skillLibrary.version') }}</span>
            <strong>v{{ selectedSkill.currentVersion }}</strong>
          </div>
          <div>
            <span>{{ t('skillLibrary.difficultyLabel') }}</span>
            <strong>{{ t(`skillLibrary.difficulty.${selectedSkill.difficulty}`) }}</strong>
          </div>
          <div>
            <span>{{ t('skillLibrary.relations') }}</span>
            <strong>{{ selectedSkill.dependencyCount }}</strong>
          </div>
          <div>
            <span>{{ t('skillLibrary.quality') }}</span>
            <strong>{{ selectedSkill.qualityRate }}%</strong>
          </div>
          <div class="skill-facts__tags">
            <span>{{ t('skillLibrary.tags') }}</span>
            <strong>{{ selectedSkill.tags.join(' / ') || '—' }}</strong>
          </div>
        </div>

        <div class="media-tabs">
          <button
            v-for="type in (['COLOR', 'DEPTH'] as SkillMediaType[])"
            :key="type"
            type="button"
            :class="{ active: mediaType === type }"
            @click="switchMedia(type)"
          >
            <el-icon>
              <Picture v-if="type === 'COLOR'" />
              <CollectionTag v-else />
            </el-icon>
            {{ t(`skillLibrary.media.${type}`) }}
          </button>
          <span>{{ t('skillLibrary.sampleTotal', { count: sampleTotal }) }}</span>
        </div>

        <StatePanel
          v-if="!samples.length && loadingSamples"
          state="loading"
        />
        <StatePanel
          v-else-if="!samples.length"
          state="empty"
          :title="t('skillLibrary.noSamples')"
          :description="t('skillLibrary.noSamplesDescription')"
        />
        <div
          v-else
          class="sample-grid"
        >
          <article
            v-for="sample in samples"
            :key="sample.annotationId"
            class="sample-card"
          >
            <button
              type="button"
              class="sample-media"
              :disabled="!sample.previewUrl"
              @click="openPreview(sample)"
            >
              <img
                v-if="sample.previewUrl && sample.contentType.startsWith('image/')"
                :src="sample.previewUrl"
                :alt="sample.description"
              >
              <video
                v-else-if="sample.previewUrl && sample.contentType.startsWith('video/')"
                :src="sample.previewUrl"
                muted
                playsinline
                preload="metadata"
              />
              <span v-else>
                <el-icon><Picture /></el-icon>
                {{ t('skillLibrary.previewUnavailable') }}
              </span>
              <i v-if="sample.previewUrl">
                <el-icon><FullScreen /></el-icon>
              </i>
            </button>
            <div>
              <strong>{{ sample.description }}</strong>
              <span>{{ sample.projectName }}</span>
              <time>{{ formatDateTime(sample.createdAt) }}</time>
            </div>
          </article>
        </div>

        <div
          ref="loadSentinel"
          class="load-sentinel"
        >
          <span v-if="loadingSamples">{{ t('state.loading') }}</span>
          <span v-else-if="hasMore">{{ t('skillLibrary.scrollForMore') }}</span>
          <span v-else-if="samples.length">{{ t('skillLibrary.allLoaded') }}</span>
        </div>
      </template>
    </template>

    <el-dialog
      :model-value="Boolean(preview)"
      width="min(1080px, 92vw)"
      destroy-on-close
      class="sample-preview-dialog"
      :title="preview?.description"
      @close="preview = undefined"
    >
      <img
        v-if="preview?.previewUrl && preview.contentType.startsWith('image/')"
        :src="preview.previewUrl"
        :alt="preview.description"
      >
      <video
        v-else-if="preview?.previewUrl"
        :src="preview.previewUrl"
        controls
        autoplay
        playsinline
      />
    </el-dialog>

    <el-drawer
      v-model="editorOpen"
      :title="editingKey ? t('skillLibrary.edit') : t('skillLibrary.create')"
      size="560px"
      destroy-on-close
    >
      <el-form
        class="skill-form"
        label-position="top"
        @submit.prevent="saveSkill"
      >
        <el-form-item
          :label="t('skillLibrary.key')"
          required
        >
          <el-input
            v-model="editingKey"
            :disabled="Boolean(selectedSkill && editingKey === selectedSkill.key)"
            placeholder="pick-and-place"
          />
        </el-form-item>
        <div class="skill-form__grid">
          <el-form-item
            :label="t('skillLibrary.nameZh')"
            required
          >
            <el-input v-model="skillForm.nameZh" />
          </el-form-item>
          <el-form-item
            :label="t('skillLibrary.nameEn')"
            required
          >
            <el-input v-model="skillForm.nameEn" />
          </el-form-item>
          <el-form-item :label="t('skillLibrary.categoryLabel')">
            <el-select
              v-model="skillForm.category"
              style="width: 100%"
            >
              <el-option
                v-for="item in categoryOptions.slice(1)"
                :key="item"
                :label="t(`skillLibrary.category.${item}`)"
                :value="item"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('skillLibrary.difficultyLabel')">
            <el-select
              v-model="skillForm.difficulty"
              style="width: 100%"
            >
              <el-option
                v-for="item in ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT']"
                :key="item"
                :label="t(`skillLibrary.difficulty.${item}`)"
                :value="item"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('common.status')">
            <el-select
              v-model="skillForm.status"
              style="width: 100%"
            >
              <el-option
                v-for="item in ['DRAFT', 'PUBLISHED', 'DEPRECATED']"
                :key="item"
                :label="t(`skillLibrary.status.${item}`)"
                :value="item"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('skillLibrary.version')">
            <el-input
              v-model="skillForm.currentVersion"
              placeholder="1.0.0"
            />
          </el-form-item>
        </div>
        <el-form-item :label="t('skillLibrary.scene')">
          <el-input v-model="skillForm.usageScene" />
        </el-form-item>
        <el-form-item :label="t('skillLibrary.tags')">
          <el-select
            v-model="skillForm.tags"
            multiple
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('skillLibrary.descriptionLabel')">
          <el-input
            v-model="skillForm.description"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="skillForm.template">
            {{ t('skillLibrary.template') }}
          </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorOpen = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="saveSkill"
        >
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.project-select {
  width: min(300px, 44vw);
}

.project-select small {
  float: right;
  margin-left: 20px;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.library-summary {
  display: grid;
  grid-template-columns: auto 1fr 240px;
  align-items: center;
  gap: 24px;
  min-height: 92px;
  border-bottom: 1px solid var(--color-border-strong);
}

.summary-primary {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

.summary-primary span {
  color: var(--color-text-muted);
  font-size: 11px;
}

.summary-primary strong {
  color: var(--color-text-primary);
  font-family: var(--font-mono);
  font-size: 28px;
}

.summary-metrics {
  display: flex;
  align-items: center;
  gap: 28px;
}

.summary-metrics p {
  display: grid;
  grid-template-columns: 18px auto;
  gap: 2px 7px;
  margin: 0;
}

.summary-metrics .el-icon {
  grid-row: 1 / 3;
  align-self: center;
  color: var(--color-accent);
}

.summary-metrics span {
  color: var(--color-text-muted);
  font-size: 10px;
}

.summary-metrics strong {
  color: var(--color-text-primary);
  font-family: var(--font-mono);
  font-size: 16px;
}

.category-rail {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-bottom: 1px solid var(--color-border-strong);
}

.category-rail button {
  position: relative;
  display: flex;
  min-height: 50px;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  cursor: pointer;
  border: 0;
  border-right: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  background: transparent;
}

.category-rail button:last-child {
  border-right: 0;
}

.category-rail button::after {
  position: absolute;
  right: 18px;
  bottom: -1px;
  left: 18px;
  height: 2px;
  background: transparent;
  content: '';
}

.category-rail button:hover,
.category-rail button.active {
  color: var(--color-accent);
  background: #f3f8f8;
}

.category-rail button.active::after {
  background: var(--color-accent);
}

.category-rail button span {
  font-size: 12px;
  font-weight: 620;
}

.category-rail button strong {
  font-family: var(--font-mono);
  font-size: 12px;
}

.skill-list {
  border-bottom: 1px solid var(--color-border-strong);
}

.library-empty {
  display: grid;
  min-height: 330px;
  place-items: center;
  align-content: center;
  gap: 20px;
  text-align: center;
}

.skill-chain {
  display: flex;
  width: 150px;
  align-items: center;
}

.skill-chain i {
  width: 17px;
  height: 17px;
  flex: 0 0 auto;
  border: 3px solid var(--color-accent);
  border-radius: 50%;
  background: var(--color-canvas);
  box-shadow: 0 0 0 5px rgb(18 116 138 / 7%);
}

.skill-chain i:nth-of-type(2) {
  border-color: #d09a37;
}

.skill-chain i:last-child {
  border-color: #6c4ba7;
}

.skill-chain span {
  width: 50px;
  height: 2px;
  background: var(--color-border-strong);
}

.library-empty h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 650;
}

.library-empty p {
  max-width: 480px;
  margin: 8px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.7;
}

.empty-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.empty-actions a {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  gap: 7px;
  padding: 0 13px;
  border: 1px solid var(--color-border-strong);
  border-radius: 4px;
  color: var(--color-text-secondary);
  background: #fff;
  font-size: 12px;
  text-decoration: none;
}

.empty-actions a:first-child {
  border-color: var(--color-ink);
  color: #fff;
  background: var(--color-ink);
}

.empty-actions a:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.skill-row {
  display: grid;
  grid-template-columns: minmax(220px, 0.9fr) minmax(360px, 1.8fr) 150px;
  min-height: 142px;
  align-items: stretch;
  border-top: 1px solid var(--color-border);
}

.skill-row:first-child {
  border-top: 0;
}

.skill-identity,
.skill-open,
.sample-tile,
.sample-media {
  cursor: pointer;
  border: 0;
  background: transparent;
}

.skill-identity {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  align-content: center;
  align-items: center;
  gap: 3px 13px;
  padding: 20px 20px 20px 4px;
  color: var(--color-text-primary);
  text-align: left;
}

.skill-index {
  grid-row: 1 / 3;
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 10px;
}

.skill-identity > span:nth-child(2) {
  min-width: 0;
}

.skill-identity strong,
.skill-identity small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skill-identity strong {
  font-size: 17px;
  font-weight: 650;
}

.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-left: 7px;
  border-radius: 50%;
  background: #a8b3b8;
}

.status-dot--published {
  background: #25a47a;
  box-shadow: 0 0 0 3px rgb(37 164 122 / 10%);
}

.status-dot--deprecated {
  background: #b05b52;
}

.skill-identity small {
  margin-top: 5px;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.skill-identity em {
  grid-column: 2;
  color: var(--color-text-secondary);
  font-size: 11px;
  font-style: normal;
}

.skill-tags {
  display: flex;
  grid-column: 2;
  min-width: 0;
  gap: 5px;
}

.skill-tags b {
  overflow: hidden;
  padding: 2px 6px;
  border: 1px solid var(--color-border);
  border-radius: 3px;
  color: var(--color-text-muted);
  font-size: 9px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skill-samples {
  display: grid;
  grid-template-columns: repeat(5, minmax(48px, 1fr));
  gap: 5px;
  align-items: center;
  padding: 18px 14px;
  border-inline: 1px solid var(--color-border);
}

.sample-tile {
  position: relative;
  min-width: 0;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  border-radius: 4px;
  background: #e8eef0;
}

.sample-tile img,
.sample-tile video,
.sample-media img,
.sample-media video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.sample-tile > span {
  display: grid;
  height: 100%;
  place-items: center;
  color: #9aa9af;
}

.sample-tile i,
.sample-media i {
  position: absolute;
  right: 6px;
  bottom: 6px;
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: 3px;
  color: #fff;
  background: rgb(16 25 30 / 78%);
}

.sample-empty {
  display: flex;
  grid-column: 1 / -1;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: var(--color-text-muted);
  font-size: 11px;
}

.skill-open {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
  padding: 20px 4px 20px 20px;
  color: var(--color-text-muted);
  text-align: right;
}

.skill-open span {
  font-size: 10px;
}

.skill-open strong {
  color: var(--color-accent);
  font-size: 12px;
}

.skill-row:hover {
  background: #f3f7f8;
}

.detail-toolbar {
  display: flex;
  min-height: 62px;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--color-border);
}

.detail-toolbar > span {
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.detail-toolbar__edit {
  margin-left: auto;
}

.skill-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.skill-facts {
  display: grid;
  grid-template-columns: repeat(5, minmax(90px, 1fr)) minmax(180px, 1.6fr);
  border-bottom: 1px solid var(--color-border-strong);
}

.skill-facts > div {
  display: flex;
  min-height: 68px;
  justify-content: center;
  flex-direction: column;
  gap: 7px;
  padding: 12px 16px;
  border-right: 1px solid var(--color-border);
}

.skill-facts > div:last-child {
  border-right: 0;
}

.skill-facts span {
  color: var(--color-text-muted);
  font-size: 9px;
  text-transform: uppercase;
}

.skill-facts strong {
  overflow: hidden;
  font-size: 12px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-tabs {
  display: flex;
  min-height: 58px;
  align-items: center;
  gap: 2px;
  border-bottom: 1px solid var(--color-border-strong);
}

.media-tabs button {
  display: flex;
  height: 34px;
  align-items: center;
  gap: 7px;
  padding: 0 13px;
  cursor: pointer;
  border: 0;
  border-radius: 4px;
  color: var(--color-text-secondary);
  background: transparent;
}

.media-tabs button.active {
  color: #fff;
  background: var(--color-ink);
}

.media-tabs > span {
  margin-left: auto;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.sample-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  padding-top: 18px;
}

.sample-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 5px;
  background: #fff;
}

.sample-media {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: #e8eef0;
}

.sample-media > span {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 7px;
  color: var(--color-text-muted);
  font-size: 11px;
}

.sample-card > div {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 6px 10px;
  padding: 12px;
}

.sample-card strong {
  grid-column: 1 / -1;
  overflow: hidden;
  font-size: 12px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sample-card span,
.sample-card time {
  color: var(--color-text-muted);
  font-size: 10px;
}

.sample-card time {
  font-family: var(--font-mono);
}

.load-sentinel {
  display: grid;
  min-height: 62px;
  place-items: center;
  color: var(--color-text-muted);
  font-size: 11px;
}

:global(.sample-preview-dialog .el-dialog__body) {
  display: grid;
  max-height: 76vh;
  place-items: center;
  overflow: auto;
  background: #0f181d;
}

:global(.sample-preview-dialog img),
:global(.sample-preview-dialog video) {
  max-width: 100%;
  max-height: 70vh;
}

@media (max-width: 1000px) {
  .skill-row {
    grid-template-columns: minmax(190px, 0.8fr) minmax(300px, 1.5fr);
  }

  .skill-open {
    display: none;
  }

  .sample-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .project-select {
    width: calc(100% - 42px);
  }

  .library-summary {
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 16px 0;
  }

  .library-summary .el-input {
    width: 100%;
  }

  .summary-metrics {
    justify-content: space-between;
    gap: 10px;
  }

  .category-rail {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .skill-row {
    grid-template-columns: minmax(0, 1fr);
    padding-bottom: 14px;
  }

  .skill-identity {
    padding: 16px 4px;
  }

  .skill-samples {
    grid-template-columns: repeat(5, minmax(48px, 1fr));
    overflow-x: auto;
    padding: 0 4px;
    border: 0;
  }

  .sample-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .media-tabs > span {
    display: none;
  }

  .skill-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .skill-form__grid {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skill-row,
  .sample-tile {
    transition: none;
  }
}
</style>
