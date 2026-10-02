import type { App, DirectiveBinding } from 'vue'
import { useAuthStore } from '@/stores/auth'

type PermissionValue = string | string[]

function applyPermission(element: HTMLElement, binding: DirectiveBinding<PermissionValue>) {
  const auth = useAuthStore()
  if (!binding.value || auth.hasPermission(binding.value)) return
  element.remove()
}

export const permissionDirective = {
  mounted: applyPermission,
  updated: applyPermission,
}

export function installPermissionDirective(app: App) {
  app.directive('permission', permissionDirective)
}

declare module 'vue' {
  interface ComponentCustomProperties {
    vPermission: typeof permissionDirective
  }
}
