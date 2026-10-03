<script setup lang="ts">
import { Expand, Fold, Menu as MenuIcon, SwitchButton } from '@element-plus/icons-vue'
import { ElIcon, ElTooltip } from 'element-plus'
import 'element-plus/theme-chalk/el-icon.css'
import 'element-plus/theme-chalk/el-tooltip.css'
import { storeToRefs } from 'pinia'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import NavigationTree from '@/components/NavigationTree.vue'
import SkinSwitcher from '@/components/SkinSwitcher.vue'
import { confirmAction, notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'
import { useLayoutStore } from '@/stores/layout'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const layoutStore = useLayoutStore()
const authStore = useAuthStore()
const { isMobileNavigationOpen, isSidebarCollapsed, sidebarWidth } = storeToRefs(layoutStore)
const { displayName, navigation } = storeToRefs(authStore)

const environmentLabel = computed(() =>
  import.meta.env.MODE === 'production' ? t('env.production') : t('env.development'),
)
function resolveKey(key?: string): string | undefined {
  if (typeof key !== 'string') return undefined
  const translated = t(key)
  return translated === key ? undefined : translated
}
const pageTitle = computed(() =>
  resolveKey(route.meta.titleKey)
  ?? (typeof route.meta.title === 'string' ? route.meta.title : undefined)
  ?? t('shell.defaultTitle'),
)
const pageEyebrow = computed(() =>
  resolveKey(route.meta.eyebrowKey)
  ?? (typeof route.meta.eyebrow === 'string' ? route.meta.eyebrow : undefined)
  ?? t('shell.defaultEyebrow'),
)
const accountInitial = computed(() => displayName.value.slice(0, 1).toUpperCase() || '—')

async function handleLogout() {
  const confirmed = await confirmAction(
    t('shell.logout.confirm'),
    t('shell.logout.title'),
    t('shell.logout.button'),
  )
  if (!confirmed) return
  try {
    await authStore.signOut()
  } catch (error) {
    notifyError(error, t('shell.logout.remoteFailed'))
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
      :aria-label="t('shell.closeNav')"
      @click="layoutStore.closeMobileNavigation"
    />

    <aside
      id="primary-navigation"
      class="sidebar"
      :class="{ 'sidebar--mobile-open': isMobileNavigationOpen }"
      :aria-label="t('shell.primaryNav')"
    >
      <div class="brand">
        <div
          class="brand__mark"
          aria-hidden="true"
        >
          <i class="brand__joint brand__joint--base" />
          <i class="brand__arm brand__arm--lower" />
          <i class="brand__joint brand__joint--elbow" />
          <i class="brand__arm brand__arm--upper" />
          <i class="brand__joint brand__joint--tool" />
        </div>
        <div class="brand__copy">
          <div>
            <strong>{{ t('app.brand') }}</strong>
            <span>{{ t('app.platform') }}</span>
          </div>
          <small>{{ t('app.domain') }}</small>
        </div>
      </div>

      <nav class="navigation">
        <div class="navigation__heading">
          <p class="navigation__label">
            {{ t('shell.workspace') }}
          </p>
          <span>{{ t('shell.controlBus') }}</span>
        </div>
        <NavigationTree
          v-if="navigation.length"
          :items="navigation"
          @navigate="layoutStore.closeMobileNavigation"
        />
        <p
          v-else
          class="navigation__empty"
        >
          {{ t('shell.noMenu') }}
        </p>
      </nav>

      <div class="sidebar__footer">
        <div
          class="system-signal"
          aria-hidden="true"
        >
          <i />
          <i />
          <i />
        </div>
        <div class="sidebar__status">
          <strong>{{ t('shell.identityVerified') }}</strong>
          <span>{{ t('shell.permissionsSynced') }}</span>
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
          :aria-label="t('shell.openNav')"
          @click="layoutStore.toggleMobileNavigation"
        >
          <el-icon :size="20">
            <MenuIcon />
          </el-icon>
        </button>

        <el-tooltip
          :content="isSidebarCollapsed ? t('shell.expandNav') : t('shell.collapseNav')"
          placement="bottom"
        >
          <button
            class="icon-button icon-button--desktop"
            type="button"
            :aria-label="isSidebarCollapsed ? t('shell.expandNav') : t('shell.collapseNav')"
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
            :aria-label="t('shell.currentIdentity')"
          >
            <span
              class="account__avatar"
              aria-hidden="true"
            >{{ accountInitial }}</span>
            <span class="account__label">{{ displayName }}</span>
          </RouterLink>
          <SkinSwitcher />
          <LanguageSwitcher />
          <el-tooltip
            :content="t('shell.logout.title')"
            placement="bottom"
          >
            <button
              class="icon-button logout-button"
              type="button"
              :aria-label="t('shell.logout.title')"
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
  --sidebar-width: 252px;
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
  border-right: 1px solid var(--sidebar-border);
  color: var(--sidebar-text);
  background: var(--sidebar-surface);
  box-shadow: 10px 0 28px rgb(25 49 52 / 7%);
  transition: width 180ms ease, transform 220ms ease;
}

.brand {
  display: flex;
  min-height: 82px;
  align-items: center;
  gap: 14px;
  padding: 0 18px;
  border-bottom: 1px solid var(--sidebar-border);
  white-space: nowrap;
}

.brand__mark {
  position: relative;
  width: 42px;
  min-width: 42px;
  height: 42px;
  border: 1px solid color-mix(in srgb, var(--sidebar-accent) 32%, var(--sidebar-border));
  border-radius: 6px;
  background: var(--sidebar-panel);
  box-shadow: inset 0 0 0 1px rgb(255 255 255 / 55%);
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.brand:hover .brand__mark {
  border-color: color-mix(in srgb, var(--sidebar-accent) 70%, var(--sidebar-border));
  box-shadow:
    inset 0 0 0 1px rgb(255 255 255 / 72%),
    0 6px 18px rgb(8 127 131 / 13%);
  transform: translateY(-1px);
}

.brand__joint {
  position: absolute;
  z-index: 2;
  width: 7px;
  height: 7px;
  border: 2px solid var(--sidebar-accent);
  border-radius: 50%;
  background: var(--sidebar-panel);
  box-shadow: 0 0 0 2px rgb(8 127 131 / 10%);
  animation: brand-joint-pulse 2.8s ease-in-out infinite;
}

.brand__joint--base {
  left: 7px;
  bottom: 7px;
  animation-delay: 0ms;
}

.brand__joint--elbow {
  top: 17px;
  left: 18px;
  animation-delay: 520ms;
}

.brand__joint--tool {
  top: 8px;
  right: 7px;
  border-color: var(--sidebar-safety);
  animation-delay: 1040ms;
}

.brand__arm {
  position: absolute;
  z-index: 1;
  height: 2px;
  overflow: visible;
  background: var(--sidebar-signal);
  transform-origin: left center;
}

.brand__arm::after {
  position: absolute;
  top: -2px;
  left: -2px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sidebar-safety);
  box-shadow: 0 0 8px rgb(168 189 54 / 52%);
  content: '';
  opacity: 0;
  animation: brand-energy-flow 2.8s ease-in-out infinite;
}

.brand__arm--lower {
  bottom: 13px;
  left: 12px;
  width: 17px;
  transform: rotate(-47deg);
}

.brand__arm--lower::after {
  animation-delay: 220ms;
}

.brand__arm--upper {
  top: 17px;
  left: 24px;
  width: 14px;
  transform: rotate(-35deg);
}

.brand__arm--upper::after {
  animation-delay: 760ms;
}

.brand__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
  opacity: 1;
  transition: opacity 120ms ease;
}

.brand__copy div {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

.brand__copy strong {
  color: var(--sidebar-text);
  font-size: 18px;
  font-weight: 680;
  letter-spacing: 0;
}

.brand__copy span {
  color: var(--sidebar-muted);
  font-size: 11px;
  letter-spacing: 0;
}

.brand__copy small {
  color: var(--sidebar-accent);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0;
}

.navigation {
  flex: 1;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 22px 14px 18px;
  scrollbar-color: #b8cac7 transparent;
  scrollbar-width: thin;
}

.navigation__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 0 8px 13px;
  transition: opacity 120ms ease;
}

.navigation__label {
  margin: 0;
  color: var(--sidebar-muted);
  font-size: 10px;
  font-weight: 650;
  letter-spacing: 0;
}

.navigation__heading > span {
  color: var(--sidebar-subtle);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0;
}

.navigation__empty {
  margin: 13px 10px;
  color: var(--sidebar-muted);
  font-size: 11px;
  line-height: 1.6;
}

.sidebar__footer {
  display: flex;
  min-height: 78px;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  border-top: 1px solid var(--sidebar-border);
  background: var(--sidebar-panel);
  white-space: nowrap;
}

.system-signal {
  display: flex;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  align-items: flex-end;
  justify-content: center;
  gap: 3px;
  padding: 8px 6px;
  border: 1px solid var(--sidebar-border);
  border-radius: 5px;
  background: var(--sidebar-surface);
}

.system-signal i {
  width: 3px;
  border-radius: 1px;
  background: var(--sidebar-signal);
  animation: system-signal 1.8s ease-in-out infinite;
}

.system-signal i:first-child {
  height: 6px;
}

.system-signal i:nth-child(2) {
  height: 12px;
  animation-delay: 180ms;
}

.system-signal i:last-child {
  height: 9px;
  animation-delay: 360ms;
}

.sidebar__status {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11px;
}

.sidebar__status strong {
  color: var(--sidebar-text);
  font-size: 11px;
  font-weight: 600;
}

.sidebar__status span {
  color: var(--sidebar-muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0;
}

@keyframes system-signal {
  0%,
  100% {
    opacity: 0.45;
    transform: scaleY(0.7);
  }

  50% {
    opacity: 1;
    transform: scaleY(1);
  }
}

@keyframes brand-joint-pulse {
  0%,
  18%,
  100% {
    opacity: 0.68;
    box-shadow: 0 0 0 2px rgb(8 127 131 / 10%);
  }

  28%,
  40% {
    opacity: 1;
    box-shadow:
      0 0 0 3px rgb(8 127 131 / 12%),
      0 0 10px rgb(8 127 131 / 36%);
  }
}

@keyframes brand-energy-flow {
  0%,
  14% {
    opacity: 0;
    transform: translateX(0) scale(0.6);
  }

  22% {
    opacity: 1;
  }

  42% {
    opacity: 0;
    transform: translateX(calc(100% + 10px)) scale(1);
  }

  100% {
    opacity: 0;
    transform: translateX(calc(100% + 10px)) scale(0.6);
  }
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
  justify-content: center;
  padding-inline: 0;
}

.app-shell--collapsed .brand__copy,
.app-shell--collapsed .navigation__heading,
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

.app-shell--collapsed .navigation {
  padding-inline: 9px;
}

.app-shell--collapsed :deep(.navigation-tree--root::before),
.app-shell--collapsed :deep(.navigation-tree--root > li::before) {
  display: none;
}

.navigation-scrim {
  display: none;
}

@media (max-width: 760px) {
  .sidebar {
    width: 268px;
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
  .shell-content,
  .system-signal i,
  .brand__joint,
  .brand__arm::after {
    animation: none;
    transition: none;
  }
}
</style>
