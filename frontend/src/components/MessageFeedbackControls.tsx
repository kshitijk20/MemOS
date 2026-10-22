import { useState } from 'react'
import { ThumbsDown, ThumbsUp } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import { useMessageFeedbackMutation } from '@/hooks/useConversations'
import type { MessageFeedback } from '@/api/types'

interface MessageFeedbackControlsProps {
  conversationId: string
  messageId: string
  feedback: MessageFeedback | null
  feedbackComment: string | null
}

export function MessageFeedbackControls({ conversationId, messageId, feedback, feedbackComment }: MessageFeedbackControlsProps) {
  const mutation = useMessageFeedbackMutation(conversationId)
  const [commentOpen, setCommentOpen] = useState(false)
  const [comment, setComment] = useState('')

  function rate(next: MessageFeedback) {
    // Clicking the active rating again clears it.
    const value = feedback === next ? null : next
    mutation.mutate({ messageId, feedback: value })
    setCommentOpen(value === 'DOWN')
    setComment('')
  }

  function sendComment() {
    const trimmed = comment.trim()
    if (trimmed) {
      mutation.mutate({ messageId, feedback: 'DOWN', comment: trimmed })
    }
    setCommentOpen(false)
  }

  return (
    <div className="mt-1 flex flex-col gap-1.5">
      <div className="flex items-center gap-0.5">
        <Button
          variant="ghost"
          size="icon-sm"
          aria-pressed={feedback === 'UP'}
          title="Helpful"
          onClick={() => rate('UP')}
          className={cn('size-7 text-muted-foreground', feedback === 'UP' && 'text-green-600 dark:text-green-400')}
        >
          <ThumbsUp className={cn('size-3.5', feedback === 'UP' && 'fill-current')} />
          <span className="sr-only">Helpful</span>
        </Button>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-pressed={feedback === 'DOWN'}
          title="Not helpful"
          onClick={() => rate('DOWN')}
          className={cn('size-7 text-muted-foreground', feedback === 'DOWN' && 'text-destructive')}
        >
          <ThumbsDown className={cn('size-3.5', feedback === 'DOWN' && 'fill-current')} />
          <span className="sr-only">Not helpful</span>
        </Button>
        {feedback === 'DOWN' && feedbackComment && !commentOpen && (
          <span className="ml-1 max-w-[16rem] truncate text-xs text-muted-foreground" title={feedbackComment}>
            “{feedbackComment}”
          </span>
        )}
        {feedback && !feedbackComment && !commentOpen && (
          <span className="ml-1 text-xs text-muted-foreground">Thanks for the feedback</span>
        )}
      </div>
      {commentOpen && (
        <div className="flex w-full max-w-md flex-col gap-2 rounded-md border bg-background p-2">
          <Textarea
            autoFocus
            value={comment}
            onChange={(event) => setComment(event.target.value)}
            maxLength={2000}
            rows={2}
            placeholder="What was wrong? (optional) e.g. outdated, wrong document, missed the point"
            className="min-h-0 text-xs"
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault()
                sendComment()
              }
            }}
          />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={() => setCommentOpen(false)}>
              Skip
            </Button>
            <Button size="sm" onClick={sendComment} disabled={!comment.trim() || mutation.isPending}>
              Send
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
