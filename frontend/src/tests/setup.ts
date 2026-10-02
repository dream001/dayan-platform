import { afterEach } from 'vitest'

afterEach(() => {
  window.localStorage.clear()
  document.body.innerHTML = ''
})
