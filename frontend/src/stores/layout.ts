import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const COLLAPSE_STORAGE_KEY = 'dayan:sidebar-collapsed'

function readInitialCollapseState() {
  return window.localStorage.getItem(COLLAPSE_STORAGE_KEY) === 'true'
}

export const useLayoutStore = defineStore('layout', () => {
  const isSidebarCollapsed = ref(readInitialCollapseState())
  const isMobileNavigationOpen = ref(false)

  const sidebarWidth = computed(() => (isSidebarCollapsed.value ? '72px' : '232px'))

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

  return {
    isSidebarCollapsed,
    isMobileNavigationOpen,
    sidebarWidth,
    toggleSidebar,
    toggleMobileNavigation,
    closeMobileNavigation,
  }
})
