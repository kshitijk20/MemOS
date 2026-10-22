import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  createConversation,
  deleteConversation,
  getConversation,
  listConversations,
  listFeedback,
  listMessages,
  sendMessage,
  setMessageFeedback,
  updateConversationScope,
} from '@/api/conversations'
import { extractErrorMessage } from '@/api/client'
import type {
  ConversationScope,
  CreateConversationRequest,
  MessageFeedback,
  MessageResponse,
  PageResponse,
  SendMessageRequest,
} from '@/api/types'

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
    mutationFn: (scope: ConversationScope) => updateConversationScope(conversationId, scope),
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

/** Rates an answer, updating the cached message list right away and rolling back on failure. */
export function useMessageFeedbackMutation(conversationId: string) {
  const queryClient = useQueryClient()
  const key = ['messages', conversationId]
  return useMutation({
    mutationFn: ({ messageId, feedback, comment }: { messageId: string; feedback: MessageFeedback | null; comment?: string }) =>
      setMessageFeedback(conversationId, messageId, feedback, comment),
    onMutate: async ({ messageId, feedback, comment }) => {
      await queryClient.cancelQueries({ queryKey: key })
      const previous = queryClient.getQueryData<PageResponse<MessageResponse>>(key)
      if (previous) {
        queryClient.setQueryData<PageResponse<MessageResponse>>(key, {
          ...previous,
          items: previous.items.map((message) =>
            message.id === messageId
              ? { ...message, feedback, feedbackComment: feedback ? (comment ?? message.feedbackComment) : null }
              : message,
          ),
        })
      }
      return { previous }
    },
    onError: (error, _variables, context) => {
      if (context?.previous) queryClient.setQueryData(key, context.previous)
      toast.error(extractErrorMessage(error))
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: key })
      queryClient.invalidateQueries({ queryKey: ['feedback'] })
      queryClient.invalidateQueries({ queryKey: ['stats'] })
    },
  })
}

export function useFeedbackQuery(rating: MessageFeedback | null, size: number) {
  return useQuery({
    queryKey: ['feedback', rating, size],
    queryFn: () => listFeedback(rating, 0, size),
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
