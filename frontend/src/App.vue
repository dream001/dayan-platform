<script setup lang="ts">
import StatePanel from '@/components/StatePanel.vue'
</script>

<template>
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
