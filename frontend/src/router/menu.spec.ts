import { describe, expect, it } from 'vitest'
import { flattenNavigation, normalizeMenus } from './menu'

describe('dynamic navigation', () => {
  it('maps the backend MenuNode contract and exact permission codes', () => {
    const navigation = normalizeMenus([
      {
        id: 1100,
        parentId: null,
        type: 'MENU',
        name: '系统管理',
        code: 'system:view',
        path: '/system',
        component: 'Layout',
        icon: 'settings',
        sortOrder: 20,
        visible: true,
        enabled: true,
        children: [
          {
            id: 1110,
            parentId: 1100,
            type: 'MENU',
            name: '用户管理',
            code: 'system:user:view',
            path: '/system/users',
            component: 'system/user/index',
            icon: 'users',
            sortOrder: 10,
            visible: true,
            enabled: true,
            children: [
              {
                id: 1111,
                parentId: 1110,
                type: 'BUTTON',
                name: '新增用户',
                code: 'system:user:create',
                path: null,
                component: null,
                icon: null,
                sortOrder: 10,
                visible: false,
                enabled: true,
                children: [],
              },
            ],
          },
        ],
      },
    ], new Set(['system:user:view', 'system:user:create']))

    expect(navigation).toHaveLength(1)
    expect(navigation[0]?.permission).toBeUndefined()
    expect(navigation[0]?.children).toEqual([
      expect.objectContaining({
        label: '用户管理',
        path: '/system/users',
        permission: 'system:user:view',
        children: [],
      }),
    ])
    expect(flattenNavigation(navigation)).toHaveLength(2)
  })

  it('rejects disabled and external menu entries', () => {
    expect(normalizeMenus([
      {
        id: 1,
        parentId: null,
        type: 'MENU',
        name: '外部地址',
        code: 'external:view',
        path: 'https://example.com',
        component: null,
        icon: null,
        sortOrder: 1,
        visible: true,
        enabled: true,
        children: [],
      },
      {
        id: 2,
        parentId: null,
        type: 'MENU',
        name: '停用菜单',
        code: 'disabled:view',
        path: '/disabled',
        component: null,
        icon: null,
        sortOrder: 2,
        visible: true,
        enabled: false,
        children: [],
      },
    ])).toEqual([])
  })
})
