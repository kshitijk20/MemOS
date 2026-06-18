import { useState } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { Citation } from '@/api/types'

export function CitationsList({ citations }: { citations: Citation[] }) {
  const [expanded, setExpanded] = useState(false)

  if (citations.length === 0) return null

  return (
    <div className="mt-2">
      <Button variant="ghost" size="sm" onClick={() => setExpanded((e) => !e)}>
        {expanded ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
        Sources ({citations.length})
      </Button>
      {expanded && (
        <div className="mt-1 flex flex-col gap-2">
          {citations.map((citation) => (
            <div key={citation.documentChunkId} className="rounded-md border bg-muted/40 p-2 text-xs">
              <div className="flex items-center justify-between gap-2">
                <span className="font-medium">{citation.sourceTitle}</span>
                <span className="text-muted-foreground">score {citation.score.toFixed(3)}</span>
              </div>
              <p className="mt-1 text-muted-foreground">{citation.snippet}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
