<script setup lang="ts">
import { Compass } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale, type AppLocale } from '@/i18n'

const { locale, t } = useI18n()

const options = [
  { value: 'zh-CN', labelKey: 'language.zhCN' },
  { value: 'en-US', labelKey: 'language.enUS' },
  { value: 'ja-JP', labelKey: 'language.jaJP' },
] as const satisfies ReadonlyArray<{ value: AppLocale; labelKey: string }>

const currentLabel = computed(() => {
  const active = options.find((option) => option.value === locale.value)
  return active ? t(active.labelKey) : ''
})

function handleCommand(value: AppLocale) {
  setLocale(value)
}
</script>

<template>
  <el-dropdown
    trigger="click"
    @command="handleCommand"
  >
    <button
      class="language-switcher"
      type="button"
      :aria-label="t('language.label')"
    >
      <el-icon :size="16">
        <Compass />
      </el-icon>
      <span class="language-switcher__label">{{ currentLabel }}</span>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="option in options"
          :key="option.value"
          :command="option.value"
        >
          {{ t(option.labelKey) }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.language-switcher {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 9px;
  cursor: pointer;
  border: 0;
  border-radius: 5px;
  color: inherit;
  background: transparent;
  font-size: 12px;
  transition: background-color 140ms ease;
}

.language-switcher:hover,
.language-switcher:focus-visible {
  background: var(--color-hover, rgb(255 255 255 / 7%));
}

.language-switcher__label {
  white-space: nowrap;
}

@media (max-width: 760px) {
  .language-switcher__label {
    display: none;
  }
}
</style>
