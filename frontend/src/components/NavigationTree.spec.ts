import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import type { NavigationItem } from '@/types/auth'
import NavigationTree from './NavigationTree.vue'

const items: NavigationItem[] = [
  {
    id: 'system',
    label: '系统管理',
    code: 'system:view',
    path: '/system',
    icon: 'settings',
    children: [
      {
        id: 'users',
        label: '用户管理',
        code: 'system:user:view',
        path: '/system/users',
        icon: 'users',
        children: [],
      },
    ],
  },
]

async function mountTree(initialPath = '/') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/system', component: { template: '<div />' } },
      { path: '/system/users', component: { template: '<div />' } },
    ],
  })
  await router.push(initialPath)
  await router.isReady()

  return {
    router,
    wrapper: mount(NavigationTree, {
      props: { items },
      global: { plugins: [router] },
    }),
  }
}

describe('NavigationTree', () => {
  it('starts collapsed and expands a branch from the parent row', async () => {
    const { wrapper } = await mountTree()
    const branch = wrapper.get('button[aria-expanded="false"]')

    expect(wrapper.text()).not.toContain('用户管理')
    await branch.trigger('click')

    expect(branch.attributes('aria-expanded')).toBe('true')
    expect(wrapper.text()).toContain('用户管理')
  })

  it('opens the branch when a child route becomes active', async () => {
    const { wrapper } = await mountTree('/system/users')
    await flushPromises()
    const branch = wrapper.get('button[aria-expanded="true"]')

    expect(branch.attributes('aria-expanded')).toBe('true')
    expect(wrapper.text()).toContain('用户管理')
  })
})
