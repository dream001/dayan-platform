<script setup lang="ts">
import { Refresh, View } from '@element-plus/icons-vue'
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { getAuditLog, getAuditLogs } from '@/services/admin'
import { getErrorMessage, notifyError } from '@/services/feedback'
import type { OperationLogDetail, OperationLogSummary } from '@/types/admin'
import { formatDateTime } from '@/utils/format'

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
    error.value = getErrorMessage(reason, '操作日志加载失败')
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
    notifyError(reason, '日志详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <PageHeader
      title="操作日志"
      eyebrow="Audit"
      description="按操作者、模块、结果和时间范围追踪管理操作。"
    >
      <template #actions>
        <el-tooltip content="刷新日志">
          <el-button
            :icon="Refresh"
            circle
            aria-label="刷新日志"
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
        placeholder="操作者"
      />
      <el-input
        v-model="query.module"
        clearable
        placeholder="模块"
      />
      <el-select
        v-model="query.result"
        clearable
        placeholder="全部结果"
      >
        <el-option
          label="成功"
          value="SUCCESS"
        />
        <el-option
          label="失败"
          value="FAILURE"
        />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        :clearable="true"
      />
      <el-button
        type="primary"
        native-type="submit"
      >
        查询
      </el-button>
      <el-button @click="resetFilters">
        重置
      </el-button>
    </form>

    <StatePanel
      v-if="loading && !logs.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !logs.length"
      state="error"
      title="操作日志加载失败"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!logs.length"
      state="empty"
      title="未找到操作日志"
      description="调整筛选条件后重新查询。"
    />
    <template v-else>
      <div class="table-shell">
        <el-table
          v-loading="loading"
          :data="logs"
          row-key="id"
        >
          <el-table-column
            label="时间"
            min-width="165"
            fixed="left"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.occurredAt) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="operatorName"
            label="操作者"
            min-width="130"
          >
            <template #default="{ row }">
              {{ row.operatorName || '系统' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="module"
            label="模块"
            min-width="110"
          />
          <el-table-column
            prop="action"
            label="动作"
            min-width="145"
          />
          <el-table-column
            label="目标"
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
            label="结果"
            width="90"
          >
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.result === 'SUCCESS' ? 'success' : 'danger'"
                effect="plain"
              >
                {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="耗时"
            width="90"
            align="right"
          >
            <template #default="{ row }">
              {{ row.durationMs == null ? '—' : `${row.durationMs} ms` }}
            </template>
          </el-table-column>
          <el-table-column
            prop="requestId"
            label="请求标识"
            min-width="210"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span class="mono">{{ row.requestId }}</span>
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="72"
            fixed="right"
          >
            <template #default="{ row }">
              <el-tooltip content="查看详情">
                <el-button
                  v-permission="'audit:log:detail'"
                  :icon="View"
                  circle
                  text
                  aria-label="查看日志详情"
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
      title="操作详情"
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
            {{ detail.result === 'SUCCESS' ? '操作成功' : '操作失败' }}
          </el-tag>
          <span>{{ formatDateTime(detail.occurredAt) }}</span>
        </div>
        <dl>
          <dt>操作者</dt>
          <dd>{{ detail.operatorName || '系统' }}{{ detail.operatorId ? `（${detail.operatorId}）` : '' }}</dd>
          <dt>模块 / 动作</dt>
          <dd>{{ detail.module }} / {{ detail.action }}</dd>
          <dt>目标</dt>
          <dd>{{ detail.targetType || '—' }}{{ detail.targetId ? ` #${detail.targetId}` : '' }}</dd>
          <dt>请求标识</dt>
          <dd class="mono">
            {{ detail.requestId }}
          </dd>
          <dt>IP 地址</dt>
          <dd>{{ detail.ipAddress || '—' }}</dd>
          <dt>耗时</dt>
          <dd>{{ detail.durationMs == null ? '—' : `${detail.durationMs} ms` }}</dd>
          <dt>User-Agent</dt>
          <dd class="break-text">
            {{ detail.userAgent || '—' }}
          </dd>
          <dt>错误摘要</dt>
          <dd>{{ detail.errorSummary || '—' }}</dd>
        </dl>
        <section v-if="detail.details">
          <h3>附加信息</h3>
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
