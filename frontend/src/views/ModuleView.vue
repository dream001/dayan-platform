<script setup lang="ts">
import StatePanel from '@/components/StatePanel.vue'

defineProps<{
  title: string
}>()
</script>

<template>
  <section
    class="module-view"
    :aria-labelledby="`module-${title}`"
  >
    <header>
      <p>功能模块</p>
      <h1 :id="`module-${title}`">
        {{ title }}
      </h1>
    </header>
    <StatePanel
      state="empty"
      title="功能入口已就绪"
      description="菜单与访问权限来自当前账户；业务页面将在对应管理功能接入后显示。"
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
