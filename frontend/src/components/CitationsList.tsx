import { useState } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { sourceNumberOf } from '@/lib/citations'
import type { Citation } from '@/api/types'

interface CitationsListProps {
  citations: Citation[]
  onOpen: (citation: Citation, sourceNumber: number) => void
}

export function CitationsList({ citations, onOpen }: CitationsListProps) {
  const [expanded, setExpanded] = useState(false)

  if (citations.length === 0) return null

  return (
    <div className="mt-2">
      <Button variant="ghost" size="sm" onClick={() => setExpanded((open) => !open)}>
        {expanded ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
        Sources ({citations.length})
      </Button>
      {expanded && (
        <div className="mt-1 flex flex-col gap-2">
          {citations.map((citation, index) => {
            const number = sourceNumberOf(citation, index)
            return (
              <button
                key={citation.documentChunkId}
                type="button"
                onClick={() => onOpen(citation, number)}
                className="rounded-md border bg-muted/40 p-2 text-left text-xs transition-colors hover:bg-muted"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="flex min-w-0 items-center gap-1.5">
                    <span className="shrink-0 rounded bg-primary/10 px-1 font-semibold text-primary">[{number}]</span>
                    <span className="truncate font-medium">{citation.sourceTitle}</span>
                  </span>
                  <span className="shrink-0 text-muted-foreground">score {citation.score.toFixed(3)}</span>
                </div>
                <p className="mt-1 line-clamp-3 text-muted-foreground">{citation.snippet}</p>
                <p className="mt-1 text-[10px] text-primary">Show highlighted passage</p>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
