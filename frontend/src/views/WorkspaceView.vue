<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { getDashboardStatistics } from '@/services/admin'
import { getErrorMessage } from '@/services/feedback'
import type { DashboardStatistics } from '@/types/admin'
import { formatBytes, formatDateTime } from '@/utils/format'

const { t } = useI18n()
const loading = ref(true)
const error = ref('')
const statistics = ref<DashboardStatistics | null>(null)

const metrics = computed(() => {
  const value = statistics.value
  if (!value) return []
  return [
    {
      label: t('workspace.totalUsers'),
      value: value.totalUsers.toLocaleString(),
      note: t('workspace.enabledNote', { count: value.enabledUsers }),
    },
    {
      label: t('workspace.totalFiles'),
      value: value.totalFiles.toLocaleString(),
      note: formatBytes(value.totalFileSizeBytes),
    },
    {
      label: t('workspace.recentOperations'),
      value: value.recentOperationCount.toLocaleString(),
      note: t('workspace.auditRecords'),
    },
  ]
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    statistics.value = await getDashboardStatistics()
  } catch (reason) {
    error.value = getErrorMessage(reason, t('workspace.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page workspace-view">
    <PageHeader
      :title="t('workspace.title')"
      :eyebrow="t('workspace.eyebrow')"
      :description="t('workspace.description')"
    >
      <template #actions>
        <el-tooltip :content="t('workspace.refresh')">
          <el-button
            :icon="Refresh"
            circle
            :aria-label="t('workspace.refresh')"
            :loading="loading"
            @click="load"
          />
        </el-tooltip>
      </template>
    </PageHeader>

    <StatePanel
      v-if="loading"
      state="loading"
    />
    <StatePanel
      v-else-if="error"
      state="error"
      :title="t('workspace.loadFailed')"
      :description="error"
      @retry="load"
    />
    <template v-else-if="statistics">
      <div class="metric-ledger">
        <div
          v-for="metric in metrics"
          :key="metric.label"
          class="metric"
        >
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.note }}</small>
        </div>
        <div class="metric metric--timestamp">
          <span>{{ t('workspace.generatedAt') }}</span>
          <strong>{{ formatDateTime(statistics.generatedAt) }}</strong>
          <small>{{ t('workspace.serverGenerated') }}</small>
        </div>
      </div>

      <section class="operations">
        <header>
          <div>
            <p>{{ t('workspace.recentActivity') }}</p>
            <h2>{{ t('workspace.operationRecords') }}</h2>
          </div>
          <RouterLink to="/audit/logs">
            {{ t('workspace.viewAll') }}
          </RouterLink>
        </header>
        <div
          v-if="statistics.recentOperations.length"
          class="table-shell"
        >
          <el-table :data="statistics.recentOperations">
            <el-table-column
              prop="operatorName"
              :label="t('workspace.operator')"
              min-width="120"
            >
              <template #default="{ row }">
                {{ row.operatorName || t('workspace.system') }}
              </template>
            </el-table-column>
            <el-table-column
              prop="module"
              :label="t('workspace.module')"
              min-width="105"
            />
            <el-table-column
              prop="action"
              :label="t('workspace.action')"
              min-width="130"
            />
            <el-table-column
              :label="t('workspace.result')"
              width="92"
            >
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="row.result === 'SUCCESS' ? 'success' : 'danger'"
                  effect="plain"
                >
                  {{ row.result === 'SUCCESS' ? t('workspace.success') : t('workspace.failure') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              :label="t('workspace.time')"
              min-width="165"
            >
              <template #default="{ row }">
                {{ formatDateTime(row.occurredAt) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
        <StatePanel
          v-else
          state="empty"
          :title="t('workspace.noRecent')"
          :description="t('workspace.noRecentDesc')"
        />
      </section>
    </template>
  </section>
</template>

<style scoped>
.metric-ledger {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) minmax(210px, 1.3fr);
  margin-top: 26px;
  border-block: 1px solid var(--color-border-strong);
}

.metric {
  display: flex;
  min-height: 132px;
  flex-direction: column;
  justify-content: center;
  padding: 20px 24px;
  border-right: 1px solid var(--color-border);
}

.metric:last-child {
  border-right: 0;
}

.metric span {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.metric strong {
  margin: 9px 0 4px;
  font-family: var(--font-mono);
  font-size: clamp(24px, 3vw, 34px);
  font-weight: 620;
  letter-spacing: -0.05em;
}

.metric small {
  color: var(--color-text-muted);
  font-size: 11px;
}

.metric--timestamp strong {
  font-size: 16px;
  letter-spacing: -0.02em;
}

.operations {
  margin-top: 36px;
}

.operations > header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  padding-bottom: 14px;
}

.operations header p {
  margin: 0 0 4px;
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.operations h2 {
  margin: 0;
  font-size: 17px;
}

.operations a {
  color: var(--color-accent);
  font-size: 12px;
  text-decoration: none;
}

@media (max-width: 900px) {
  .metric-ledger {
    grid-template-columns: repeat(2, 1fr);
  }

  .metric:nth-child(2) {
    border-right: 0;
  }

  .metric:nth-child(-n + 2) {
    border-bottom: 1px solid var(--color-border);
  }
}

@media (max-width: 520px) {
  .metric-ledger {
    grid-template-columns: 1fr;
  }

  .metric {
    min-height: 104px;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .metric:last-child {
    border-bottom: 0;
  }
}
</style>
