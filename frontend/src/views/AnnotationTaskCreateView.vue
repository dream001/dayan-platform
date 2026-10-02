<script setup lang="ts">
import { ArrowLeft, Check, Sort } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import {
  createAnnotationTasks,
  getAnnotationTaskOptions,
} from '@/services/annotation-tasks'
import { notifyError } from '@/services/feedback'
import type {
  AnnotationDatasetOption,
  AnnotationPersonOption,
  AnnotationProjectOption,
} from '@/types/annotation-task'
import { formatBytes, formatDateTime } from '@/utils/format'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const projects = ref<AnnotationProjectOption[]>([])
const annotators = ref<AnnotationPersonOption[]>([])
const reviewers = ref<AnnotationPersonOption[]>([])
const datasets = ref<AnnotationDatasetOption[]>([])
const form = reactive({
  name: '',
  projectId: undefined as number | undefined,
  annotatorIds: [] as number[],
  reviewerIds: [] as number[],
  datasetIds: [] as number[],
  randomOrder: false,
})

const allocation = computed(() => {
  if (!form.annotatorIds.length) return []
  const base = Math.floor(form.datasetIds.length / form.annotatorIds.length)
  const remainder = form.datasetIds.length % form.annotatorIds.length
  return form.annotatorIds.map((id, index) => ({
    id,
    name: annotators.value.find((person) => person.id === id)?.displayName ?? String(id),
    count: base + (index < remainder ? 1 : 0),
  }))
})

async function loadProjects() {
  loading.value = true
  try {
    const options = await getAnnotationTaskOptions()
    projects.value = options.projects
  } catch (reason) {
    notifyError(reason, t('annotationTasks.optionsFailed'))
  } finally {
    loading.value = false
  }
}

async function loadProjectOptions() {
  form.annotatorIds = []
  form.reviewerIds = []
  form.datasetIds = []
  if (!form.projectId) {
    annotators.value = []
    reviewers.value = []
    datasets.value = []
    return
  }
  loading.value = true
  try {
    const options = await getAnnotationTaskOptions(form.projectId)
    annotators.value = options.annotators
    reviewers.value = options.reviewers
    datasets.value = options.datasets
    const requestedIds = typeof route.query.datasetIds === 'string'
      ? route.query.datasetIds.split(',').map(Number)
      : []
    form.datasetIds = requestedIds.filter((id) => datasets.value.some((item) => item.id === id))
  } catch (reason) {
    notifyError(reason, t('annotationTasks.optionsFailed'))
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.projectId || !form.annotatorIds.length || !form.datasetIds.length) {
    ElMessage.warning(t('annotationTasks.createValidation'))
    return
  }
  if (form.datasetIds.length < form.annotatorIds.length) {
    ElMessage.warning(t('annotationTasks.dataInsufficient'))
    return
  }
  saving.value = true
  try {
    const created = await createAnnotationTasks({
      name: form.name.trim(),
      projectId: form.projectId,
      annotatorIds: form.annotatorIds,
      reviewerIds: form.reviewerIds,
      datasetIds: form.datasetIds,
      randomOrder: form.randomOrder,
    })
    ElMessage.success(t('annotationTasks.created', { count: created.length }))
    if (created.length === 1 && created[0]) {
      await router.replace({ name: 'annotation-task-detail', params: { id: created[0].summary.id } })
    } else {
      await router.replace({ name: 'annotation-tasks' })
    }
  } catch (reason) {
    notifyError(reason, t('annotationTasks.createFailed'))
  } finally {
    saving.value = false
  }
}

onMounted(loadProjects)
</script>

