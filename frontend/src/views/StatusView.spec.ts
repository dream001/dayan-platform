import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import StatusView from './StatusView.vue'

describe('StatusView', () => {
  it.each([
    ['401', '登录已失效', '重新登录'],
    ['403', '无权访问', '返回上一页'],
    ['404', '页面不存在', '返回工作台'],
  ] as const)('renders the %s feedback page', (status, title, action) => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div />' } }],
    })
    const wrapper = mount(StatusView, {
      props: { status },
      global: { plugins: [router] },
    })

    expect(wrapper.get('h1').text()).toBe(title)
    expect(wrapper.get('button').text()).toBe(action)
  })
})
