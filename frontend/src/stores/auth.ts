import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authService from '@/services/auth'
import {
  clearTokenSession,
  getRefreshToken,
  hasRefreshToken,
  setTokenSession,
} from '@/services/token-session'
import { normalizeMenus } from '@/router/menu'
import type { AuthProfile, AuthUser, BackendMenu, LoginPayload } from '@/types/auth'

type AuthStatus = 'idle' | 'loading' | 'authenticated' | 'anonymous'

export const useAuthStore = defineStore('auth', () => {
  const status = ref<AuthStatus>('idle')
  const profile = ref<AuthProfile | null>(null)
  const permissions = ref<Set<string>>(new Set())
  let restorePromise: Promise<boolean> | null = null

  const isAuthenticated = computed(() => status.value === 'authenticated')
  const navigation = computed(() => normalizeMenus(profile.value?.menus, permissions.value))
  const displayName = computed(() => profile.value?.user.displayName ?? profile.value?.user.username ?? '')

  function applyProfile(user: AuthUser, menus: BackendMenu[]) {
    profile.value = {
      user: {
        ...user,
        permissions: Array.isArray(user.permissions) ? user.permissions : [],
      },
      menus: Array.isArray(menus) ? menus : [],
    }
    permissions.value = new Set(profile.value.user.permissions)
    status.value = 'authenticated'
  }

  function reset() {
    clearTokenSession()
    profile.value = null
    permissions.value = new Set()
    status.value = 'anonymous'
  }

  async function fetchProfile() {
    const [user, menus] = await Promise.all([
      authService.getCurrentUser(),
      authService.getCurrentMenus(),
    ])
    applyProfile(user, menus)
    return profile.value
  }

  async function signIn(payload: LoginPayload) {
    status.value = 'loading'
    try {
      const tokens = await authService.login(payload)
      setTokenSession(tokens)
      const menus = await authService.getCurrentMenus()
      applyProfile(tokens.user, menus)
    } catch (error) {
      reset()
      throw error
    }
  }

  async function restore() {
    if (status.value === 'authenticated') return true
    if (!hasRefreshToken()) {
      status.value = 'anonymous'
      return false
    }
    if (restorePromise) return restorePromise

    status.value = 'loading'
    restorePromise = fetchProfile()
      .then(() => true)
      .catch(() => {
        reset()
        return false
      })
      .finally(() => {
        restorePromise = null
      })
    return restorePromise
  }

  async function signOut() {
    const refreshToken = getRefreshToken()
    try {
      if (refreshToken) await authService.logout(refreshToken)
    } finally {
      reset()
    }
  }

  function hasPermission(required: string | string[]) {
    const values = Array.isArray(required) ? required : [required]
    return values.length === 0 || values.every((value) => permissions.value.has(value))
  }

  return {
    status,
    profile,
    permissions,
    navigation,
    displayName,
    isAuthenticated,
    signIn,
    fetchProfile,
    restore,
    signOut,
    reset,
    hasPermission,
  }
})
