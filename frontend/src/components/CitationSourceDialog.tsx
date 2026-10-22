import { useEffect, useMemo, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ExternalLink } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { getDocumentChunk } from '@/api/documents'
import { highlightPassage } from '@/lib/highlight'
import type { Citation } from '@/api/types'

const CONTEXT_CHARS = 400

interface CitationSourceDialogProps {
  citation: Citation | null
  /** The [n] the answer uses for this source. */
  sourceNumber?: number
  /** The answer text, used to highlight the sentences it drew on. */
  answer?: string
  onOpenChange: (open: boolean) => void
}

function pageLabel(metadata: Record<string, unknown> | undefined): string | null {
  const start = metadata?.pageStart
  const end = metadata?.pageEnd
  if (typeof start !== 'number') return null
  return typeof end === 'number' && end !== start ? `Pages ${start}–${end}` : `Page ${start}`
}

function tail(text: string): string {
  return text.length <= CONTEXT_CHARS ? text : `…${text.slice(-CONTEXT_CHARS)}`
}

function head(text: string): string {
  return text.length <= CONTEXT_CHARS ? text : `${text.slice(0, CONTEXT_CHARS)}…`
}

export function CitationSourceDialog({ citation, sourceNumber, answer, onOpenChange }: CitationSourceDialogProps) {
  const open = citation !== null
  const highlightRef = useRef<HTMLElement>(null)
  const { data, isLoading, error } = useQuery({
    queryKey: ['document-chunk', citation?.documentId, citation?.documentChunkId],
    queryFn: () => getDocumentChunk(citation!.documentId, citation!.documentChunkId),
    enabled: open,
  })

  const segments = useMemo(
    () => (data ? highlightPassage(data.content, answer ?? '') : []),
    [data, answer],
  )
  const firstHighlight = segments.findIndex((segment) => segment.highlight)

  const passageRef = useRef<HTMLDivElement>(null)

  // Centre the first highlight within the passage box only; scrollIntoView would also scroll the
  // page behind the dialog.
  useEffect(() => {
    const box = passageRef.current
    const mark = highlightRef.current
    if (box && mark) {
      // The box is position:relative, so it is the mark's offsetParent.
      box.scrollTop = mark.offsetTop - box.clientHeight / 2 + mark.offsetHeight / 2
    }
  }, [data])

  const sourcePath =
    data?.metadata && typeof data.metadata.path === 'string' ? data.metadata.path : null
  const page = pageLabel(data?.metadata)
  const webLink = data?.sourceType === 'WEB_PAGE' && data.sourceUri ? data.sourceUri : null
  const details = data
    ? [page, sourcePath, `Section ${data.chunkIndex + 1}`].filter(Boolean).join(' · ')
    : 'Loading the cited passage from the original source.'

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {sourceNumber !== undefined && (
              <span className="rounded bg-primary/10 px-1.5 py-0.5 text-xs font-semibold text-primary">
                [{sourceNumber}]
              </span>
            )}
            <span className="truncate">{data?.documentTitle ?? citation?.sourceTitle ?? 'Source'}</span>
          </DialogTitle>
          <DialogDescription>{details}</DialogDescription>
        </DialogHeader>
        {isLoading && <p className="text-sm text-muted-foreground">Loading passage…</p>}
        {error && <p className="text-sm text-destructive">Could not load this passage. The document may have been re-ingested or deleted.</p>}
        {data && (
          <div className="flex flex-col gap-3">
            <div ref={passageRef} className="relative max-h-[55vh] overflow-auto rounded-md border bg-muted/30 p-4 text-sm leading-relaxed">
              {data.previousContent && (
                <p className="mb-3 whitespace-pre-wrap text-muted-foreground/70">{tail(data.previousContent)}</p>
              )}
              <div className="whitespace-pre-wrap border-l-2 border-primary/60 pl-3">
                {segments.map((segment, i) =>
                  segment.highlight ? (
                    <mark
                      key={i}
                      ref={i === firstHighlight ? highlightRef : undefined}
                      className="rounded-sm bg-yellow-200/80 px-0.5 text-foreground dark:bg-yellow-500/30"
                    >
                      {segment.text}
                    </mark>
                  ) : (
                    <span key={i}>{segment.text}</span>
                  ),
                )}
              </div>
              {data.nextContent && (
                <p className="mt-3 whitespace-pre-wrap text-muted-foreground/70">{head(data.nextContent)}</p>
              )}
            </div>
            <p className="text-xs text-muted-foreground">
              The bordered passage is what the answer cited; highlighted sentences are the closest match to the answer.
            </p>
            {webLink && (
              <Button variant="outline" size="sm" asChild className="self-start">
                <a href={webLink} target="_blank" rel="noreferrer">
                  <ExternalLink className="size-3.5" />
                  Open original page
                </a>
              </Button>
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
