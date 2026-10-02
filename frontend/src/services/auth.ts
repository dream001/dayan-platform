import type { ApiResponse } from '@/types/api'
import type { AuthTokens, AuthUser, BackendMenu, LoginPayload } from '@/types/auth'
import { http } from './http'

export async function login(payload: LoginPayload) {
  const response = await http.post<ApiResponse<AuthTokens>>('/auth/login', payload)
  return response.data.data
}

export async function getCurrentUser() {
  const response = await http.get<ApiResponse<AuthUser>>('/auth/me')
  return response.data.data
}

export async function getCurrentMenus() {
  const response = await http.get<ApiResponse<BackendMenu[]>>('/auth/me/menus')
  return response.data.data
}

export async function logout(refreshToken: string) {
  await http.post<ApiResponse<null>>('/auth/logout', { refreshToken })
}
