<script setup lang="ts">
import { ArrowRight, Grid } from '@element-plus/icons-vue'
import { ElIcon } from 'element-plus'
import 'element-plus/theme-chalk/el-icon.css'
import type { NavigationItem } from '@/types/auth'

withDefaults(defineProps<{
  items: NavigationItem[]
  depth?: number
}>(), {
  depth: 0,
})

defineEmits<{
  navigate: []
}>()
</script>

<template>
  <ul class="navigation-tree">
    <li
      v-for="item in items"
      :key="item.id"
    >
      <RouterLink
        v-if="item.path"
        class="navigation-link"
        :to="item.path"
        :style="{ paddingLeft: `${10 + depth * 13}px` }"
        @click="$emit('navigate')"
      >
        <el-icon
          class="navigation-link__icon"
          :size="17"
        >
          <Grid />
        </el-icon>
        <span>{{ item.label }}</span>
        <el-icon
          class="navigation-link__arrow"
          :size="12"
        >
          <ArrowRight />
        </el-icon>
      </RouterLink>
      <p
        v-else
        class="navigation-group"
        :style="{ paddingLeft: `${10 + depth * 13}px` }"
      >
        {{ item.label }}
      </p>
      <NavigationTree
        v-if="item.children.length"
        :items="item.children"
        :depth="depth + 1"
        @navigate="$emit('navigate')"
      />
    </li>
  </ul>
</template>

<style scoped>
.navigation-tree {
  margin: 0;
  padding: 0;
  list-style: none;
}

.navigation-link {
  position: relative;
  display: flex;
  min-height: 40px;
  align-items: center;
  gap: 11px;
  padding-right: 10px;
  border-radius: 6px;
  color: #aeb9c3;
  font-size: 13px;
  font-weight: 500;
  text-decoration: none;
  white-space: nowrap;
  transition: color 140ms ease, background-color 140ms ease;
}

.navigation-link::before {
  position: absolute;
  left: -12px;
  width: 2px;
  height: 20px;
  border-radius: 0 2px 2px 0;
  background: var(--color-accent-soft);
  content: '';
  opacity: 0;
}

.navigation-link:hover,
.navigation-link.router-link-active {
  color: #fff;
  background: rgb(255 255 255 / 7%);
}

.navigation-link.router-link-active::before {
  opacity: 1;
}

.navigation-link__icon {
  flex: 0 0 auto;
}

.navigation-link__arrow {
  margin-left: auto;
  color: #61717f;
}

.navigation-group {
  margin: 15px 0 5px;
  color: #758390;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
  white-space: nowrap;
}
</style>
