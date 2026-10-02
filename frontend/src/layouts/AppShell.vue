<script setup lang="ts">
import { Expand, Fold, Menu as MenuIcon, SwitchButton } from '@element-plus/icons-vue'
import { ElIcon, ElTooltip } from 'element-plus'
import 'element-plus/theme-chalk/el-icon.css'
import 'element-plus/theme-chalk/el-tooltip.css'
import { storeToRefs } from 'pinia'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NavigationTree from '@/components/NavigationTree.vue'
import { confirmAction, notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'
import { useLayoutStore } from '@/stores/layout'

const route = useRoute()
const router = useRouter()
const layoutStore = useLayoutStore()
const authStore = useAuthStore()
const { isMobileNavigationOpen, isSidebarCollapsed, sidebarWidth } = storeToRefs(layoutStore)
const { displayName, navigation } = storeToRefs(authStore)

const environmentLabel = import.meta.env.MODE === 'production' ? '生产环境' : '开发环境'
const pageTitle = computed(() =>
  typeof route.meta.title === 'string' ? route.meta.title : '工作台',
)
const pageEyebrow = computed(() =>
  typeof route.meta.eyebrow === 'string' ? route.meta.eyebrow : '管理平台',
)
const accountInitial = computed(() => displayName.value.slice(0, 1).toUpperCase() || '—')

async function handleLogout() {
  const confirmed = await confirmAction('退出后需要重新登录才能继续访问。', '退出登录', '退出')
  if (!confirmed) return
  try {
    await authStore.signOut()
  } catch (error) {
    notifyError(error, '服务端退出失败，本地会话已清除')
  } finally {
    await router.replace({ name: 'login' })
  }
}
</script>

<template>
  <div
    class="app-shell"
    :class="{ 'app-shell--collapsed': isSidebarCollapsed }"
    :style="{ '--sidebar-width': sidebarWidth }"
  >
    <button
      v-if="isMobileNavigationOpen"
      class="navigation-scrim"
      type="button"
      aria-label="关闭导航"
      @click="layoutStore.closeMobileNavigation"
    />

    <aside
      id="primary-navigation"
      class="sidebar"
      :class="{ 'sidebar--mobile-open': isMobileNavigationOpen }"
      aria-label="主导航"
    >
      <div class="brand">
        <div
          class="brand__mark"
          aria-hidden="true"
        >
          <span />
          <span />
        </div>
        <div class="brand__copy">
          <strong>大雁</strong>
          <span>管理平台</span>
        </div>
      </div>

      <nav class="navigation">
        <p class="navigation__label">
          工作区
        </p>
        <NavigationTree
          v-if="navigation.length"
          :items="navigation"
          @navigate="layoutStore.closeMobileNavigation"
        />
        <p
          v-else
          class="navigation__empty"
        >
          当前账户暂无菜单
        </p>
      </nav>

      <div class="sidebar__footer">
        <span
          class="status-dot status-dot--online"
          aria-hidden="true"
        />
        <div class="sidebar__status">
          <strong>身份已验证</strong>
          <span>权限配置已同步</span>
        </div>
      </div>
    </aside>

    <div class="shell-content">
      <header class="topbar">
        <button
          class="icon-button icon-button--mobile"
          type="button"
          aria-controls="primary-navigation"
          :aria-expanded="isMobileNavigationOpen"
          aria-label="打开导航"
          @click="layoutStore.toggleMobileNavigation"
        >
          <el-icon :size="20">
            <MenuIcon />
          </el-icon>
        </button>

        <el-tooltip
          :content="isSidebarCollapsed ? '展开导航' : '收起导航'"
          placement="bottom"
        >
          <button
            class="icon-button icon-button--desktop"
            type="button"
            :aria-label="isSidebarCollapsed ? '展开导航' : '收起导航'"
            @click="layoutStore.toggleSidebar"
          >
            <el-icon :size="18">
              <Expand v-if="isSidebarCollapsed" />
              <Fold v-else />
            </el-icon>
          </button>
        </el-tooltip>

        <div class="topbar__context">
          <span>{{ pageEyebrow }}</span>
          <span
            class="topbar__separator"
            aria-hidden="true"
          >/</span>
          <strong>{{ pageTitle }}</strong>
        </div>

        <div class="topbar__rail">
          <div class="environment">
            <span
              class="environment__pulse"
              aria-hidden="true"
            />
            {{ environmentLabel }}
          </div>
          <RouterLink
            class="account"
            to="/profile"
            aria-label="当前身份"
          >
            <span
              class="account__avatar"
              aria-hidden="true"
            >{{ accountInitial }}</span>
            <span class="account__label">{{ displayName }}</span>
          </RouterLink>
          <el-tooltip
            content="退出登录"
            placement="bottom"
          >
            <button
              class="icon-button logout-button"
              type="button"
              aria-label="退出登录"
              @click="handleLogout"
            >
              <el-icon :size="17">
                <SwitchButton />
              </el-icon>
            </button>
          </el-tooltip>
        </div>
      </header>

      <main class="workspace">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  --sidebar-width: 232px;
  min-height: 100svh;
  background: var(--color-canvas);
}

.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 30;
  display: flex;
  width: var(--sidebar-width);
  flex-direction: column;
  overflow: hidden;
  color: #d9e1e8;
  background: var(--color-ink);
  transition: width 180ms ease, transform 220ms ease;
}

.brand {
  display: flex;
  min-height: 64px;
  align-items: center;
  gap: 11px;
  padding: 0 20px;
  border-bottom: 1px solid rgb(255 255 255 / 8%);
  white-space: nowrap;
}

