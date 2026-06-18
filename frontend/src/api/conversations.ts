import { apiClient } from './client'
import type {
  ConversationResponse,
  CreateConversationRequest,
  MessageResponse,
  PageResponse,
  SendMessageRequest,
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
