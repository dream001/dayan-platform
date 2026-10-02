import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useLayoutStore } from './layout'

describe('layout store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('toggles and persists the desktop navigation state', () => {
    const store = useLayoutStore()

    expect(store.isSidebarCollapsed).toBe(false)
    expect(store.sidebarWidth).toBe('232px')

    store.toggleSidebar()

    expect(store.isSidebarCollapsed).toBe(true)
    expect(store.sidebarWidth).toBe('72px')
    expect(window.localStorage.getItem('dayan:sidebar-collapsed')).toBe('true')
  })

  it('opens and closes mobile navigation', () => {
    const store = useLayoutStore()

    store.toggleMobileNavigation()
    expect(store.isMobileNavigationOpen).toBe(true)

    store.closeMobileNavigation()
    expect(store.isMobileNavigationOpen).toBe(false)
  })
})
