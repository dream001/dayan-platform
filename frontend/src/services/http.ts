import axios, {
  AxiosError,
  AxiosHeaders,
  type InternalAxiosRequestConfig,
} from 'axios'
import type { ApiResponse } from '@/types/api'
import type { AuthTokens } from '@/types/auth'
import {
  clearTokenSession,
  getAccessToken,
  getRefreshToken,
  setTokenSession,
} from './token-session'

const DEFAULT_TIMEOUT = 15_000
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: DEFAULT_TIMEOUT,
  headers: {
    Accept: 'application/json',
  },
})

export const authRefreshClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: DEFAULT_TIMEOUT,
  headers: {
    Accept: 'application/json',
  },
})

interface RetryableRequest extends InternalAxiosRequestConfig {
  _retry?: boolean
}

let refreshPromise: Promise<string> | null = null
let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler
}

function isAuthRequest(url?: string) {
  return Boolean(url && ['/auth/login', '/auth/refresh'].some((path) => url.endsWith(path)))
}

function isLogoutRequest(url?: string) {
  return Boolean(url?.endsWith('/auth/logout'))
}

async function refreshAccessToken() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error('Missing refresh token')
  }

  const response = await authRefreshClient.post<ApiResponse<AuthTokens>>('/auth/refresh', {
    refreshToken,
  })
  setTokenSession(response.data.data)
  return response.data.data.accessToken
}

function getRefreshPromise() {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers = AxiosHeaders.from(config.headers)
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const request = error.config as RetryableRequest | undefined
    if (error.response?.status !== 401 || !request || request._retry || isAuthRequest(request.url)) {
      return Promise.reject(error)
    }

    request._retry = true
    try {
      const token = await getRefreshPromise()
      request.headers = AxiosHeaders.from(request.headers)
      request.headers.set('Authorization', `Bearer ${token}`)
      if (isLogoutRequest(request.url)) {
        request.data = JSON.stringify({ refreshToken: getRefreshToken() })
      }
      return await http(request)
    } catch (refreshError) {
      clearTokenSession()
      unauthorizedHandler?.()
      return Promise.reject(refreshError)
    }
  },
)
