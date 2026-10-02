export interface AuthTokens {
  tokenType: string
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: AuthUser
}

export interface AuthUser {
  id: number
  username: string
  displayName: string
  email: string | null
  phone: string | null
  departmentId: number | null
  permissions: string[]
}

export interface BackendMenu {
  id: number
  parentId: number | null
  type: 'MENU' | 'BUTTON'
  name: string
  code: string | null
  path: string | null
  component: string | null
  icon: string | null
  sortOrder: number
  visible: boolean
  enabled: boolean
  children: BackendMenu[]
}

export interface NavigationItem {
  id: string
  label: string
  code?: string
  path?: string
  icon?: string
  permission?: string
  children: NavigationItem[]
}

export interface AuthProfile {
  user: AuthUser
  menus: BackendMenu[]
}

export interface LoginPayload {
  username: string
  password: string
}
