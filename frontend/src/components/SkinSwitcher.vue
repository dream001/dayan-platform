<script setup lang="ts">
import { Brush, Check } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { useLayoutStore, type AppSkin } from '@/stores/layout'

const { t } = useI18n()
const layoutStore = useLayoutStore()
const { skin } = storeToRefs(layoutStore)

const skins: Array<{ value: AppSkin, colors: [string, string, string] }> = [
  { value: 'titanium', colors: ['#edf3f2', '#087f83', '#a8bd36'] },
  { value: 'orbit', colors: ['#0d1725', '#66b4ff', '#38d3cf'] },
  { value: 'forge', colors: ['#202523', '#e09b48', '#55c5bb'] },
]

function selectSkin(value: string | number | object) {
  layoutStore.setSkin(value as AppSkin)
}
</script>

<template>
  <el-dropdown
    trigger="click"
    placement="bottom-end"
    @command="selectSkin"
  >
    <el-button
      class="skin-switcher__trigger"
      :icon="Brush"
      circle
      :aria-label="t('shell.skinSwitcher')"
      :title="t('shell.skinSwitcher')"
    />
    <template #dropdown>
      <el-dropdown-menu class="skin-menu">
        <el-dropdown-item
          v-for="item in skins"
          :key="item.value"
          :command="item.value"
          :class="{ 'skin-menu__item--active': skin === item.value }"
        >
          <span
            class="skin-menu__swatch"
            aria-hidden="true"
          >
            <i
              v-for="color in item.colors"
              :key="color"
              :style="{ backgroundColor: color }"
            />
          </span>
          <span>{{ t(`shell.skins.${item.value}`) }}</span>
          <el-icon
            v-if="skin === item.value"
            class="skin-menu__check"
          >
            <Check />
          </el-icon>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.skin-switcher__trigger {
  width: 34px;
  height: 34px;
  border: 0;
  color: var(--color-text-secondary);
  background: transparent;
}

.skin-switcher__trigger:hover,
.skin-switcher__trigger:focus-visible {
  border-color: transparent;
  color: var(--color-text-primary);
  background: var(--color-hover);
}

.skin-menu__swatch {
  display: inline-flex;
  width: 38px;
  height: 18px;
  overflow: hidden;
  border: 1px solid var(--color-border-strong);
  border-radius: 4px;
}

.skin-menu__swatch i {
  flex: 1;
}

.skin-menu__check {
  margin-left: auto;
  color: var(--color-accent);
}

:global(.skin-menu .el-dropdown-menu__item) {
  min-width: 190px;
  gap: 10px;
}

:global(.skin-menu .skin-menu__item--active) {
  color: var(--color-accent);
  background: var(--color-hover);
}
</style>
