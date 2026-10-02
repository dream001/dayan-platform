<script setup lang="ts">
import { ArrowDown, ArrowRight, Edit, Plus } from '@element-plus/icons-vue'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DepartmentNode } from '@/types/admin'

defineOptions({ name: 'OrganizationChartNode' })

withDefaults(defineProps<{
  node: DepartmentNode
  root?: boolean
}>(), {
  root: false,
})

const emit = defineEmits<{
  createChild: [node: DepartmentNode]
  edit: [node: DepartmentNode]
}>()

const { t } = useI18n()
const collapsed = ref(false)
</script>

<template>
  <li
    class="org-tree-item"
    :class="{ 'is-root': root }"
  >
    <article
      class="org-node"
      :class="{ 'is-disabled': !node.enabled }"
    >
      <div class="org-node-status">
        <span
          class="status-dot"
          :class="{ 'is-disabled': !node.enabled }"
        />
        <span>{{ node.enabled ? t('departments.statusEnabled') : t('departments.statusDisabled') }}</span>
      </div>

      <div class="org-node-main">
        <strong>{{ node.name }}</strong>
        <span class="org-node-code">{{ node.code }}</span>
      </div>

      <div class="org-node-footer">
        <el-tooltip
          v-if="node.children.length"
          :content="collapsed ? t('departments.expandBranch') : t('departments.collapseBranch')"
        >
          <el-button
            :icon="collapsed ? ArrowRight : ArrowDown"
            circle
            text
            :aria-label="collapsed ? t('departments.expandBranch') : t('departments.collapseBranch')"
            :aria-expanded="!collapsed"
            @click="collapsed = !collapsed"
          />
        </el-tooltip>
        <span class="child-count">
          {{ t('departments.childCount', { count: node.children.length }) }}
        </span>
        <span class="node-actions">
          <el-tooltip :content="t('departments.addChild')">
            <el-button
              v-permission="'system:department:create'"
              :icon="Plus"
              circle
              text
              :aria-label="t('departments.addChild')"
              @click="emit('createChild', node)"
            />
          </el-tooltip>
          <el-tooltip :content="t('departments.edit')">
            <el-button
              v-permission="'system:department:update'"
              :icon="Edit"
              circle
              text
              :aria-label="t('departments.edit')"
              @click="emit('edit', node)"
            />
          </el-tooltip>
        </span>
      </div>
    </article>

    <ul
      v-if="node.children.length && !collapsed"
      class="org-tree-children"
    >
      <OrganizationChartNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        @create-child="emit('createChild', $event)"
        @edit="emit('edit', $event)"
      />
    </ul>
  </li>
</template>

<style scoped>
.org-tree-item {
  position: relative;
  flex: 0 0 auto;
  padding: 28px 10px 0;
  list-style: none;
  text-align: center;
}

.org-tree-item::before,
.org-tree-item::after {
  position: absolute;
  top: 0;
  width: 50%;
  height: 28px;
  border-top: 1px solid #b9c9cf;
  content: '';
}

.org-tree-item::before {
  right: 50%;
}

.org-tree-item::after {
  left: 50%;
  border-left: 1px solid #b9c9cf;
}

.org-tree-item:first-child::before,
.org-tree-item:last-child::after {
  border-top-color: transparent;
}

.org-tree-item:last-child::before {
  border-right: 1px solid #b9c9cf;
  border-radius: 0 5px 0 0;
}

.org-tree-item:first-child::after {
  border-radius: 5px 0 0;
}

.org-tree-item:only-child::before {
  display: none;
}

.org-tree-item:only-child::after {
  width: 0;
  border-top: 0;
  border-left: 1px solid #b9c9cf;
  border-radius: 0;
}

.org-tree-item.is-root {
  padding-top: 0;
}

.org-tree-item.is-root::before,
.org-tree-item.is-root::after {
  display: none;
}

.org-node {
  position: relative;
  z-index: 1;
  width: 220px;
  overflow: hidden;
  border: 1px solid #cfdce1;
  border-top: 3px solid var(--color-accent);
  border-radius: 6px;
  background: var(--color-surface);
  box-shadow: 0 5px 16px rgb(35 66 78 / 7%);
  text-align: left;
  transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
}

.org-node:hover {
  border-color: #9bbbc4;
  box-shadow: 0 8px 20px rgb(35 66 78 / 11%);
  transform: translateY(-1px);
}

.org-node.is-disabled {
  border-top-color: #9ca9af;
}

.org-node-status {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 12px 0;
  color: var(--color-text-muted);
  font-size: 11px;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #2f8f68;
  box-shadow: 0 0 0 3px rgb(47 143 104 / 11%);
}

.status-dot.is-disabled {
  background: #9ca9af;
  box-shadow: none;
}

.org-node-main {
  display: grid;
  gap: 7px;
  min-height: 74px;
  padding: 10px 12px 13px;
}

.org-node-main strong {
  overflow: hidden;
  color: var(--color-ink);
  font-size: 14px;
  font-weight: 680;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-node-code {
  overflow: hidden;
  color: var(--color-text-secondary);
  font-family: var(--font-mono);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-node-footer {
  display: flex;
  align-items: center;
  min-height: 40px;
  padding: 4px 7px 4px 9px;
  border-top: 1px solid var(--color-border);
  background: #f8fafb;
}

.child-count {
  margin-right: auto;
  color: var(--color-text-muted);
  font-size: 11px;
}

.node-actions {
  display: flex;
  align-items: center;
  gap: 1px;
}

.org-tree-children {
  position: relative;
  display: flex;
  justify-content: center;
  margin: 0;
  padding: 28px 0 0;
}

.org-tree-children::before {
  position: absolute;
  top: 0;
  left: 50%;
  height: 28px;
  border-left: 1px solid #b9c9cf;
  content: '';
}

@media (max-width: 700px) {
  .org-tree-item {
    padding-inline: 7px;
  }

  .org-node {
    width: 190px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .org-node {
    transition: none;
  }
}
</style>
