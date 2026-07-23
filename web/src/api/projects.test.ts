import { describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { projectApi } from './projects'

describe('project api', () => {
  it('uses backend project document path', async () => {
    const spy = vi.spyOn(http, 'get').mockResolvedValue({ data: { success: true, data: [] } })

    await projectApi.documents(8)

    expect(spy).toHaveBeenCalledWith('/api/projects/8/documents')
  })
})
