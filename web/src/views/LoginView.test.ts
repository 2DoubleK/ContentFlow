import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import LoginView from './LoginView.vue'

const auth = vi.hoisted(() => ({
  login: vi.fn().mockResolvedValue(undefined),
  register: vi.fn().mockResolvedValue(undefined)
}))

vi.mock('../stores/auth', () => ({
  useAuthStore: () => auth
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn().mockResolvedValue(undefined) })
}))

const InputStub = defineComponent({
  inheritAttrs: false,
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
})

const ButtonStub = defineComponent({
  inheritAttrs: false,
  emits: ['click'],
  template: '<button @click.stop="$emit(\'click\')"><slot /></button>'
})

describe('LoginView', () => {
  it('switches the account panel heading when opening registration', async () => {
    const wrapper = mount(LoginView, {
      global: {
        stubs: {
          'el-form': { template: '<form><slot /></form>' },
          'el-form-item': { template: '<label><slot /></label>' },
          'el-input': InputStub,
          'el-button': ButtonStub,
          RouterLink: { template: '<a><slot /></a>' }
        }
      }
    })

    expect(wrapper.get('[data-testid="account-heading"]').text()).toBe('登录 ContentFlow')
    await wrapper.find('.mode-tabs button:nth-child(2)').trigger('click')

    expect(wrapper.get('[data-testid="account-heading"]').text()).toBe('注册 ContentFlow')
  })

  it('rejects mismatched registration passwords before sending a request', async () => {
    auth.register.mockClear()
    const wrapper = mount(LoginView, {
      global: {
        stubs: {
          'el-form': { template: '<form><slot /></form>' },
          'el-form-item': { template: '<label><slot /></label>' },
          'el-input': InputStub,
          'el-button': ButtonStub,
          RouterLink: { template: '<a><slot /></a>' }
        }
      }
    })

    await wrapper.find('.mode-tabs button:nth-child(2)').trigger('click')
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('writer')
    await inputs[1].setValue('secret1')
    await inputs[2].setValue('secret2')
    await inputs[3].setValue('writer@example.com')
    await wrapper.findAll('button')[3].trigger('click')

    expect(auth.register).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('两次输入的密码不一致')
  })
})
