import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import WorkspaceView from './WorkspaceView.vue'

describe('WorkspaceView', () => {
  it('renders a truthful empty state while backend data is unavailable', () => {
    const wrapper = mount(WorkspaceView)

    expect(wrapper.get('h1').text()).toBe('工作台')
    expect(wrapper.text()).toContain('暂无可展示的数据')
    expect(wrapper.text()).toContain('当前不使用模拟数据')
    expect(wrapper.text()).toContain('等待后端服务')
  })
})
