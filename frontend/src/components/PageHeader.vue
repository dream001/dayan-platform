<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

withDefaults(defineProps<{
  title: string
  description?: string
  eyebrow?: string
}>(), {
  description: '',
  eyebrow: '',
})
</script>

<template>
  <header class="page-header">
    <div>
      <p>{{ eyebrow || t('common.manage') }}</p>
      <h1>{{ title }}</h1>
      <span v-if="description">{{ description }}</span>
    </div>
    <div
      v-if="$slots.actions"
      class="page-header__actions"
    >
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 2px 0 20px;
  border-bottom: 1px solid var(--color-border-strong);
}

.page-header p {
  margin: 0 0 6px;
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 650;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.page-header h1 {
  margin: 0;
  font-size: clamp(24px, 3vw, 31px);
  font-weight: 650;
  letter-spacing: -0.035em;
}

.page-header span {
  display: block;
  margin-top: 7px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.page-header__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 620px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .page-header__actions :deep(.el-button:not(.is-circle)) {
    flex: 1;
  }
}
</style>