<template>
  <section class="admin-page task-create">
    <PageHeader
      :title="t('annotationTasks.create')"
      :eyebrow="t('annotationTasks.createEyebrow')"
      :description="t('annotationTasks.createDescription')"
    >
      <template #actions>
        <el-button
          :icon="ArrowLeft"
          @click="router.push({ name: 'annotation-tasks' })"
        >
          {{ t('annotationTasks.backToList') }}
        </el-button>
      </template>
    </PageHeader>

    <div
      v-loading="loading"
      class="creation-layout"
    >
      <section class="dataset-picker">
        <header>
          <div>
            <span>{{ t('annotationTasks.stepDataset') }}</span>
            <h2>{{ t('annotationTasks.selectDatasets') }}</h2>
          </div>
          <strong>{{ t('annotationTasks.selectedCount', { count: form.datasetIds.length }) }}</strong>
        </header>
        <div
          v-if="!form.projectId"
          class="picker-empty"
        >
          {{ t('annotationTasks.selectProjectFirst') }}
        </div>
        <el-checkbox-group
          v-else
          v-model="form.datasetIds"
          class="dataset-list"
        >
          <label
            v-for="dataset in datasets"
            :key="dataset.id"
            class="dataset-option"
          >
            <el-checkbox :value="dataset.id" />
            <span class="dataset-option__main">
              <strong>{{ dataset.name }}</strong>
              <small>
                {{ dataset.dataType }} · {{ formatBytes(dataset.sizeBytes) }} ·
                {{ formatDateTime(dataset.createdAt) }}
              </small>
            </span>
          </label>
        </el-checkbox-group>
        <div
          v-if="form.projectId && !datasets.length"
          class="picker-empty"
        >
          {{ t('annotationTasks.noAvailableDatasets') }}
        </div>
      </section>

      <aside class="assignment-panel">
        <span class="section-index">{{ t('annotationTasks.stepAssignment') }}</span>
        <h2>{{ t('annotationTasks.assignment') }}</h2>
        <el-form label-position="top">
          <el-form-item :label="t('annotationTasks.taskBaseName')">
            <el-input
              v-model="form.name"
              maxlength="120"
              show-word-limit
              :placeholder="t('annotationTasks.taskNameHint')"
            />
          </el-form-item>
          <el-form-item
            :label="t('annotationTasks.project')"
            required
          >
            <el-select
              v-model="form.projectId"
              filterable
              :placeholder="t('annotationTasks.selectProject')"
              @change="loadProjectOptions"
            >
              <el-option
                v-for="project in projects"
                :key="project.id"
                :label="project.name"
                :value="project.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            :label="t('annotationTasks.annotators')"
            required
          >
            <el-select
              v-model="form.annotatorIds"
              multiple
              filterable
              collapse-tags
              :disabled="!form.projectId"
              :placeholder="t('annotationTasks.selectAnnotators')"
            >
              <el-option
                v-for="person in annotators"
                :key="person.id"
                :label="`${person.displayName} (${person.username})`"
                :value="person.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('annotationTasks.reviewers')">
            <el-select
              v-model="form.reviewerIds"
              multiple
              filterable
              collapse-tags
              :disabled="!form.projectId"
              :placeholder="t('annotationTasks.selectReviewers')"
            >
              <el-option
                v-for="person in reviewers"
                :key="person.id"
                :label="`${person.displayName} (${person.username})`"
                :value="person.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="form.randomOrder">
              <el-icon><Sort /></el-icon>
              {{ t('annotationTasks.randomOrder') }}
            </el-checkbox>
          </el-form-item>
        </el-form>

        <div
          v-if="allocation.length"
          class="allocation-preview"
        >
          <span>{{ t('annotationTasks.allocationPreview') }}</span>
          <div
            v-for="item in allocation"
            :key="item.id"
          >
            <strong>{{ item.name }}</strong>
            <small>{{ t('annotationTasks.datasetUnit', { count: item.count }) }}</small>
          </div>
        </div>

        <el-button
          class="create-button"
          type="primary"
          :icon="Check"
          :loading="saving"
          :disabled="!form.projectId || !form.annotatorIds.length || !form.datasetIds.length"
          @click="submit"
        >
          {{ t('annotationTasks.createAction') }}
        </el-button>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.creation-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(320px, 0.75fr);
  min-height: 590px;
  margin-top: 24px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
}

.dataset-picker {
  min-width: 0;
  padding: 22px;
  border-right: 1px solid var(--color-border);
}

.dataset-picker > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
}

.dataset-picker h2,
.assignment-panel h2 {
  margin: 4px 0 0;
  font-size: 18px;
  letter-spacing: 0;
}

.dataset-picker header span,
.section-index {
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 650;
}

.dataset-picker header > strong {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.dataset-list {
  display: grid;
  max-height: 505px;
  overflow-y: auto;
  border-top: 1px solid var(--color-border);
}

.dataset-option {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 13px 8px;
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
}

.dataset-option:hover {
  background: #f4f8f9;
}

.dataset-option__main {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.dataset-option__main strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dataset-option__main small {
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.picker-empty {
  display: grid;
  min-height: 320px;
  place-items: center;
  color: var(--color-text-muted);
  font-size: 13px;
}

.assignment-panel {
  padding: 22px;
  background: #f7fafb;
}

.assignment-panel :deep(.el-select) {
  width: 100%;
}

.allocation-preview {
  display: grid;
  gap: 7px;
  margin: 8px 0 20px;
  padding: 14px 0;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}

.allocation-preview > span {
  margin-bottom: 3px;
  color: var(--color-text-secondary);
  font-size: 11px;
}

.allocation-preview > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}

.allocation-preview small {
  color: var(--color-text-muted);
}

.create-button {
  width: 100%;
}

@media (max-width: 900px) {
  .creation-layout {
    grid-template-columns: 1fr;
  }

  .dataset-picker {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .dataset-list {
    max-height: 340px;
  }
}
</style>
