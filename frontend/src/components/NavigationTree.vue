<script setup lang="ts">
import {
  Aim,
  ArrowRight,
  ChatLineRound,
  CircleCheck,
  Clock,
  Coin,
  Collection,
  DataAnalysis,
  Download,
  EditPen,
  Files,
  FolderOpened,
  Lock,
  MagicStick,
  Menu as MenuIcon,
  Monitor,
  OfficeBuilding,
  Reading,
  Setting,
  TrendCharts,
  Upload,
  User,
} from '@element-plus/icons-vue'
import { ElIcon } from 'element-plus'
import 'element-plus/theme-chalk/el-icon.css'
import { reactive, watch } from 'vue'
import type { Component } from 'vue'
import { useRoute } from 'vue-router'
import { translateMenu } from '@/i18n'
import type { NavigationItem } from '@/types/auth'

const iconComponents: Record<string, Component> = {
  dashboard: DataAnalysis,
  database: Coin,
  files: Files,
  upload: Upload,
  collect: Aim,
  annotate: EditPen,
  qc: CircleCheck,
  dict: Reading,
  chart: TrendCharts,
  skill: MagicStick,
  export: Download,
  visual: Monitor,
  agent: ChatLineRound,
  settings: Setting,
  users: User,
  shield: Lock,
  menu: MenuIcon,
  organization: OfficeBuilding,
  folder: FolderOpened,
  history: Clock,
}

const props = withDefaults(defineProps<{
  items: NavigationItem[]
  depth?: number
}>(), {
  depth: 0,
})

const route = useRoute()
const expandedItems = reactive<Record<string, boolean>>({})

defineEmits<{
  navigate: []
}>()

function resolveIcon(item: NavigationItem): Component {
  return iconComponents[item.icon?.toLowerCase() ?? ''] ?? Collection
}

function containsActiveRoute(item: NavigationItem): boolean {
  return item.path === route.path || item.children.some(containsActiveRoute)
}

function isExpanded(item: NavigationItem): boolean {
  return expandedItems[item.id] ?? false
}

function toggleItem(item: NavigationItem) {
  expandedItems[item.id] = !isExpanded(item)
}

watch(
  () => route.path,
  () => {
    props.items.forEach((item) => {
      if (item.children.length && containsActiveRoute(item)) {
        expandedItems[item.id] = true
      }
    })
  },
  { immediate: true },
)
</script>

<template>
  <ul class="navigation-tree">
    <li
      v-for="item in items"
      :key="item.id"
    >
      <button
        v-if="item.children.length"
        class="navigation-link navigation-link--branch"
        :class="{ 'navigation-link--active': containsActiveRoute(item) }"
        type="button"
        :style="{ paddingLeft: `${10 + depth * 13}px` }"
        :aria-expanded="isExpanded(item)"
        @click="toggleItem(item)"
      >
        <el-icon
          class="navigation-link__icon"
          :size="18"
        >
          <component :is="resolveIcon(item)" />
        </el-icon>
        <span>{{ translateMenu(item.code, item.label) }}</span>
        <el-icon
          class="navigation-link__arrow"
          :class="{ 'navigation-link__arrow--expanded': isExpanded(item) }"
          :size="12"
        >
          <ArrowRight />
        </el-icon>
      </button>
      <RouterLink
        v-else-if="item.path"
        class="navigation-link"
        :to="item.path"
        :style="{ paddingLeft: `${10 + depth * 13}px` }"
        @click="$emit('navigate')"
      >
        <el-icon
          class="navigation-link__icon"
          :size="18"
        >
          <component :is="resolveIcon(item)" />
        </el-icon>
        <span>{{ translateMenu(item.code, item.label) }}</span>
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
        {{ translateMenu(item.code, item.label) }}
      </p>
      <Transition name="navigation-branch">
        <NavigationTree
          v-if="item.children.length && isExpanded(item)"
          :items="item.children"
          :depth="depth + 1"
          @navigate="$emit('navigate')"
        />
      </Transition>
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
  width: 100%;
  min-height: 40px;
  align-items: center;
  gap: 11px;
  padding-right: 10px;
  cursor: pointer;
  border: 0;
  border-radius: 6px;
  color: #aeb9c3;
  background: transparent;
  font-size: 13px;
  font-weight: 500;
  text-align: left;
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
.navigation-link.router-link-active,
.navigation-link--active {
  color: #fff;
  background: rgb(255 255 255 / 7%);
}

.navigation-link.router-link-active::before,
.navigation-link--active::before {
  opacity: 1;
}

.navigation-link__icon {
  flex: 0 0 auto;
  color: #8294a1;
  transition: color 140ms ease;
}

.navigation-link:hover .navigation-link__icon,
.navigation-link.router-link-active .navigation-link__icon,
.navigation-link--active .navigation-link__icon {
  color: var(--color-accent-soft);
}

.navigation-link__arrow {
  margin-left: auto;
  color: #61717f;
  transition: transform 160ms ease;
}

.navigation-link__arrow--expanded {
  transform: rotate(90deg);
}

.navigation-group {
  margin: 15px 0 5px;
  color: #758390;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.navigation-branch-enter-active,
.navigation-branch-leave-active {
  max-height: 480px;
  overflow: hidden;
  transition: max-height 180ms ease, opacity 140ms ease;
}

.navigation-branch-enter-from,
.navigation-branch-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
