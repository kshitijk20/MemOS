import { Link } from 'react-router-dom'
import { ChevronRight, ThumbsDown, ThumbsUp } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useFeedbackQuery } from '@/hooks/useConversations'

interface AnswersToImproveProps {
  helpful: number | undefined
  unhelpful: number | undefined
}

/** Recent thumbs-down answers, each linking back to its conversation, so bad answers get fixed. */
export function AnswersToImprove({ helpful, unhelpful }: AnswersToImproveProps) {
  const { data, isLoading } = useFeedbackQuery('DOWN', 5)

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">Answers to improve</h2>
        <div className="flex items-center gap-3 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-1" title="Helpful answers">
            <ThumbsUp className="size-3.5" />
            {helpful ?? 0}
          </span>
          <span className="inline-flex items-center gap-1" title="Unhelpful answers">
            <ThumbsDown className="size-3.5" />
            {unhelpful ?? 0}
          </span>
        </div>
      </div>
      <Card className="overflow-hidden">
        {isLoading ? (
          <div className="flex flex-col gap-3 p-4">
            {Array.from({ length: 2 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
          </div>
        ) : !data || data.items.length === 0 ? (
          <div className="flex flex-col items-center gap-1 py-8 text-center">
            <ThumbsDown className="size-6 text-muted-foreground/40" />
            <p className="text-sm text-muted-foreground">No answers marked unhelpful.</p>
            <p className="text-xs text-muted-foreground">Use 👍 / 👎 under answers to flag what needs fixing.</p>
          </div>
        ) : (
          <ul className="divide-y">
            {data.items.map((entry) => (
              <li key={entry.messageId}>
                <Link
                  to={`/conversations/${entry.conversationId}`}
                  className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-muted/40"
                >
                  <ThumbsDown className="size-4 shrink-0 text-destructive" />
                  <div className="flex min-w-0 flex-1 flex-col">
                    <span className="truncate text-sm font-medium">{entry.question ?? entry.conversationTitle}</span>
                    <span className="truncate text-xs text-muted-foreground">
                      {entry.comment ? `“${entry.comment}”` : entry.answer}
                    </span>
                  </div>
                  <span className="hidden shrink-0 text-xs text-muted-foreground sm:block">
                    {new Date(entry.feedbackAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
                  </span>
                  <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
