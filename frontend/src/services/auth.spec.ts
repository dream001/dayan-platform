import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import { getCurrentMenus, getCurrentUser, login, logout } from './auth'
import { http } from './http'

const defaultAdapter = http.defaults.adapter
const user = {
  id: 1,
  username: 'operator',
  displayName: '平台管理员',
  email: null,
  phone: null,
  departmentId: null,
  permissions: ['dashboard:view'],
}
const menu = {
  id: 1000,
  parentId: null,
  type: 'MENU' as const,
  name: '工作台',
  code: 'dashboard:view',
  path: '/dashboard',
  component: 'dashboard/index',
  icon: 'dashboard',
  sortOrder: 10,
  visible: true,
  enabled: true,
  children: [],
}

function apiResponse(config: InternalAxiosRequestConfig, data: unknown) {
  return {
    config,
    data: {
      code: 'OK',
      message: 'Success',
      data,
      requestId: 'request-1',
      timestamp: '2026-10-02T00:00:00Z',
    },
    status: 200,
    statusText: 'OK',
    headers: {},
  }
}

describe('auth API contract', () => {
  afterEach(() => {
    http.defaults.adapter = defaultAdapter
  })

  it('uses the AuthController login fields and TokenView response', async () => {
    const adapter: AxiosAdapter = async (config) => {
      expect(config.url).toBe('/auth/login')
      expect(JSON.parse(String(config.data))).toEqual({
        username: 'operator',
        password: 'secret',
      })
      return apiResponse(config, {
        tokenType: 'Bearer',
        accessToken: 'access',
        expiresIn: 900,
        refreshToken: 'refresh',
        user,
      })
    }
    http.defaults.adapter = adapter

    await expect(login({ username: 'operator', password: 'secret' })).resolves.toEqual({
      tokenType: 'Bearer',
      accessToken: 'access',
      expiresIn: 900,
      refreshToken: 'refresh',
      user,
    })
  })

  it('loads current user and menus from their separate real endpoints', async () => {
    http.defaults.adapter = async (config) => {
      if (config.url === '/auth/me') return apiResponse(config, user)
      if (config.url === '/auth/me/menus') return apiResponse(config, [menu])
      throw new Error(`Unexpected endpoint: ${config.url}`)
    }

    await expect(Promise.all([getCurrentUser(), getCurrentMenus()])).resolves.toEqual([
      user,
      [menu],
    ])
  })

  it('sends the current refresh token in the logout request body', async () => {
    http.defaults.adapter = async (config) => {
      expect(config.url).toBe('/auth/logout')
      expect(JSON.parse(String(config.data))).toEqual({ refreshToken: 'refresh' })
      return apiResponse(config, null)
    }

    await expect(logout('refresh')).resolves.toBeUndefined()
  })
})
