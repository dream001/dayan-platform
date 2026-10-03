<script setup lang="ts">
import {
  Aim,
  ArrowRight,
  ChatLineRound,
  CircleCheck,
  Clock,
  Coin,
  Connection,
  Collection,
  Cpu,
  DataAnalysis,
  Download,
  EditPen,
  Files,
  FolderOpened,
  Lock,
  MagicStick,
  Menu as MenuIcon,
  Message,
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
  workflow: Connection,
  message: Message,
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
  if (item.code === 'basic:view') return Cpu
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
  <ul
    class="navigation-tree"
    :class="{
      'navigation-tree--root': depth === 0,
      'navigation-tree--nested': depth > 0,
    }"
    :data-depth="depth"
  >
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
  position: relative;
  margin: 0;
  padding: 0;
  list-style: none;
}

.navigation-tree--root::before {
  position: absolute;
  top: 23px;
  bottom: 23px;
  left: 4px;
  width: 1px;
  background: var(--sidebar-border);
  content: '';
}

.navigation-tree--root > li {
  position: relative;
}

.navigation-tree--root > li::before {
  position: absolute;
  top: 21px;
  left: 1px;
  z-index: 2;
  width: 7px;
  height: 7px;
  border: 2px solid var(--sidebar-subtle);
  border-radius: 50%;
  background: var(--sidebar-surface);
  content: '';
  transition: border-color 150ms ease, background-color 150ms ease;
}

.navigation-tree--root > li:has(> .router-link-active)::before,
.navigation-tree--root > li:has(> .navigation-link--active)::before {
  border-color: var(--sidebar-accent);
  background: var(--sidebar-safety);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--sidebar-accent) 14%, transparent);
}

.navigation-link {
  position: relative;
  display: flex;
  width: 100%;
  min-height: 44px;
  align-items: center;
  gap: 10px;
  padding-right: 10px;
  cursor: pointer;
  border: 0;
  border-radius: 5px;
  color: var(--sidebar-muted);
  background: transparent;
  font-size: 12px;
  font-weight: 560;
  letter-spacing: 0;
  text-align: left;
  text-decoration: none;
  white-space: nowrap;
  transition:
    color 140ms ease,
    background-color 140ms ease,
    transform 140ms ease;
}

.navigation-tree--root > li > .navigation-link {
  min-height: 50px;
  padding-left: 44px !important;
  font-size: 14px;
  font-weight: 620;
}

.navigation-link:hover,
.navigation-link.router-link-active,
.navigation-link--active {
  color: var(--sidebar-text);
  background: var(--sidebar-active);
}

.navigation-link:hover {
  transform: translateX(2px);
}

.navigation-link__icon {
  flex: 0 0 auto;
  color: var(--sidebar-subtle);
  transition: color 140ms ease;
}

.navigation-tree--root > li > .navigation-link > .navigation-link__icon {
  position: absolute;
  left: 10px;
  z-index: 3;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 1px solid var(--sidebar-border);
  border-radius: 4px;
  background: var(--sidebar-panel);
}

.navigation-link:hover .navigation-link__icon,
.navigation-link.router-link-active .navigation-link__icon,
.navigation-link--active .navigation-link__icon {
  color: var(--sidebar-accent);
}

.navigation-tree--root > li > .navigation-link.router-link-active > .navigation-link__icon,
.navigation-tree--root > li > .navigation-link--active > .navigation-link__icon {
  border-color: color-mix(in srgb, var(--sidebar-accent) 70%, var(--sidebar-border));
  background: var(--sidebar-active);
}

.navigation-link__arrow {
  margin-left: auto;
  color: var(--sidebar-subtle);
  transition: transform 160ms ease;
}

.navigation-link__arrow--expanded {
  transform: rotate(90deg);
}

.navigation-group {
  margin: 15px 0 5px;
  color: var(--sidebar-muted);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0;
  white-space: nowrap;
}

.navigation-tree--nested {
  margin: 3px 0 10px 25px;
  padding: 4px 0 4px 12px;
  border-left: 1px solid var(--sidebar-border);
}

.navigation-tree--nested .navigation-link {
  min-height: 40px;
  gap: 9px;
  padding-right: 9px;
  padding-left: 10px !important;
  border-radius: 4px;
  color: var(--sidebar-muted);
  font-size: 13px;
  font-weight: 540;
}

.navigation-tree--nested .navigation-link__icon {
  width: 17px;
  font-size: 16px;
}

.navigation-tree--nested .navigation-link__arrow {
  color: var(--sidebar-subtle);
  font-size: 13px;
}

.navigation-tree--nested .navigation-link.router-link-active {
  color: var(--sidebar-text);
  background: var(--sidebar-active);
  box-shadow: inset 3px 0 var(--sidebar-accent);
  font-weight: 620;
}

.navigation-tree--nested .navigation-link:hover {
  color: var(--sidebar-text);
  background: color-mix(in srgb, var(--sidebar-active) 74%, var(--sidebar-surface));
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
