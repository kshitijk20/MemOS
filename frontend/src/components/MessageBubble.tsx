import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { cn } from '@/lib/utils'
import { CitationsList } from '@/components/CitationsList'
import type { Citation, MessageRole } from '@/api/types'

interface MessageBubbleProps {
  role: MessageRole
  content: string
  citations?: Citation[]
  createdAt?: string
  pending?: boolean
  streaming?: boolean
}

const markdownComponents = {
  p: ({ ...props }) => <p className="mb-2 last:mb-0" {...props} />,
  ul: ({ ...props }) => <ul className="mb-2 list-disc space-y-1 pl-5 last:mb-0" {...props} />,
  ol: ({ ...props }) => <ol className="mb-2 list-decimal space-y-1 pl-5 last:mb-0" {...props} />,
  li: ({ ...props }) => <li {...props} />,
  strong: ({ ...props }) => <strong className="font-semibold" {...props} />,
  a: ({ ...props }) => <a className="underline underline-offset-2" target="_blank" rel="noreferrer" {...props} />,
  code: ({ ...props }) => <code className="rounded bg-black/10 px-1 py-0.5 text-xs" {...props} />,
  pre: ({ ...props }) => <pre className="mb-2 overflow-x-auto rounded bg-black/10 p-2 text-xs last:mb-0" {...props} />,
  h1: ({ ...props }) => <h1 className="mb-2 text-base font-semibold" {...props} />,
  h2: ({ ...props }) => <h2 className="mb-2 text-base font-semibold" {...props} />,
  h3: ({ ...props }) => <h3 className="mb-1 text-sm font-semibold" {...props} />,
  blockquote: ({ ...props }) => <blockquote className="border-l-2 pl-2 italic opacity-90" {...props} />,
}

export function MessageBubble({ role, content, citations = [], createdAt, pending, streaming }: MessageBubbleProps) {
  if (role === 'SYSTEM') {
    return <p className="text-center text-xs text-muted-foreground">{content}</p>
  }

  const isUser = role === 'USER'

  return (
    <div className={cn('flex flex-col', isUser ? 'items-end' : 'items-start')}>
      <div
        className={cn(
          'max-w-[80%] rounded-xl px-3 py-2 text-sm',
          isUser ? 'whitespace-pre-wrap bg-primary text-primary-foreground' : 'bg-muted text-foreground',
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
        ) : (
          <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownComponents}>
            {content}
          </ReactMarkdown>
        )}
      </div>
      {!pending && !isUser && <CitationsList citations={citations} />}
      {createdAt && !pending && (
        <span className="mt-1 text-xs text-muted-foreground">{new Date(createdAt).toLocaleTimeString()}</span>
      )}
    </div>
  )
}
