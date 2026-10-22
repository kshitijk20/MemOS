import { useMemo, useState, type ComponentProps } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Search, SearchX } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { CitationsList } from '@/components/CitationsList'
import { CitationSourceDialog } from '@/components/CitationSourceDialog'
import { MessageFeedbackControls } from '@/components/MessageFeedbackControls'
import { linkSourceReferences, SOURCE_LINK_PREFIX, sourceNumberOf } from '@/lib/citations'
import type { Citation, MessageFeedback, MessageRole } from '@/api/types'

interface MessageBubbleProps {
  role: MessageRole
  content: string
  citations?: Citation[]
  createdAt?: string
  pending?: boolean
  streaming?: boolean
  /** Set for persisted assistant answers, which can be rated. */
  conversationId?: string
  messageId?: string
  unanswered?: boolean
  searchQuery?: string | null
  feedback?: MessageFeedback | null
  feedbackComment?: string | null
  /** Offered on "not found" answers when the chat is limited to some documents. */
  onWidenScope?: () => void
}

const markdownComponents = {
  p: ({ ...props }) => <p className="mb-2 last:mb-0" {...props} />,
  ul: ({ ...props }) => <ul className="mb-2 list-disc space-y-1 pl-5 last:mb-0" {...props} />,
  ol: ({ ...props }) => <ol className="mb-2 list-decimal space-y-1 pl-5 last:mb-0" {...props} />,
  li: ({ ...props }) => <li {...props} />,
  strong: ({ ...props }) => <strong className="font-semibold" {...props} />,
  code: ({ ...props }) => <code className="rounded bg-black/10 px-1 py-0.5 text-xs" {...props} />,
  pre: ({ ...props }) => <pre className="mb-2 overflow-x-auto rounded bg-black/10 p-2 text-xs last:mb-0" {...props} />,
  h1: ({ ...props }) => <h1 className="mb-2 text-base font-semibold" {...props} />,
  h2: ({ ...props }) => <h2 className="mb-2 text-base font-semibold" {...props} />,
  h3: ({ ...props }) => <h3 className="mb-1 text-sm font-semibold" {...props} />,
  blockquote: ({ ...props }) => <blockquote className="border-l-2 pl-2 italic opacity-90" {...props} />,
}

export function MessageBubble({
  role,
  content,
  citations = [],
  createdAt,
  pending,
  streaming,
  conversationId,
  messageId,
  unanswered,
  searchQuery,
  feedback = null,
  feedbackComment = null,
  onWidenScope,
}: MessageBubbleProps) {
  const [active, setActive] = useState<{ citation: Citation; number: number } | null>(null)

  const citationsByNumber = useMemo(
    () => new Map(citations.map((citation, index) => [sourceNumberOf(citation, index), citation])),
    [citations],
  )
  const linkedContent = useMemo(
    () => linkSourceReferences(content, new Set(citationsByNumber.keys())),
    [content, citationsByNumber],
  )

  if (role === 'SYSTEM') {
    return <p className="text-center text-xs text-muted-foreground">{content}</p>
  }

  const isUser = role === 'USER'
  const isAnswer = !isUser && !pending && !streaming

  const components = {
    ...markdownComponents,
    a: ({ href, children, ...props }: ComponentProps<'a'>) => {
      if (href?.startsWith(SOURCE_LINK_PREFIX)) {
        const number = Number(href.slice(SOURCE_LINK_PREFIX.length))
        const citation = citationsByNumber.get(number)
        return (
          <button
            type="button"
            title={citation ? `Source ${number}: ${citation.sourceTitle}` : undefined}
            onClick={() => citation && setActive({ citation, number })}
            className="mx-px inline-flex h-4 min-w-4 items-center justify-center rounded bg-primary/15 px-1 align-text-top text-[10px] font-semibold text-primary transition-colors hover:bg-primary hover:text-primary-foreground"
          >
            {number}
          </button>
        )
      }
      return (
        <a href={href} className="underline underline-offset-2" target="_blank" rel="noreferrer" {...props}>
          {children}
        </a>
      )
    },
  }

  return (
    <div className={cn('flex flex-col', isUser ? 'items-end' : 'items-start')}>
      <div
        className={cn(
          'max-w-[80%] rounded-xl px-3 py-2 text-sm',
          isUser
            ? 'whitespace-pre-wrap bg-primary text-primary-foreground'
            : unanswered
              ? 'border border-dashed bg-background text-foreground'
              : 'bg-muted text-foreground',
        )}
      >
        {pending ? (
          <span className="inline-flex items-center gap-1 text-muted-foreground">
            <span className="size-1.5 animate-bounce rounded-full bg-current [animation-delay:-0.3s]" />
            <span className="size-1.5 animate-bounce rounded-full bg-current [animation-delay:-0.15s]" />
            <span className="size-1.5 animate-bounce rounded-full bg-current" />
          </span>
        ) : streaming ? (
          <span className="whitespace-pre-wrap">
            {content}
            <span className="inline-block h-4 w-0.5 animate-pulse bg-current align-middle ml-0.5" />
          </span>
        ) : isUser ? (
          content
        ) : unanswered ? (
          <div className="flex flex-col gap-2">
            <p className="flex items-center gap-1.5 font-medium">
              <SearchX className="size-4 text-muted-foreground" />
              Not found in your documents
            </p>
            <p className="text-muted-foreground">{content}</p>
            {onWidenScope && (
              <Button variant="outline" size="sm" className="self-start" onClick={onWidenScope}>
                Search all documents instead
              </Button>
            )}
          </div>
        ) : (
          <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
            {linkedContent}
          </ReactMarkdown>
        )}
      </div>
      {isAnswer && searchQuery && (
        <span className="mt-1 flex max-w-[80%] items-center gap-1 text-xs text-muted-foreground" title="Follow-up rewritten as a standalone search">
          <Search className="size-3 shrink-0" />
          <span className="truncate">Searched for “{searchQuery}”</span>
        </span>
      )}
      {isAnswer && (
        <CitationsList citations={citations} onOpen={(citation, number) => setActive({ citation, number })} />
      )}
      {isAnswer && conversationId && messageId && (
        <MessageFeedbackControls
          conversationId={conversationId}
          messageId={messageId}
          feedback={feedback}
          feedbackComment={feedbackComment}
        />
      )}
      {createdAt && !pending && (
        <span className="mt-1 text-xs text-muted-foreground">{new Date(createdAt).toLocaleTimeString()}</span>
      )}
      <CitationSourceDialog
        citation={active?.citation ?? null}
        sourceNumber={active?.number}
        answer={content}
        onOpenChange={(open) => {
          if (!open) setActive(null)
        }}
      />
    </div>
  )
}
