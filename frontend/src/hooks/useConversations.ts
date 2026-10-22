import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  createConversation,
  deleteConversation,
  getConversation,
  listConversations,
  listMessages,
  sendMessage,
  updateConversationScope,
} from '@/api/conversations'
import { extractErrorMessage } from '@/api/client'
import type { CreateConversationRequest, SendMessageRequest } from '@/api/types'

const MESSAGES_PAGE_SIZE = 100

export function useConversationsQuery(page: number, size: number) {
  return useQuery({
    queryKey: ['conversations', page, size],
    queryFn: () => listConversations(page, size),
  })
}

export function useConversationQuery(id: string) {
  return useQuery({
    queryKey: ['conversations', id],
    queryFn: () => getConversation(id),
  })
}

export function useCreateConversationMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateConversationRequest) => createConversation(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['conversations'] })
    },
    onError: (error) => {
      toast.error(extractErrorMessage(error))
    },
  })
}

export function useUpdateConversationScopeMutation(conversationId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (documentIds: string[]) => updateConversationScope(conversationId, documentIds),
    onSuccess: () => {
      toast.success('Source filter updated')
      queryClient.invalidateQueries({ queryKey: ['conversations', conversationId] })
      queryClient.invalidateQueries({ queryKey: ['conversations'] })
    },
    onError: (error) => {
      toast.error(extractErrorMessage(error))
    },
  })
}

export function useDeleteConversationMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteConversation(id),
    onSuccess: () => {
      toast.success('Conversation deleted')
      queryClient.invalidateQueries({ queryKey: ['conversations'] })
    },
    onError: (error) => {
      toast.error(extractErrorMessage(error))
    },
  })
}

export function useMessagesQuery(conversationId: string) {
  return useQuery({
    queryKey: ['messages', conversationId],
    queryFn: () => listMessages(conversationId, 0, MESSAGES_PAGE_SIZE),
  })
}

export function useSendMessageMutation(conversationId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: SendMessageRequest) => sendMessage(conversationId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['messages', conversationId] })
      queryClient.invalidateQueries({ queryKey: ['conversations'] })
    },
  })
}
