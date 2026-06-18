import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { MessageBubble } from '@/components/MessageBubble'
import { ChatInput } from '@/components/ChatInput'
import { useConversationQuery, useMessagesQuery, useSendMessageMutation } from '@/hooks/useConversations'
import { extractErrorMessage } from '@/api/client'

export function ConversationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const conversationId = id!

  const { data: conversation, isLoading: isConversationLoading } = useConversationQuery(conversationId)
  const { data: messagesPage, isLoading: isMessagesLoading } = useMessagesQuery(conversationId)
  const sendMutation = useSendMessageMutation(conversationId)

  const [pendingContent, setPendingContent] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messagesPage, pendingContent, sendMutation.isPending])

  async function handleSend(content: string) {
    setError(null)
    setPendingContent(content)
    try {
      await sendMutation.mutateAsync({ content })
      setPendingContent(null)
    } catch (err) {
      setError(extractErrorMessage(err))
    }
  }

  function handleRetry() {
    if (pendingContent) {
      handleSend(pendingContent)
    }
  }

  return (
    <div className="flex h-[calc(100vh-8rem)] flex-col gap-4">
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
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-semibold">{conversation?.title ?? 'Untitled conversation'}</h1>
            <Badge variant="secondary">{conversation?.provider}</Badge>
            <span className="text-xs text-muted-foreground">{conversation?.model}</span>
          </div>
        )}
      </div>

      <div className="flex flex-1 flex-col gap-4 overflow-y-auto rounded-lg border p-4">
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
              />
            ))}
            {pendingContent && (
              <MessageBubble role="USER" content={pendingContent} />
            )}
            {sendMutation.isPending && <MessageBubble role="ASSISTANT" content="" pending />}
          </>
        )}
        <div ref={scrollRef} />
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

      <ChatInput onSend={handleSend} disabled={sendMutation.isPending} />
    </div>
  )
}
