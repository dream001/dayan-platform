import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { authRefreshClient, http, setUnauthorizedHandler } from './http'
import {
  clearTokenSession,
  getAccessToken,
  getRefreshToken,
  setTokenSession,
} from './token-session'

const defaultHttpAdapter = http.defaults.adapter
const defaultRefreshAdapter = authRefreshClient.defaults.adapter
const user = {
  id: 1,
  username: 'operator',
  displayName: '平台管理员',
  email: null,
  phone: null,
  departmentId: null,
  permissions: ['system:user:view'],
}

function response(
  config: InternalAxiosRequestConfig,
  data: unknown,
  status = 200,
): AxiosResponse {
  return {
    config,
    data,
    status,
    statusText: status === 200 ? 'OK' : 'Unauthorized',
    headers: {},
  }
}

describe('http authentication', () => {
  afterEach(() => {
    clearTokenSession()
    setUnauthorizedHandler(null)
    http.defaults.adapter = defaultHttpAdapter
    authRefreshClient.defaults.adapter = defaultRefreshAdapter
  })

  it('keeps the access token in memory and persists only the refresh token', () => {
    setTokenSession({
      tokenType: 'Bearer',
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      expiresIn: 900,
      user,
    })

    expect(getAccessToken()).toBe('access-token')
    expect(getRefreshToken()).toBe('refresh-token')
    expect(window.localStorage.getItem('dayan:refresh-token')).toBe('refresh-token')
    expect(window.localStorage.getItem('dayan:access-token')).toBeNull()
  })

  it('shares one refresh request across concurrent 401 responses and retries once', async () => {
    setTokenSession({
      tokenType: 'Bearer',
      accessToken: 'expired',
      refreshToken: 'refresh-token',
      expiresIn: 1,
      user,
    })
    let refreshCount = 0

    const requestAdapter: AxiosAdapter = async (config) => {
      const retryable = config as InternalAxiosRequestConfig & { _retry?: boolean }
      if (!retryable._retry) {
        return Promise.reject({
          config,
          response: response(config, null, 401),
        })
      }
      return response(config, { data: config.url })
    }
    const refreshAdapter: AxiosAdapter = async (config) => {
      refreshCount += 1
      await Promise.resolve()
      return response(config, {
        data: {
          tokenType: 'Bearer',
          accessToken: 'renewed',
          refreshToken: 'rotated',
          expiresIn: 900,
          user,
        },
      })
    }

    http.defaults.adapter = requestAdapter
    authRefreshClient.defaults.adapter = refreshAdapter

    const [first, second] = await Promise.all([http.get('/one'), http.get('/two')])

    expect(first.data.data).toBe('/one')
    expect(second.data.data).toBe('/two')
    expect(refreshCount).toBe(1)
    expect(getAccessToken()).toBe('renewed')
    expect(getRefreshToken()).toBe('rotated')
  })

  it('clears the rotated session and reports unauthorized when refresh fails', async () => {
    setTokenSession({
      tokenType: 'Bearer',
      accessToken: 'expired',
      refreshToken: 'refresh-token',
      expiresIn: 1,
      user,
    })
    const unauthorized = vi.fn()
    setUnauthorizedHandler(unauthorized)
    http.defaults.adapter = async (config) => Promise.reject({
      config,
      response: response(config, null, 401),
    })
    authRefreshClient.defaults.adapter = async (config) => {
      expect(JSON.parse(String(config.data))).toEqual({ refreshToken: 'refresh-token' })
      return Promise.reject({
        config,
        response: response(config, null, 401),
      })
    }

    await expect(http.get('/protected')).rejects.toBeTruthy()

    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
    expect(unauthorized).toHaveBeenCalledOnce()
  })

  it('replaces the logout body with the rotated refresh token before retrying', async () => {
    setTokenSession({
      tokenType: 'Bearer',
      accessToken: 'expired',
      refreshToken: 'old-refresh',
      expiresIn: 1,
      user,
    })
    http.defaults.adapter = async (config) => {
      const request = config as InternalAxiosRequestConfig & { _retry?: boolean }
      if (!request._retry) {
        return Promise.reject({
          config,
          response: response(config, null, 401),
        })
      }
      expect(JSON.parse(String(config.data))).toEqual({ refreshToken: 'rotated-refresh' })
      return response(config, { data: null })
    }
    authRefreshClient.defaults.adapter = async (config) => response(config, {
      data: {
        tokenType: 'Bearer',
        accessToken: 'renewed',
        refreshToken: 'rotated-refresh',
        expiresIn: 900,
        user,
      },
    })

    await expect(http.post('/auth/logout', {
      refreshToken: 'old-refresh',
    })).resolves.toBeTruthy()
  })
})
