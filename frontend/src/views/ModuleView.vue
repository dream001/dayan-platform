<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import StatePanel from '@/components/StatePanel.vue'
import { translateMenu } from '@/i18n'

const { t } = useI18n()
const props = defineProps<{
  title: string
  code?: string
}>()

const resolvedTitle = computed(() => translateMenu(props.code, props.title))
</script>

<template>
  <section
    class="module-view"
    :aria-labelledby="`module-${resolvedTitle}`"
  >
    <header>
      <p>{{ t('module.eyebrow') }}</p>
      <h1 :id="`module-${resolvedTitle}`">
        {{ resolvedTitle }}
      </h1>
    </header>
    <StatePanel
      state="empty"
      :title="t('module.readyTitle')"
      :description="t('module.readyDesc')"
    />
  </section>
</template>

<style scoped>
.module-view {
  width: min(1180px, 100%);
  margin: 0 auto;
}

.module-view header {
  padding: 4px 0 25px;
  border-bottom: 1px solid var(--color-border-strong);
}

.module-view header p {
  margin: 0 0 7px;
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.module-view h1 {
  margin: 0;
  font-size: clamp(25px, 3vw, 32px);
  font-weight: 650;
  letter-spacing: -0.035em;
}
</style>
