<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import OrganizationChartNode from '@/components/OrganizationChartNode.vue'
import type { DepartmentNode } from '@/types/admin'

const props = defineProps<{
  departments: DepartmentNode[]
}>()

const emit = defineEmits<{
  createChild: [node: DepartmentNode]
  delete: [node: DepartmentNode]
  edit: [node: DepartmentNode]
}>()

const { t } = useI18n()
const viewport = ref<HTMLElement>()
const dragging = ref(false)
let pointerStartX = 0
let scrollStartX = 0
let resizeObserver: ResizeObserver | undefined

async function centerChart() {
  await nextTick()
  requestAnimationFrame(() => {
    const element = viewport.value
    if (!element) return
    element.scrollLeft = Math.max(0, (element.scrollWidth - element.clientWidth) / 2)
  })
}

onMounted(() => {
  void centerChart()
  if (viewport.value) {
    resizeObserver = new ResizeObserver(centerChart)
    resizeObserver.observe(viewport.value)
  }
})
onBeforeUnmount(() => resizeObserver?.disconnect())
watch(() => props.departments, centerChart)

function startPan(event: PointerEvent) {
  const element = viewport.value
  const target = event.target as HTMLElement
  if (!element || event.pointerType === 'touch' || event.button !== 0) return
  if (target.closest('button, a, input, label')) return
  if (element.scrollWidth <= element.clientWidth) return

  dragging.value = true
  pointerStartX = event.clientX
  scrollStartX = element.scrollLeft
  element.setPointerCapture(event.pointerId)
}

function panChart(event: PointerEvent) {
  if (!dragging.value || !viewport.value) return
  viewport.value.scrollLeft = scrollStartX - (event.clientX - pointerStartX)
}

function stopPan(event: PointerEvent) {
  const element = viewport.value
  if (!dragging.value || !element) return
  dragging.value = false
  if (element.hasPointerCapture(event.pointerId)) {
    element.releasePointerCapture(event.pointerId)
  }
}
</script>

<template>
  <div
    ref="viewport"
    class="organization-chart"
    :class="{ 'is-dragging': dragging }"
    role="region"
    :aria-label="t('departments.chartView')"
    @pointerdown="startPan"
    @pointermove="panChart"
    @pointerup="stopPan"
    @pointercancel="stopPan"
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
          @delete="emit('delete', $event)"
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
  overscroll-behavior-inline: contain;
  border-block: 1px solid var(--color-border);
  background: #f4f7f8;
  cursor: grab;
  scrollbar-color: #b9c7cd transparent;
  touch-action: pan-x pan-y;
  -webkit-overflow-scrolling: touch;
}

.organization-chart.is-dragging {
  cursor: grabbing;
  user-select: none;
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
