import { apiRequest } from './client'
import type { ChatMessage, Conversation, PageResult } from '@/types/domain'

function requestMessagePage(courseId: string, conversationId: string, page: number, size: number) {
  return apiRequest<PageResult<ChatMessage>>({
    url: `/courses/${courseId}/conversations/${conversationId}/messages`,
    params: { page, size },
  })
}

export const assistantApi = {
  conversations: (courseId: string) =>
    apiRequest<PageResult<Conversation>>({ url: `/courses/${courseId}/conversations`, params: { page: 0, size: 100 } }),
  createConversation: (courseId: string, title?: string) =>
    apiRequest<Conversation>({
      url: `/courses/${courseId}/conversations`,
      method: 'POST',
      data: title?.trim() ? { title: title.trim() } : {},
    }),
  messagesPage: requestMessagePage,
  async messages(courseId: string, conversationId: string): Promise<PageResult<ChatMessage>> {
    const size = 100
    const first = await requestMessagePage(courseId, conversationId, 0, size)
    if (first.total <= size) return first

    const lastPage = Math.floor((first.total - 1) / size)
    const tail = await requestMessagePage(courseId, conversationId, lastPage, size)
    if (tail.items.length === size) return tail

    const previous = await requestMessagePage(courseId, conversationId, lastPage - 1, size)
    return {
      items: [...previous.items, ...tail.items].slice(-size),
      page: lastPage,
      size,
      total: first.total,
    }
  },
  feedback: (courseId: string, conversationId: string, messageId: string, helpful: boolean, comment?: string) =>
    apiRequest<void>({
      url: `/courses/${courseId}/conversations/${conversationId}/messages/${messageId}/feedback`,
      method: 'POST',
      data: { rating: helpful ? 'HELPFUL' : 'NOT_HELPFUL', comment },
    }),
  cancel: (courseId: string, conversationId: string, requestId: string) =>
    apiRequest<{ cancelled: boolean }>({
      url: `/courses/${courseId}/conversations/${conversationId}/requests/${requestId}`,
      method: 'DELETE',
    }),
  deleteConversation: (courseId: string, conversationId: string) =>
    apiRequest<void>({ url: `/courses/${courseId}/conversations/${conversationId}`, method: 'DELETE' }),
}
