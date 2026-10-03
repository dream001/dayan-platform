import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const COLLAPSE_STORAGE_KEY = 'dayan:sidebar-collapsed'
const SKIN_STORAGE_KEY = 'dayan:skin'

export type AppSkin = 'titanium' | 'orbit' | 'forge'

const skins: AppSkin[] = ['titanium', 'orbit', 'forge']

function readInitialCollapseState() {
  return window.localStorage.getItem(COLLAPSE_STORAGE_KEY) === 'true'
}

function readInitialSkin(): AppSkin {
  const stored = window.localStorage.getItem(SKIN_STORAGE_KEY)
  return skins.includes(stored as AppSkin) ? stored as AppSkin : 'titanium'
}

export const useLayoutStore = defineStore('layout', () => {
  const isSidebarCollapsed = ref(readInitialCollapseState())
  const isMobileNavigationOpen = ref(false)
  const skin = ref<AppSkin>(readInitialSkin())

  const sidebarWidth = computed(() => (isSidebarCollapsed.value ? '72px' : '252px'))

  function applySkin(value: AppSkin) {
    skin.value = value
    document.documentElement.dataset.skin = value
  }

  applySkin(skin.value)

  function toggleSidebar() {
    isSidebarCollapsed.value = !isSidebarCollapsed.value
    window.localStorage.setItem(COLLAPSE_STORAGE_KEY, String(isSidebarCollapsed.value))
  }

  function toggleMobileNavigation() {
    isMobileNavigationOpen.value = !isMobileNavigationOpen.value
  }

  function closeMobileNavigation() {
    isMobileNavigationOpen.value = false
  }

  function setSkin(value: AppSkin) {
    applySkin(value)
    window.localStorage.setItem(SKIN_STORAGE_KEY, value)
  }

  return {
    isSidebarCollapsed,
    isMobileNavigationOpen,
    skin,
    sidebarWidth,
    toggleSidebar,
    toggleMobileNavigation,
    closeMobileNavigation,
    setSkin,
  }
})
