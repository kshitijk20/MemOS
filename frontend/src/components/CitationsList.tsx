import { useState } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { CitationSourceDialog } from '@/components/CitationSourceDialog'
import type { Citation } from '@/api/types'

export function CitationsList({ citations }: { citations: Citation[] }) {
  const [expanded, setExpanded] = useState(false)
  const [active, setActive] = useState<Citation | null>(null)

  if (citations.length === 0) return null

  return (
    <div className="mt-2">
      <Button variant="ghost" size="sm" onClick={() => setExpanded((open) => !open)}>
        {expanded ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
        Sources ({citations.length})
      </Button>
      {expanded && (
        <div className="mt-1 flex flex-col gap-2">
          {citations.map((citation) => (
            <button
              key={citation.documentChunkId}
              type="button"
              onClick={() => setActive(citation)}
              className="rounded-md border bg-muted/40 p-2 text-left text-xs transition-colors hover:bg-muted"
            >
              <div className="flex items-center justify-between gap-2">
                <span className="font-medium">{citation.sourceTitle}</span>
                <span className="text-muted-foreground">score {citation.score.toFixed(3)}</span>
              </div>
              <p className="mt-1 text-muted-foreground">{citation.snippet}</p>
              <p className="mt-1 text-[10px] text-primary">View chunk in source</p>
            </button>
          ))}
        </div>
      )}
      <CitationSourceDialog citation={active} onOpenChange={(open) => { if (!open) setActive(null) }} />
    </div>
  )
}
