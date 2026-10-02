<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import { getDashboardStatistics } from '@/services/admin'
import { getErrorMessage } from '@/services/feedback'
import type { DashboardStatistics } from '@/types/admin'
import { formatBytes, formatDateTime } from '@/utils/format'

const loading = ref(true)
const error = ref('')
const statistics = ref<DashboardStatistics | null>(null)

const metrics = computed(() => {
  const value = statistics.value
  if (!value) return []
  return [
    { label: '用户总数', value: value.totalUsers.toLocaleString(), note: `${value.enabledUsers} 个已启用` },
    { label: '文件总数', value: value.totalFiles.toLocaleString(), note: formatBytes(value.totalFileSizeBytes) },
    { label: '近期操作', value: value.recentOperationCount.toLocaleString(), note: '审计记录' },
  ]
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    statistics.value = await getDashboardStatistics()
  } catch (reason) {
    error.value = getErrorMessage(reason, '工作台数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page workspace-view">
    <PageHeader
      title="工作台"
      eyebrow="Workspace"
      description="用户、文件与操作数据均来自当前服务。"
    >
      <template #actions>
        <el-tooltip content="刷新统计">
          <el-button
            :icon="Refresh"
            circle
            aria-label="刷新统计"
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
      title="工作台加载失败"
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
          <span>统计时间</span>
          <strong>{{ formatDateTime(statistics.generatedAt) }}</strong>
          <small>服务端生成</small>
        </div>
      </div>

      <section class="operations">
        <header>
          <div>
            <p>最近活动</p>
            <h2>操作记录</h2>
          </div>
          <RouterLink to="/audit/logs">
            查看全部
          </RouterLink>
        </header>
        <div
          v-if="statistics.recentOperations.length"
          class="table-shell"
        >
          <el-table :data="statistics.recentOperations">
            <el-table-column
              prop="operatorName"
              label="操作者"
              min-width="120"
            >
              <template #default="{ row }">
                {{ row.operatorName || '系统' }}
              </template>
            </el-table-column>
            <el-table-column
              prop="module"
              label="模块"
              min-width="105"
            />
            <el-table-column
              prop="action"
              label="动作"
              min-width="130"
            />
            <el-table-column
              label="结果"
              width="92"
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
              label="时间"
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
          title="暂无近期操作"
          description="产生登录或管理操作后，记录会显示在这里。"
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
