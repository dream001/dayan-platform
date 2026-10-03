import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useLayoutStore } from './layout'

describe('layout store', () => {
  beforeEach(() => {
    window.localStorage.clear()
    delete document.documentElement.dataset.skin
    setActivePinia(createPinia())
  })

  it('toggles and persists the desktop navigation state', () => {
    const store = useLayoutStore()

    expect(store.isSidebarCollapsed).toBe(false)
    expect(store.sidebarWidth).toBe('252px')

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

  it('applies and persists the selected interface skin', () => {
    const store = useLayoutStore()

    expect(store.skin).toBe('titanium')
    expect(document.documentElement.dataset.skin).toBe('titanium')

    store.setSkin('orbit')

    expect(store.skin).toBe('orbit')
    expect(document.documentElement.dataset.skin).toBe('orbit')
    expect(window.localStorage.getItem('dayan:skin')).toBe('orbit')
  })
})
