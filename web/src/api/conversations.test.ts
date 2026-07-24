import { describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { conversationApi } from './conversations'

describe('conversation api', () => {
  it('uses the stage 13 conversation endpoints', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { success: true, data: [] } })
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { success: true, data: {} } })

    await conversationApi.create({ projectId: 7, title: 'JWT' })
    await conversationApi.list(7)
    await conversationApi.messages(4)
    await conversationApi.send(4, 'Generate an article', 'request-1')

    expect(post).toHaveBeenNthCalledWith(1, '/api/agent/conversations', { projectId: 7, title: 'JWT' })
    expect(get).toHaveBeenNthCalledWith(1, '/api/agent/conversations?projectId=7')
    expect(get).toHaveBeenNthCalledWith(2, '/api/agent/conversations/4/messages')
    expect(post).toHaveBeenNthCalledWith(2, '/api/agent/conversations/4/messages', {
      content: 'Generate an article',
      requestId: 'request-1'
    })
  })
})