.brand__mark {
  position: relative;
  width: 29px;
  min-width: 29px;
  height: 24px;
}

.brand__mark span {
  position: absolute;
  width: 18px;
  height: 8px;
  border: 2px solid var(--color-accent-soft);
  border-left: 0;
  border-radius: 0 12px 12px 0;
  transform: rotate(-18deg);
}

.brand__mark span:first-child {
  top: 2px;
  left: 1px;
}

.brand__mark span:last-child {
  right: 0;
  bottom: 2px;
  transform: rotate(18deg) scaleX(-1);
}

.brand__copy {
  display: flex;
  align-items: baseline;
  gap: 7px;
  opacity: 1;
  transition: opacity 120ms ease;
}

.brand__copy strong {
  color: #fff;
  font-size: 17px;
  letter-spacing: 0.08em;
}

.brand__copy span {
  color: #8f9ca8;
  font-size: 11px;
  letter-spacing: 0.12em;
}

.navigation {
  flex: 1;
  padding: 20px 12px;
}

.navigation__label {
  margin: 0 10px 8px;
  color: #758390;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  transition: opacity 120ms ease;
}

.navigation__empty {
  margin: 13px 10px;
  color: #6f7d88;
  font-size: 11px;
  line-height: 1.6;
}

.sidebar__footer {
  display: flex;
  min-height: 66px;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-top: 1px solid rgb(255 255 255 / 8%);
  white-space: nowrap;
}

.status-dot {
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  border: 2px solid #586875;
  border-radius: 50%;
}

.status-dot--online {
  border-color: var(--color-accent-soft);
  background: var(--color-accent-soft);
  box-shadow: 0 0 0 3px rgb(112 185 199 / 10%);
}

.sidebar__status {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11px;
}

.sidebar__status strong {
  color: #c4ced6;
  font-weight: 500;
}

.sidebar__status span {
  color: #71808d;
}

.shell-content {
  min-height: 100svh;
  margin-left: var(--sidebar-width);
  transition: margin-left 180ms ease;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  height: 63px;
  align-items: center;
  gap: 14px;
  padding: 0 28px;
  border-bottom: 1px solid var(--color-border);
  background: rgb(247 249 251 / 94%);
  backdrop-filter: blur(10px);
}

.icon-button {
  display: inline-grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  cursor: pointer;
  place-items: center;
  border: 0;
  border-radius: 5px;
  color: var(--color-text-secondary);
  background: transparent;
  transition: color 140ms ease, background-color 140ms ease;
}

.icon-button:hover {
  color: var(--color-text-primary);
  background: var(--color-hover);
}

.icon-button--mobile {
  display: none;
}

.topbar__context {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.topbar__context span:first-child {
  color: var(--color-text-muted);
}

.topbar__context strong {
  overflow: hidden;
  color: var(--color-text-primary);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar__separator {
  color: #c0c9d0;
}

.topbar__rail {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-left: auto;
}

.environment {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--color-text-secondary);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.04em;
}

.environment__pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-accent);
  box-shadow: 0 0 0 3px rgb(18 116 138 / 10%);
}

.account {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 18px;
  border-left: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-size: 12px;
  text-decoration: none;
}

.account:hover {
  color: var(--color-text-primary);
}

.account__avatar {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 1px solid #cbd4da;
  border-radius: 50%;
  color: #8a98a3;
  background: #fff;
}

.logout-button {
  margin-left: -8px;
}

.workspace {
  min-height: calc(100svh - 64px);
  padding: 30px 32px 48px;
}

.app-shell--collapsed .brand {
  padding-inline: 21px;
}

.app-shell--collapsed .brand__copy,
.app-shell--collapsed .navigation__label,
.app-shell--collapsed .navigation__empty,
.app-shell--collapsed .sidebar__status {
  width: 0;
  opacity: 0;
  pointer-events: none;
}

.app-shell--collapsed :deep(.navigation-link) {
  justify-content: center;
  padding-inline: 0 !important;
}

.app-shell--collapsed :deep(.navigation-link span),
.app-shell--collapsed :deep(.navigation-link__arrow),
.app-shell--collapsed :deep(.navigation-group),
.app-shell--collapsed :deep(.navigation-tree .navigation-tree) {
  display: none;
}

.app-shell--collapsed .sidebar__footer {
  justify-content: center;
  padding-inline: 0;
}

.navigation-scrim {
  display: none;
}

@media (max-width: 760px) {
  .sidebar {
    width: 232px;
    transform: translateX(-100%);
  }

  .sidebar--mobile-open {
    transform: translateX(0);
    box-shadow: 24px 0 60px rgb(20 34 43 / 18%);
  }

  .shell-content {
    margin-left: 0;
  }

  .navigation-scrim {
    position: fixed;
    inset: 0;
    z-index: 25;
    display: block;
    width: 100%;
    border: 0;
    background: rgb(19 31 39 / 32%);
  }

  .icon-button--desktop {
    display: none;
  }

  .icon-button--mobile {
    display: inline-grid;
  }

  .topbar {
    gap: 9px;
    padding: 0 16px;
  }

  .topbar__context span:first-child,
  .topbar__separator,
  .account__label {
    display: none;
  }

  .topbar__rail {
    gap: 12px;
  }

  .account {
    padding-left: 12px;
  }

  .workspace {
    padding: 24px 18px 36px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .sidebar,
  .shell-content {
    transition: none;
  }
}
</style>
