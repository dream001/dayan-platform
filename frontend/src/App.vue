<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import StatePanel from '@/components/StatePanel.vue'
import { elementLocales, type AppLocale } from '@/i18n'

const { locale } = useI18n()
const elementLocale = computed(() => elementLocales[locale.value as AppLocale])
</script>

<template>
  <el-config-provider :locale="elementLocale">
    <RouterView v-slot="{ Component }">
      <Suspense>
        <component :is="Component" />
        <template #fallback>
          <main class="route-loading">
            <StatePanel state="loading" />
          </main>
        </template>
      </Suspense>
    </RouterView>
  </el-config-provider>
</template>

<style scoped>
.route-loading {
  display: grid;
  min-height: 100svh;
  place-items: center;
  padding: 32px;
  background: var(--color-canvas);
}
</style>
