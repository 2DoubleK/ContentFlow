import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import ProjectsView from './ProjectsView.vue'

const projectApi = vi.hoisted(() => ({
  list: vi.fn().mockResolvedValue({ data: { data: [] } }),
  create: vi.fn()
}))

vi.mock('../api/projects', () => ({
  projectApi
}))

vi.mock('../stores/auth', () => ({
  useAuthStore: () => ({ user: { username: 'writer' }, logout: vi.fn() })
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

const ButtonStub = defineComponent({
  inheritAttrs: false,
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>'
})

const DialogStub = defineComponent({
  props: ['modelValue', 'title'],
  template: '<section v-if="modelValue" role="dialog"><slot name="header"><h2>{{ title }}</h2></slot><slot /></section>'
})

describe('ProjectsView', () => {
  it('opens an in-app create-project dialog', async () => {
    const wrapper = mount(ProjectsView, {
      global: {
        stubs: {
          'el-button': ButtonStub,
          'el-dialog': DialogStub,
          'el-form': { template: '<form><slot /></form>' },
          'el-form-item': { props: ['label'], template: '<label>{{ label }}<slot /></label>' },
          'el-input': { template: '<input />' }
        }
      }
    })

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    await wrapper.get('.projects-topbar button').trigger('click')

    expect(wrapper.get('[role="dialog"]').text()).toContain('AI 帮你创建项目')
    expect(wrapper.get('[role="dialog"]').text()).toContain('描述你想创建的项目')
  })
})
