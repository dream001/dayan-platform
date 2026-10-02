import { watch } from 'vue'
import { createI18n } from 'vue-i18n'
import elEn from 'element-plus/es/locale/lang/en'
import elJa from 'element-plus/es/locale/lang/ja'
import elZh from 'element-plus/es/locale/lang/zh-cn'
import enUS from './locales/en-US'
import jaJP from './locales/ja-JP'
import zhCN from './locales/zh-CN'

export type AppLocale = 'zh-CN' | 'en-US' | 'ja-JP'

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US', 'ja-JP'] as const satisfies readonly AppLocale[]

export type MessageSchema = typeof zhCN

const STORAGE_KEY = 'app-language'

function detectLocale(): AppLocale {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved && (SUPPORTED_LOCALES as readonly string[]).includes(saved)) {
    return saved as AppLocale
  }
  const preferred = navigator.language.toLowerCase()
  if (preferred.startsWith('ja')) return 'ja-JP'
  if (preferred.startsWith('en')) return 'en-US'
  if (preferred.startsWith('zh')) return 'zh-CN'
  return 'zh-CN'
}

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: detectLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
    'ja-JP': jaJP,
  },
})

watch(i18n.global.locale, (value) => {
  localStorage.setItem(STORAGE_KEY, value)
})

export function setLocale(value: AppLocale) {
  i18n.global.locale.value = value
}

export function getActiveLocale(): string {
  return i18n.global.locale.value
}

/** 翻译后端菜单权限码；无对应词条时回退到后端返回名称。 */
export function translateMenu(code: string | undefined, fallback: string): string {
  if (!code) return fallback
  const key = `menu.${code}`
  const translated = i18n.global.t(key)
  return translated === key ? fallback : translated
}

export const elementLocales: Record<AppLocale, typeof elZh> = {
  'zh-CN': elZh,
  'en-US': elEn,
  'ja-JP': elJa,
}
