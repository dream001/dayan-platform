import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as adminService from '@/services/admin'
import WorkspaceView from './WorkspaceView.vue'

vi.mock('@/services/admin', () => ({
  getDashboardStatistics: vi.fn(),
}))

const mockedAdminService = vi.mocked(adminService)
const statistics = {
  totalUsers: 18,
  enabledUsers: 16,
  totalFiles: 7,
  totalFileSizeBytes: 2048,
  recentOperationCount: 3,
  recentOperations: [],
  generatedAt: '2026-10-02T10:00:00Z',
}

function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: WorkspaceView },
      { path: '/audit/logs', component: { template: '<div />' } },
    ],
  })
  return mount(WorkspaceView, {
    global: { plugins: [ElementPlus, router] },
  })
}

describe('WorkspaceView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders statistics returned by the real dashboard contract', async () => {
    mockedAdminService.getDashboardStatistics.mockResolvedValue(statistics)
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('工作台')
    expect(wrapper.text()).toContain('18')
    expect(wrapper.text()).toContain('16 个已启用')
    expect(wrapper.text()).toContain('2.0 KB')
    expect(wrapper.text()).toContain('暂无近期操作')
  })

  it('renders retryable feedback when the dashboard request fails', async () => {
    mockedAdminService.getDashboardStatistics.mockRejectedValue(new Error('offline'))
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('工作台加载失败')
    expect(wrapper.text()).toContain('offline')
  })
})
