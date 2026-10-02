<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  status: '401' | '403' | '404'
}>()

const { t } = useI18n()
const router = useRouter()
const content = computed(() => ({
  '401': {
    eyebrow: t('status.401.eyebrow'),
    title: t('status.401.title'),
    description: t('status.401.description'),
    action: t('status.401.action'),
  },
  '403': {
    eyebrow: t('status.403.eyebrow'),
    title: t('status.403.title'),
    description: t('status.403.description'),
    action: t('status.403.action'),
  },
  '404': {
    eyebrow: t('status.404.eyebrow'),
    title: t('status.404.title'),
    description: t('status.404.description'),
    action: t('status.404.action'),
  },
})[props.status])

function handleAction() {
  if (props.status === '401') {
    void router.replace({ name: 'login' })
  } else if (props.status === '403' && window.history.length > 1) {
    router.back()
  } else {
    void router.replace({ name: 'workspace' })
  }
}
</script>

<template>
  <main class="status-page">
    <div
      class="status-page__index"
      aria-hidden="true"
    >
      {{ status }}
    </div>
    <section>
      <p>{{ content.eyebrow }}</p>
      <h1>{{ content.title }}</h1>
      <span>{{ content.description }}</span>
      <button
        type="button"
        @click="handleAction"
      >
        {{ content.action }}
      </button>
    </section>
    <div
      class="status-page__axis"
      aria-hidden="true"
    />
  </main>
</template>

<style scoped>
.status-page {
  position: relative;
  display: grid;
  min-height: 100svh;
  place-items: center;
  overflow: hidden;
  padding: 32px;
  background: var(--color-canvas);
}

.status-page section {
  position: relative;
  z-index: 1;
  width: min(420px, 100%);
}

.status-page section p {
  margin: 0 0 12px;
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.16em;
}

.status-page h1 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: clamp(30px, 6vw, 48px);
  font-weight: 620;
  letter-spacing: -0.05em;
}

.status-page section span {
  display: block;
  margin-top: 12px;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.status-page button {
  margin-top: 28px;
  padding: 9px 15px;
  cursor: pointer;
  border: 1px solid var(--color-border-strong);
  border-radius: 5px;
  color: var(--color-text-primary);
  background: var(--color-surface);
}

.status-page__index {
  position: absolute;
  right: -0.05em;
  bottom: -0.27em;
  color: #edf1f3;
  font-family: var(--font-mono);
  font-size: min(36vw, 430px);
  font-weight: 700;
  letter-spacing: -0.14em;
  line-height: 1;
  user-select: none;
}

.status-page__axis {
  position: absolute;
  top: 0;
  left: 14%;
  width: 1px;
  height: 100%;
  background: var(--color-border);
}

.status-page__axis::before {
  position: absolute;
  top: 50%;
  left: -3px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-accent);
  content: '';
}
</style>
