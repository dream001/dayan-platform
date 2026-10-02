<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import OrganizationChartNode from '@/components/OrganizationChartNode.vue'
import type { DepartmentNode } from '@/types/admin'

defineProps<{
  departments: DepartmentNode[]
}>()

const emit = defineEmits<{
  createChild: [node: DepartmentNode]
  edit: [node: DepartmentNode]
}>()

const { t } = useI18n()
</script>

<template>
  <div
    class="organization-chart"
    role="region"
    :aria-label="t('departments.chartView')"
  >
    <div class="chart-stage">
      <ul
        v-for="department in departments"
        :key="department.id"
        class="org-tree-root"
      >
        <OrganizationChartNode
          :node="department"
          root
          @create-child="emit('createChild', $event)"
          @edit="emit('edit', $event)"
        />
      </ul>
    </div>
  </div>
</template>

<style scoped>
.organization-chart {
  min-height: 430px;
  margin-top: 22px;
  overflow: auto;
  border-block: 1px solid var(--color-border);
  background: #f4f7f8;
  scrollbar-color: #b9c7cd transparent;
}

.chart-stage {
  display: flex;
  width: max-content;
  min-width: 100%;
  align-items: flex-start;
  justify-content: center;
  gap: 56px;
  padding: 36px 28px 52px;
}

.org-tree-root {
  display: flex;
  flex: 0 0 auto;
  justify-content: center;
  margin: 0;
  padding: 0;
}

@media (max-width: 700px) {
  .organization-chart {
    min-height: 360px;
    margin-inline: -18px;
  }

  .chart-stage {
    justify-content: flex-start;
    gap: 32px;
    padding: 28px 18px 42px;
  }
}
</style>
