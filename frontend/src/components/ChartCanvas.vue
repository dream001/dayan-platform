<script setup lang="ts">
import {
  BarChart,
  GraphChart,
  HeatmapChart,
  LineChart,
  PieChart,
  SankeyChart,
  SunburstChart,
} from 'echarts/charts'
import {
  AriaComponent,
  CalendarComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  VisualMapComponent,
} from 'echarts/components'
import { init, use, type ECharts, type EChartsCoreOption } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

use([
  AriaComponent,
  BarChart,
  CalendarComponent,
  CanvasRenderer,
  GraphChart,
  GridComponent,
  HeatmapChart,
  LegendComponent,
  LineChart,
  PieChart,
  SankeyChart,
  SunburstChart,
  TooltipComponent,
  VisualMapComponent,
])

const props = defineProps<{
  option: EChartsCoreOption
  height?: number
}>()

const root = ref<HTMLElement>()
let chart: ECharts | null = null
let observer: ResizeObserver | null = null

function render() {
  if (!chart) return
  chart.setOption(props.option, { notMerge: true })
}

onMounted(async () => {
  await nextTick()
  if (!root.value) return
  chart = init(root.value, undefined, { renderer: 'canvas' })
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(root.value)
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div
    ref="root"
    class="chart-canvas"
    :style="height ? { '--chart-height': `${height}px` } : undefined"
    role="img"
  />
</template>

<style scoped>
.chart-canvas {
  width: 100%;
  height: var(--chart-height, 100%);
  min-height: var(--chart-height, 480px);
}

@media (max-width: 700px) {
  .chart-canvas {
    min-height: 420px;
  }
}
</style>
