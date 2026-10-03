<script setup lang="ts">
import { Cpu, RefreshRight, WarningFilled } from '@element-plus/icons-vue'
import { ElIcon, ElSkeleton, ElSkeletonItem } from 'element-plus'
import 'element-plus/theme-chalk/el-icon.css'
import 'element-plus/theme-chalk/el-skeleton.css'
import 'element-plus/theme-chalk/el-skeleton-item.css'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

withDefaults(defineProps<{
  state: 'loading' | 'empty' | 'error'
  title?: string
  description?: string
}>(), {
  title: '',
  description: '',
})

defineEmits<{
  retry: []
}>()
</script>

<template>
  <section
    class="state-panel"
    :class="`state-panel--${state}`"
    :aria-busy="state === 'loading'"
    :aria-live="state === 'error' ? 'assertive' : 'polite'"
  >
    <template v-if="state === 'loading'">
      <div
        class="state-panel__visual state-panel__visual--loading"
        aria-hidden="true"
      >
        <span class="state-panel__orbit">
          <i />
          <i />
          <i />
        </span>
        <el-icon
          class="state-panel__core"
          :size="28"
        >
          <Cpu />
        </el-icon>
      </div>
      <el-skeleton
        animated
        :aria-label="t('state.loading')"
      >
        <template #template>
          <el-skeleton-item
            variant="text"
            class="state-panel__skeleton-title"
          />
          <el-skeleton-item
            variant="text"
            class="state-panel__skeleton-line"
          />
          <el-skeleton-item
            variant="text"
            class="state-panel__skeleton-line state-panel__skeleton-line--short"
          />
        </template>
      </el-skeleton>
    </template>

    <template v-else>
      <div
        class="state-panel__visual"
        :class="`state-panel__visual--${state}`"
        aria-hidden="true"
      >
        <span
          v-if="state === 'empty'"
          class="state-panel__orbit"
        >
          <i />
          <i />
          <i />
        </span>
        <el-icon
          class="state-panel__core"
          :size="state === 'empty' ? 28 : 26"
        >
          <Cpu v-if="state === 'empty'" />
          <WarningFilled v-else />
        </el-icon>
      </div>
      <h2>{{ title || (state === 'empty' ? t('state.empty') : t('state.error')) }}</h2>
      <p v-if="description">
        {{ description }}
      </p>
      <button
        v-if="state === 'error'"
        type="button"
        @click="$emit('retry')"
      >
        <el-icon :size="15">
          <RefreshRight />
        </el-icon>
        {{ t('state.retry') }}
      </button>
      <slot />
    </template>
  </section>
</template>

<style scoped>
.state-panel {
  position: relative;
  display: flex;
  min-height: clamp(260px, 34vh, 340px);
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 42px 24px;
  color: var(--color-text-secondary);
  text-align: center;
}

.state-panel h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 18px;
  font-weight: 650;
  letter-spacing: 0;
}

.state-panel p {
  max-width: 500px;
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.65;
}

.state-panel button {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  gap: 7px;
  margin-top: 18px;
  padding: 7px 14px;
  cursor: pointer;
  border: 1px solid var(--color-border-strong);
  border-radius: 5px;
  color: var(--color-text-primary);
  background: var(--color-surface);
  transition: border-color 150ms ease, color 150ms ease, transform 150ms ease;
}

.state-panel button:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  transform: translateY(-1px);
}

.state-panel__visual {
  position: relative;
  display: grid;
  width: 112px;
  height: 112px;
  margin-bottom: 25px;
  place-items: center;
}

.state-panel__visual::before,
.state-panel__visual::after {
  position: absolute;
  background: var(--color-border);
  content: '';
}

.state-panel__visual::before {
  top: 55px;
  left: 0;
  width: 112px;
  height: 1px;
}

.state-panel__visual::after {
  top: 0;
  left: 55px;
  width: 1px;
  height: 112px;
}

.state-panel__orbit {
  position: absolute;
  inset: 10px;
  z-index: 1;
  border: 1px solid #b9cbce;
  border-radius: 50%;
  animation: state-orbit 9s linear infinite;
}

.state-panel__orbit::before {
  position: absolute;
  inset: 14px;
  border: 1px dashed #d3dfe1;
  border-radius: 50%;
  content: '';
}

.state-panel__orbit i {
  position: absolute;
  width: 8px;
  height: 8px;
  border: 2px solid #f7f9f9;
  border-radius: 50%;
  background: #53c9d0;
  box-shadow: 0 0 0 3px rgb(83 201 208 / 12%);
}

.state-panel__orbit i:first-child {
  top: 5px;
  left: 16px;
}

.state-panel__orbit i:nth-child(2) {
  top: 38px;
  right: -4px;
}

.state-panel__orbit i:last-child {
  bottom: 1px;
  left: 27px;
}

.state-panel__core {
  z-index: 2;
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border: 1px solid #34545b;
  border-radius: 7px;
  color: #67d6da;
  background: #13252a;
  box-shadow:
    0 0 0 6px rgb(83 201 208 / 6%),
    0 12px 30px rgb(23 66 73 / 12%);
}

.state-panel__visual--loading .state-panel__core {
  animation: state-core-pulse 1.8s ease-in-out infinite;
}

.state-panel__visual--error {
  width: 88px;
  height: 88px;
}

.state-panel__visual--error::before {
  top: 43px;
  width: 88px;
}

.state-panel__visual--error::after {
  left: 43px;
  height: 88px;
}

.state-panel__visual--error .state-panel__core {
  border-color: color-mix(in srgb, var(--color-danger) 42%, var(--color-border));
  color: var(--color-danger);
  background: color-mix(in srgb, var(--color-danger) 7%, var(--color-surface));
  box-shadow: 0 0 0 6px color-mix(in srgb, var(--color-danger) 5%, transparent);
}

.state-panel--loading {
  width: min(620px, 100%);
}

.state-panel--loading :deep(.el-skeleton) {
  width: min(320px, 86%);
}

.state-panel__skeleton-title {
  width: 38%;
  height: 18px;
  margin: 0 auto 18px;
}

.state-panel__skeleton-line {
  display: block;
  width: 76%;
  margin-top: 10px;
  margin-inline: auto;
}

.state-panel__skeleton-line--short {
  width: 54%;
}

@keyframes state-orbit {
  to {
    transform: rotate(360deg);
  }
}

@keyframes state-core-pulse {
  0%,
  100% {
    box-shadow:
      0 0 0 5px rgb(83 201 208 / 5%),
      0 12px 30px rgb(23 66 73 / 10%);
  }

  50% {
    box-shadow:
      0 0 0 10px rgb(83 201 208 / 9%),
      0 12px 30px rgb(23 66 73 / 16%);
  }
}

@media (max-width: 700px) {
  .state-panel {
    min-height: 240px;
    padding: 36px 18px;
  }

  .state-panel__visual {
    transform: scale(0.9);
  }
}

@media (prefers-reduced-motion: reduce) {
  .state-panel__orbit,
  .state-panel__visual--loading .state-panel__core {
    animation: none;
  }
}
</style>
