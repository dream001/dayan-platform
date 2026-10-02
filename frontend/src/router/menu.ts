import type { BackendMenu, NavigationItem } from '@/types/auth'

function safePath(value?: string) {
  if (!value) return undefined
  const path = value.startsWith('/') ? value : `/${value}`
  return path.startsWith('//') || path.includes('://') ? undefined : path
}

export function normalizeMenus(
  menus: BackendMenu[] = [],
  permissions: ReadonlySet<string> = new Set(),
): NavigationItem[] {
  return menus.flatMap((menu) => {
    if (menu.type !== 'MENU' || !menu.enabled || !menu.visible) return []

    const children = normalizeMenus(menu.children, permissions)
    const path = safePath(menu.path ?? undefined)
    const permission = menu.code && permissions.has(menu.code) ? menu.code : undefined
    if (!menu.name || (!path && children.length === 0)) return []

    return [{
      id: String(menu.id),
      label: menu.name,
      path,
      icon: menu.icon ?? undefined,
      permission,
      children,
    }]
  })
}

export function flattenNavigation(items: NavigationItem[]): NavigationItem[] {
  return items.flatMap((item) => [item, ...flattenNavigation(item.children)])
}
