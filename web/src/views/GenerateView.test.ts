import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { conversationApi } from '../api/conversations'
import GenerateView from './GenerateView.vue'

const push = vi.fn()
const route = reactive({ params: { id: '7' } })

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ back: vi.fn(), push })
}))

vi.mock('../api/conversations', () => ({
  conversationApi: {
    create: vi.fn(),
    list: vi.fn(),
    messages: vi.fn(),
    send: vi.fn()
  }
}))

const conversation = {
  id: 4,
  projectId: 7,
  threadId: 'd91eb9dc-3df2-440c-b5f8-8ab65c22391a',
  title: 'JWT content',
  status: 'ACTIVE',
  createdAt: '2026-07-25T04:00:00+08:00',
  updatedAt: '2026-07-25T04:00:00+08:00'
}

describe('GenerateView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.params.id = '7'
    vi.mocked(conversationApi.list).mockResolvedValue({ data: { success: true, data: [conversation] } } as never)
    vi.mocked(conversationApi.messages).mockResolvedValue({
      data: {
        success: true,
        data: [{
          id: 11,
          conversationId: 4,
          role: 'ASSISTANT',
          content: '# Existing reply',
          metadata: null,
          createdAt: '2026-07-25T04:00:00+08:00'
        }]
      }
    } as never)
  })

  it('selects the first conversation and renders ordered history', async () => {
    const wrapper = mount(GenerateView)
    await flushPromises()

    expect(conversationApi.list).toHaveBeenCalledWith(7)
    expect(conversationApi.messages).toHaveBeenCalledWith(4)
    expect(wrapper.text()).toContain('JWT content')
    expect(wrapper.text()).toContain('# Existing reply')
  })

  it('creates a conversation inline and selects it', async () => {
    vi.mocked(conversationApi.list).mockResolvedValue({ data: { success: true, data: [] } } as never)
    vi.mocked(conversationApi.create).mockResolvedValue({ data: { success: true, data: conversation } } as never)
    const wrapper = mount(GenerateView)
    await flushPromises()

    await wrapper.get('[data-testid="new-title"]').setValue('JWT content')
    await wrapper.get('[data-testid="create-conversation"]').trigger('click')
    await flushPromises()

    expect(conversationApi.create).toHaveBeenCalledWith({ projectId: 7, title: 'JWT content' })
    expect(conversationApi.messages).toHaveBeenCalledWith(4)
    expect(wrapper.text()).toContain('JWT content')
  })

  it('sends a message and navigates to the saved draft', async () => {
    vi.mocked(conversationApi.messages).mockResolvedValue({ data: { success: true, data: [] } } as never)
    vi.mocked(conversationApi.send).mockResolvedValue({
      data: {
        success: true,
        data: {
          userMessage: { id: 12, conversationId: 4, role: 'USER', content: 'Write JWT', metadata: null },
          assistantMessage: { id: 13, conversationId: 4, role: 'ASSISTANT', content: '# JWT', metadata: '{}' },
          savedDraftId: 31
        }
      }
    } as never)
    const wrapper = mount(GenerateView)
    await flushPromises()

    await wrapper.get('[data-testid="composer"]').setValue('Write JWT')
    await wrapper.get('[data-testid="send-message"]').trigger('click')
    await flushPromises()

    expect(conversationApi.send).toHaveBeenCalledWith(4, 'Write JWT', expect.any(String))
    expect(wrapper.text()).toContain('# JWT')
    await wrapper.get('[data-testid="open-draft"]').trigger('click')
    expect(push).toHaveBeenCalledWith('/contents/31')
  })

  it('ignores stale message responses after switching conversations', async () => {
    const secondConversation = { ...conversation, id: 5, title: 'Second conversation' }
    vi.mocked(conversationApi.list).mockResolvedValue({
      data: { success: true, data: [conversation, secondConversation] }
    } as never)
    let resolveFirst: (value: unknown) => void = () => undefined
    vi.mocked(conversationApi.messages)
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve }) as never)
      .mockResolvedValueOnce({
        data: { success: true, data: [{ id: 21, conversationId: 5, role: 'ASSISTANT', content: 'Second reply' }] }
      } as never)
    const wrapper = mount(GenerateView)
    await flushPromises()

    await wrapper.get('[data-conversation-id="5"]').trigger('click')
    await flushPromises()
    resolveFirst({
      data: { success: true, data: [{ id: 11, conversationId: 4, role: 'ASSISTANT', content: 'Stale reply' }] }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Second reply')
    expect(wrapper.text()).not.toContain('Stale reply')
  })

  it('reloads conversations when the route project changes', async () => {
    const wrapper = mount(GenerateView)
    await flushPromises()

    route.params.id = '8'
    await flushPromises()

    expect(conversationApi.list).toHaveBeenCalledWith(8)
    wrapper.unmount()
  })

  it('reuses the request id when retrying a failed message', async () => {
    vi.mocked(conversationApi.messages).mockResolvedValue({ data: { success: true, data: [] } } as never)
    vi.mocked(conversationApi.send)
      .mockRejectedValueOnce(new Error('timeout'))
      .mockResolvedValueOnce({
        data: {
          success: true,
          data: {
            userMessage: { id: 12, conversationId: 4, role: 'USER', content: 'Write JWT', metadata: null },
            assistantMessage: { id: 13, conversationId: 4, role: 'ASSISTANT', content: '# JWT', metadata: '{}' },
            savedDraftId: null
          }
        }
      } as never)
    const wrapper = mount(GenerateView)
    await flushPromises()
    await wrapper.get('[data-testid="composer"]').setValue('Write JWT')

    await wrapper.get('[data-testid="send-message"]').trigger('click')
    await flushPromises()
    const firstRequestId = vi.mocked(conversationApi.send).mock.calls[0][2]
    await wrapper.get('[data-testid="send-message"]').trigger('click')
    await flushPromises()

    expect(conversationApi.send).toHaveBeenNthCalledWith(2, 4, 'Write JWT', firstRequestId)
  })

  it('ignores a conversation created for a project that is no longer active', async () => {
    vi.mocked(conversationApi.list).mockResolvedValue({ data: { success: true, data: [] } } as never)
    let resolveCreate: (value: unknown) => void = () => undefined
    vi.mocked(conversationApi.create).mockImplementation(
      () => new Promise((resolve) => { resolveCreate = resolve }) as never
    )
    const wrapper = mount(GenerateView)
    await flushPromises()
    await wrapper.get('[data-testid="new-title"]').setValue('Old project conversation')
    await wrapper.get('[data-testid="create-conversation"]').trigger('click')

    route.params.id = '8'
    await flushPromises()
    resolveCreate({ data: { success: true, data: { ...conversation, title: 'Old project conversation' } } })
    await flushPromises()

    expect(wrapper.text()).not.toContain('Old project conversation')
  })
})
