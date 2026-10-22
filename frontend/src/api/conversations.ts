import { apiClient, getAccessToken } from './client'
import type {
  ConversationResponse,
  CreateConversationRequest,
  MessageResponse,
  PageResponse,
  SendMessageRequest,
  StreamEvent,
} from './types'

export async function listConversations(page: number, size: number): Promise<PageResponse<ConversationResponse>> {
  const response = await apiClient.get<PageResponse<ConversationResponse>>('/api/v1/conversations', {
    params: { page, size },
  })
  return response.data
}

export async function createConversation(payload: CreateConversationRequest): Promise<ConversationResponse> {
  const response = await apiClient.post<ConversationResponse>('/api/v1/conversations', payload)
  return response.data
}

export async function getConversation(id: string): Promise<ConversationResponse> {
  const response = await apiClient.get<ConversationResponse>(`/api/v1/conversations/${id}`)
  return response.data
}

export async function updateConversationScope(
  id: string,
  documentIds: string[],
): Promise<ConversationResponse> {
  const response = await apiClient.patch<ConversationResponse>(`/api/v1/conversations/${id}/scope`, {
    documentIds,
  })
  return response.data
}

export async function deleteConversation(id: string): Promise<void> {
  await apiClient.delete(`/api/v1/conversations/${id}`)
}

export async function listMessages(id: string, page: number, size: number): Promise<PageResponse<MessageResponse>> {
  const response = await apiClient.get<PageResponse<MessageResponse>>(`/api/v1/conversations/${id}/messages`, {
    params: { page, size },
  })
  return response.data
}

export async function sendMessage(id: string, payload: SendMessageRequest): Promise<MessageResponse> {
  const response = await apiClient.post<MessageResponse>(`/api/v1/conversations/${id}/messages`, payload)
  return response.data
}

export async function streamMessage(
  conversationId: string,
  content: string,
  onToken: (token: string) => void,
): Promise<Extract<StreamEvent, { type: 'done' }>> {
  const baseUrl = import.meta.env.VITE_API_BASE_URL
  const response = await fetch(`${baseUrl}/api/v1/conversations/${conversationId}/messages/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getAccessToken()}`,
    },
    body: JSON.stringify({ content }),
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error((errorData as { message?: string }).message ?? `Request failed (${response.status})`)
  }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      if (!line.startsWith('data:')) continue
      const json = line.slice(5).trim()
      if (!json) continue

      const event: StreamEvent = JSON.parse(json)
      if (event.type === 'token') {
        onToken(event.content)
      } else if (event.type === 'done') {
        return event
      } else if (event.type === 'error') {
        throw new Error(event.message)
      }
    }
  }

  throw new Error('Stream ended without a done event')
}
