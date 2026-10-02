import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it } from 'vitest'
import { permissionDirective } from './permission'
import { useAuthStore } from '@/stores/auth'

const ProtectedButton = defineComponent({
  props: {
    permission: {
      type: String,
      required: true,
    },
  },
  template: '<button v-permission="permission">新增用户</button>',
})

describe('permission directive', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('keeps an action only when the exact backend permission code is granted', () => {
    const auth = useAuthStore()
    auth.permissions = new Set(['system:user:create'])

    const wrapper = mount(ProtectedButton, {
      props: { permission: 'system:user:create' },
      global: {
        directives: { permission: permissionDirective },
      },
    })

    expect(wrapper.find('button').exists()).toBe(true)
  })

  it('removes an action when the permission code is absent', () => {
    const wrapper = mount(ProtectedButton, {
      props: { permission: 'system:user:delete' },
      global: {
        directives: { permission: permissionDirective },
      },
    })

    expect(wrapper.get('button').element.parentElement).toBeNull()
  })
})
