<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Refresh, View } from '@element-plus/icons-vue'
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { getAuditLog, getAuditLogs } from '@/services/admin'
import { getErrorMessage, notifyError } from '@/services/feedback'
import type { OperationLogDetail, OperationLogSummary } from '@/types/admin'
import { formatDateTime } from '@/utils/format'

const { t } = useI18n()

const loading = ref(false)
const error = ref('')
const logs = ref<OperationLogSummary[]>([])
const total = ref(0)
const dateRange = ref<[Date, Date] | null>(null)
const query = reactive({
  page: 1,
  size: 20,
  user: '',
  module: '',
  result: undefined as 'SUCCESS' | 'FAILURE' | undefined,
})
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<OperationLogDetail | null>(null)

function params() {
  return {
    page: query.page,
    size: query.size,
    user: query.user.trim() || undefined,
    module: query.module.trim() || undefined,
    result: query.result,
    startTime: dateRange.value?.[0].toISOString(),
    endTime: dateRange.value?.[1].toISOString(),
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await getAuditLogs(params())
    logs.value = page.items
    total.value = page.total
  } catch (reason) {
    error.value = getErrorMessage(reason, t('audit.loadFailed'))
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  void load()
}

function resetFilters() {
  query.user = ''
  query.module = ''
  query.result = undefined
  dateRange.value = null
  search()
}

async function showDetail(log: OperationLogSummary) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getAuditLog(log.id)
  } catch (reason) {
    detailOpen.value = false
    notifyError(reason, t('audit.detailLoadFailed'))
  } finally {
    detailLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <PageHeader
      :title="t('audit.title')"
      eyebrow="Audit"
      :description="t('audit.description')"
    >
      <template #actions>
        <el-tooltip :content="t('audit.refresh')">
          <el-button
            :icon="Refresh"
            circle
            :aria-label="t('audit.refresh')"
            :loading="loading"
            @click="load"
          />
        </el-tooltip>
      </template>
    </PageHeader>

    <form
      class="filter-bar audit-filter"
      @submit.prevent="search"
    >
      <el-input
        v-model="query.user"
        clearable
        :placeholder="t('audit.operatorPlaceholder')"
      />
      <el-input
        v-model="query.module"
        clearable
        :placeholder="t('audit.modulePlaceholder')"
      />
      <el-select
        v-model="query.result"
        clearable
        :placeholder="t('audit.allResults')"
      >
        <el-option
          :label="t('audit.success')"
          value="SUCCESS"
        />
        <el-option
          :label="t('audit.failure')"
          value="FAILURE"
        />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="datetimerange"
        :range-separator="t('audit.rangeSeparator')"
        :start-placeholder="t('audit.startPlaceholder')"
        :end-placeholder="t('audit.endPlaceholder')"
        :clearable="true"
      />
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
      v-if="loading && !logs.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !logs.length"
      state="error"
      :title="t('audit.loadFailed')"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!logs.length"
      state="empty"
      :title="t('audit.notFound')"
      :description="t('audit.notFoundDesc')"
    />
    <template v-else>
      <div class="table-shell">
        <el-table
          v-loading="loading"
          :data="logs"
          row-key="id"
        >
          <el-table-column
            :label="t('audit.time')"
            min-width="165"
            fixed="left"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.occurredAt) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="operatorName"
            :label="t('audit.operator')"
            min-width="130"
          >
            <template #default="{ row }">
              {{ row.operatorName || t('audit.system') }}
            </template>
          </el-table-column>
          <el-table-column
            prop="module"
            :label="t('audit.module')"
            min-width="110"
          />
          <el-table-column
            prop="action"
            :label="t('audit.action')"
            min-width="145"
          />
          <el-table-column
            :label="t('audit.target')"
            min-width="180"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.targetType || '—' }}
              <span
                v-if="row.targetId"
                class="mono"
              > #{{ row.targetId }}</span>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('audit.result')"
            width="90"
          >
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.result === 'SUCCESS' ? 'success' : 'danger'"
                effect="plain"
              >
                {{ row.result === 'SUCCESS' ? t('audit.success') : t('audit.failure') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('audit.duration')"
            width="90"
            align="right"
          >
            <template #default="{ row }">
              {{ row.durationMs == null ? '—' : `${row.durationMs} ms` }}
            </template>
          </el-table-column>
          <el-table-column
            prop="requestId"
            :label="t('audit.requestId')"
            min-width="210"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span class="mono">{{ row.requestId }}</span>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('common.operation')"
            width="72"
            fixed="right"
          >
            <template #default="{ row }">
              <el-tooltip :content="t('audit.viewDetail')">
                <el-button
                  v-permission="'audit:log:detail'"
                  :icon="View"
                  circle
                  text
                  :aria-label="t('audit.viewDetailAria')"
                  @click="showDetail(row)"
                />
              </el-tooltip>
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
      v-model="detailOpen"
      :title="t('audit.drawerTitle')"
      size="520px"
    >
      <el-skeleton
        v-if="detailLoading"
        :rows="8"
        animated
      />
      <div
        v-else-if="detail"
        class="audit-detail"
      >
        <div class="audit-result">
          <el-tag
            :type="detail.result === 'SUCCESS' ? 'success' : 'danger'"
            effect="plain"
          >
            {{ detail.result === 'SUCCESS' ? t('audit.operationSuccess') : t('audit.operationFailure') }}
          </el-tag>
          <span>{{ formatDateTime(detail.occurredAt) }}</span>
        </div>
        <dl>
          <dt>{{ t('audit.operator') }}</dt>
          <dd>{{ detail.operatorName || t('audit.system') }}{{ detail.operatorId ? `（${detail.operatorId}）` : '' }}</dd>
          <dt>{{ t('audit.moduleAction') }}</dt>
          <dd>{{ detail.module }} / {{ detail.action }}</dd>
          <dt>{{ t('audit.target') }}</dt>
          <dd>{{ detail.targetType || '—' }}{{ detail.targetId ? ` #${detail.targetId}` : '' }}</dd>
          <dt>{{ t('audit.requestId') }}</dt>
          <dd class="mono">
            {{ detail.requestId }}
          </dd>
          <dt>{{ t('audit.ip') }}</dt>
          <dd>{{ detail.ipAddress || '—' }}</dd>
          <dt>{{ t('audit.duration') }}</dt>
          <dd>{{ detail.durationMs == null ? '—' : `${detail.durationMs} ms` }}</dd>
          <dt>{{ t('audit.userAgent') }}</dt>
          <dd class="break-text">
            {{ detail.userAgent || '—' }}
          </dd>
          <dt>{{ t('audit.errorSummary') }}</dt>
          <dd>{{ detail.errorSummary || '—' }}</dd>
        </dl>
        <section v-if="detail.details">
          <h3>{{ t('audit.additional') }}</h3>
          <pre>{{ detail.details }}</pre>
        </section>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.audit-filter .el-date-editor {
  width: 360px;
}

.audit-result {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--color-border);
}

.audit-result span {
  color: var(--color-text-muted);
  font-size: 11px;
}

.audit-detail dl {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  margin: 18px 0 0;
}

.audit-detail dt,
.audit-detail dd {
  margin: 0;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 12px;
}

.audit-detail dt {
  color: var(--color-text-muted);
}

.break-text {
  overflow-wrap: anywhere;
}

.audit-detail section {
  margin-top: 24px;
}

.audit-detail h3 {
  margin: 0 0 10px;
  font-size: 13px;
}

.audit-detail pre {
  max-height: 280px;
  margin: 0;
  overflow: auto;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 5px;
  background: #f4f6f7;
  font-family: var(--font-mono);
  font-size: 10px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
