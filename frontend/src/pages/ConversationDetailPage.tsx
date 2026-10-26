import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, RotateCcw } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { MessageBubble } from '@/components/MessageBubble'
import { ChatInput } from '@/components/ChatInput'
import { ConversationScopeControl } from '@/components/ConversationScopeControl'
import { useConversationQuery, useMessagesQuery, useUpdateConversationScopeMutation } from '@/hooks/useConversations'
import { scopeOf } from '@/lib/scope'
import { streamMessage } from '@/api/conversations'
import { extractErrorMessage } from '@/api/client'

export function ConversationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const conversationId = id!

  const queryClient = useQueryClient()
  const { data: conversation, isLoading: isConversationLoading } = useConversationQuery(conversationId)
  const { data: messagesPage, isLoading: isMessagesLoading } = useMessagesQuery(conversationId)
  const updateScope = useUpdateConversationScopeMutation(conversationId)
  const isScoped = !!conversation && scopeOf(conversation).kind !== 'all'

  const [pendingContent, setPendingContent] = useState<string | null>(null)
  const [streamingContent, setStreamingContent] = useState<string | null>(null)
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const scrollContainerRef = useRef<HTMLDivElement>(null)
  /** Follow new content only while the reader is at (or near) the bottom of the chat. */
  const stickToBottomRef = useRef(true)
  const tokenBufferRef = useRef('')
  const rafRef = useRef<number | null>(null)
  const messageCount = messagesPage?.items.length ?? 0

  function scrollToBottom(behavior: ScrollBehavior) {
    const container = scrollContainerRef.current
    // Scroll only the message list; scrollIntoView would also scroll the page around it.
    container?.scrollTo({ top: container.scrollHeight, behavior })
  }

  function handleScroll() {
    const container = scrollContainerRef.current
    if (!container) return
    stickToBottomRef.current = container.scrollHeight - container.scrollTop - container.clientHeight < 80
  }

  // Opening a conversation starts at the latest message, without animating through the history.
  useLayoutEffect(() => {
    if (!isMessagesLoading) {
      stickToBottomRef.current = true
      scrollToBottom('auto')
    }
  }, [conversationId, isMessagesLoading])

  // New messages and streamed tokens. Deliberately not keyed on the message data itself, so
  // rating an answer or other in-place updates don't yank the view to the bottom.
  useEffect(() => {
    if (stickToBottomRef.current) {
      scrollToBottom(isStreaming ? 'auto' : 'smooth')
    }
  }, [messageCount, pendingContent, streamingContent, isStreaming])

  async function handleSend(content: string) {
    // Sending is an explicit "take me to the conversation's end".
    stickToBottomRef.current = true
    setError(null)
    setPendingContent(content)
    setStreamingContent('')
    setIsStreaming(true)
    tokenBufferRef.current = ''

    try {
      const done = await streamMessage(conversationId, content, (token) => {
        tokenBufferRef.current += token
        if (rafRef.current === null) {
          rafRef.current = requestAnimationFrame(() => {
            setStreamingContent((prev) => (prev ?? '') + tokenBufferRef.current)
            tokenBufferRef.current = ''
            rafRef.current = null
          })
        }
      })
      if (rafRef.current !== null) {
        cancelAnimationFrame(rafRef.current)
        rafRef.current = null
      }
      tokenBufferRef.current = ''
      setPendingContent(null)
      setStreamingContent(null)
      setIsStreaming(false)
      queryClient.invalidateQueries({ queryKey: ['messages', conversationId] })
      if (done.conversationTitle) {
        queryClient.invalidateQueries({ queryKey: ['conversations'] })
        queryClient.invalidateQueries({ queryKey: ['conversations', conversationId] })
      }
    } catch (err) {
      if (rafRef.current !== null) {
        cancelAnimationFrame(rafRef.current)
        rafRef.current = null
      }
      tokenBufferRef.current = ''
      setStreamingContent(null)
      setIsStreaming(false)
      setError(extractErrorMessage(err))
    }
  }

  function handleRetry() {
    if (pendingContent) {
      handleSend(pendingContent)
    }
  }

  return (
    <div className="flex h-[calc(100dvh-11rem-env(safe-area-inset-top)-env(safe-area-inset-bottom))] flex-col gap-3 md:h-[calc(100dvh-8rem)] md:gap-4">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon-sm" asChild>
          <Link to="/conversations">
            <ArrowLeft className="size-4" />
            <span className="sr-only">Back</span>
          </Link>
        </Button>
        {isConversationLoading ? (
          <Skeleton className="h-6 w-48" />
        ) : (
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-xl font-semibold">{conversation?.title ?? 'Untitled conversation'}</h1>
            <Badge variant="secondary">{conversation?.provider}</Badge>
            {conversation?.model && (
              <span className="hidden text-xs text-muted-foreground sm:inline">{conversation.model}</span>
            )}
            {conversation && <ConversationScopeControl conversation={conversation} />}
          </div>
        )}
      </div>

      <div
        ref={scrollContainerRef}
        onScroll={handleScroll}
        className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto overscroll-contain rounded-lg border p-4"
      >
        {isMessagesLoading ? (
          <div className="flex flex-col gap-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-12 w-2/3" />
            ))}
          </div>
        ) : messagesPage && messagesPage.items.length === 0 && !pendingContent ? (
          <div className="flex flex-1 items-center justify-center text-center text-sm text-muted-foreground">
            Send a message to start the conversation.
          </div>
        ) : (
          <>
            {messagesPage?.items.map((message) => (
              <MessageBubble
                key={message.id}
                role={message.role}
                content={message.content}
                citations={message.citations}
                createdAt={message.createdAt}
                conversationId={conversationId}
                messageId={message.id}
                unanswered={message.unanswered}
                searchQuery={message.searchQuery}
                feedback={message.feedback}
                feedbackComment={message.feedbackComment}
                onWidenScope={
                  message.unanswered && isScoped && !updateScope.isPending
                    ? () => updateScope.mutate({ kind: 'all' })
                    : undefined
                }
              />
            ))}
            {pendingContent && (
              <MessageBubble role="USER" content={pendingContent} />
            )}
            {isStreaming && (
              streamingContent
                ? <MessageBubble role="ASSISTANT" content={streamingContent} streaming />
                : <MessageBubble role="ASSISTANT" content="" pending />
            )}
          </>
        )}
      </div>

      {error && (
        <div className="flex items-center justify-between rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          <span>{error}</span>
          <Button variant="outline" size="sm" onClick={handleRetry}>
            <RotateCcw className="size-3.5" />
            Retry
          </Button>
        </div>
      )}

      <ChatInput onSend={handleSend} disabled={isStreaming} />
    </div>
  )
}
