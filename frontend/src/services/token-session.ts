import type { AuthTokens } from '@/types/auth'

const REFRESH_TOKEN_KEY = 'dayan:refresh-token'

let accessToken: string | null = null

export function getAccessToken() {
  return accessToken
}

export function getRefreshToken() {
  return window.localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setTokenSession(tokens: AuthTokens) {
  accessToken = tokens.accessToken
  window.localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
}

export function clearTokenSession() {
  accessToken = null
  window.localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export function hasRefreshToken() {
  return Boolean(getRefreshToken())
}
