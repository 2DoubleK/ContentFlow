import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import GenerateView from './GenerateView.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '1' } }),
  useRouter: () => ({ back: vi.fn(), push: vi.fn() })
}))

describe('GenerateView', () => {
  it('renders generate command button', () => {
    const wrapper = mount(GenerateView, {
      global: {
        stubs: {
          'el-input': { template: '<textarea />' },
          'el-button': { template: '<button><slot /></button>' }
        }
      }
    })

    expect(wrapper.text()).toContain('生成并保存')
  })
})
