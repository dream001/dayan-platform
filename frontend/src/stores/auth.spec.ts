import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getAccessToken, getRefreshToken } from '@/services/token-session'
import * as authService from '@/services/auth'
import { useAuthStore } from './auth'

vi.mock('@/services/auth', () => ({
  login: vi.fn(),
  getCurrentUser: vi.fn(),
  getCurrentMenus: vi.fn(),
  logout: vi.fn(),
}))

const mockedAuthService = vi.mocked(authService)
const user = {
  id: 1,
  username: 'operator',
  displayName: '平台管理员',
  email: 'operator@example.com',
  phone: null,
  departmentId: null,
  permissions: ['system:user:view', 'system:user:create'],
}
const menus = [{
  id: 1110,
  parentId: 1100,
  type: 'MENU' as const,
  name: '用户管理',
  code: 'system:user:view',
  path: '/system/users',
  component: 'system/user/index',
  icon: 'users',
  sortOrder: 10,
  visible: true,
  enabled: true,
  children: [],
}]

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads the profile, menu and permissions after login', async () => {
    mockedAuthService.login.mockResolvedValue({
      tokenType: 'Bearer',
      accessToken: 'access',
      refreshToken: 'refresh',
      expiresIn: 900,
      user,
    })
    mockedAuthService.getCurrentMenus.mockResolvedValue(menus)
    const store = useAuthStore()

    await store.signIn({ username: 'operator', password: 'secret' })

    expect(store.isAuthenticated).toBe(true)
    expect(store.displayName).toBe('平台管理员')
    expect(store.navigation[0]?.path).toBe('/system/users')
    expect(store.navigation[0]?.permission).toBe('system:user:view')
    expect(store.hasPermission(['system:user:view', 'system:user:create'])).toBe(true)
    expect(mockedAuthService.getCurrentUser).not.toHaveBeenCalled()
    expect(getAccessToken()).toBe('access')
    expect(getRefreshToken()).toBe('refresh')
  })

  it('clears local authentication even when remote logout fails', async () => {
    mockedAuthService.login.mockResolvedValue({
      tokenType: 'Bearer',
      accessToken: 'access',
      refreshToken: 'refresh',
      expiresIn: 900,
      user,
    })
    mockedAuthService.getCurrentMenus.mockResolvedValue([])
    mockedAuthService.logout.mockRejectedValue(new Error('offline'))
    const store = useAuthStore()
    await store.signIn({ username: 'operator', password: 'secret' })

    await expect(store.signOut()).rejects.toThrow('offline')

    expect(store.isAuthenticated).toBe(false)
    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
    expect(mockedAuthService.logout).toHaveBeenCalledWith('refresh')
  })

  it('restores the user and menus through the refresh-capable API boundary', async () => {
    window.localStorage.setItem('dayan:refresh-token', 'persisted-refresh')
    mockedAuthService.getCurrentUser.mockResolvedValue(user)
    mockedAuthService.getCurrentMenus.mockResolvedValue(menus)
    const store = useAuthStore()

    await expect(store.restore()).resolves.toBe(true)

    expect(store.profile).toEqual({ user, menus })
    expect(store.navigation[0]?.permission).toBe('system:user:view')
  })
})
