import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    title?: string
    eyebrow?: string
    titleKey?: string
    eyebrowKey?: string
    permission?: string
  }
}
