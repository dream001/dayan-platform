import { config } from '@vue/test-utils'
import { afterEach, beforeEach } from 'vitest'
import { i18n } from '@/i18n'

config.global.plugins = [i18n]

beforeEach(() => {
  i18n.global.locale.value = 'zh-CN'
})

afterEach(() => {
  window.localStorage.clear()
  document.body.innerHTML = ''
})
