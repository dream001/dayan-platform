<script setup lang="ts">
import { WarningFilled } from '@element-plus/icons-vue'
import { ElIcon, ElSkeleton, ElSkeletonItem } from 'element-plus'
import 'element-plus/theme-chalk/el-icon.css'
import 'element-plus/theme-chalk/el-skeleton.css'
import 'element-plus/theme-chalk/el-skeleton-item.css'

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
    <el-skeleton
      v-if="state === 'loading'"
      animated
      aria-label="正在加载"
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

    <template v-else>
      <div
        v-if="state === 'empty'"
        class="state-panel__empty-mark"
        aria-hidden="true"
      >
        <span />
        <span />
        <span />
      </div>
      <el-icon
        v-else
        class="state-panel__error-mark"
        :size="26"
        aria-hidden="true"
      >
        <WarningFilled />
      </el-icon>
      <h2>{{ title || (state === 'empty' ? '暂无数据' : '加载失败') }}</h2>
      <p v-if="description">
        {{ description }}
      </p>
      <button
        v-if="state === 'error'"
        type="button"
        @click="$emit('retry')"
      >
        重新加载
      </button>
      <slot />
    </template>
  </section>
</template>

<style scoped>
.state-panel {
  display: flex;
  min-height: 260px;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  color: var(--color-text-secondary);
}

.state-panel h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 16px;
  font-weight: 620;
}

.state-panel p {
  max-width: 500px;
  margin: 9px 0 0;
  font-size: 13px;
  line-height: 1.7;
}

.state-panel button {
  margin-top: 18px;
  padding: 7px 13px;
  cursor: pointer;
  border: 1px solid var(--color-border-strong);
  border-radius: 5px;
  color: var(--color-text-primary);
  background: var(--color-surface);
}

.state-panel__empty-mark {
  position: relative;
  width: 72px;
  height: 44px;
  margin-bottom: 23px;
  border-bottom: 1px solid var(--color-border-strong);
}

.state-panel__empty-mark span {
  position: absolute;
  bottom: 0;
  width: 13px;
  border: 1px solid #b8c5cc;
  border-bottom: 0;
  background: #eef3f5;
}

.state-panel__empty-mark span:nth-child(1) {
  left: 6px;
  height: 16px;
}

.state-panel__empty-mark span:nth-child(2) {
  left: 29px;
  height: 31px;
  border-color: #7da6af;
}

.state-panel__empty-mark span:nth-child(3) {
  right: 6px;
  height: 11px;
}

.state-panel__error-mark {
  margin-bottom: 20px;
  color: var(--color-danger);
}

.state-panel--loading {
  width: min(620px, 100%);
}

.state-panel__skeleton-title {
  width: 28%;
  height: 18px;
  margin-bottom: 18px;
}

.state-panel__skeleton-line {
  width: 72%;
  margin-top: 10px;
}

.state-panel__skeleton-line--short {
  width: 48%;
}
</style>
