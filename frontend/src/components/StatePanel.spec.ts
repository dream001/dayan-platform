import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StatePanel from './StatePanel.vue'

describe('StatePanel', () => {
  it.each([
    ['loading', '正在加载'],
    ['empty', '暂无数据'],
    ['error', '加载失败'],
  ] as const)('renders the unified %s state', (state, expectedText) => {
    const wrapper = mount(StatePanel, { props: { state } })

    if (state === 'loading') {
      expect(wrapper.get('[aria-label="正在加载"]').attributes('aria-label')).toBe('正在加载')
      expect(wrapper.get('section').attributes('aria-busy')).toBe('true')
    } else {
      expect(wrapper.text()).toContain(expectedText)
    }
  })

  it('emits retry from an error state', async () => {
    const wrapper = mount(StatePanel, {
      props: {
        state: 'error',
        title: '请求失败',
        description: '请检查网络后重试。',
      },
    })

    await wrapper.get('button').trigger('click')

    expect(wrapper.text()).toContain('请求失败')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
