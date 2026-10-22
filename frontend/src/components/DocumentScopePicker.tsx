import { useMemo, useState } from 'react'
import { Check, FileText, Globe, GitBranch } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { DocumentResponse, DocumentSourceType } from '@/api/types'

function SourceIcon({ type }: { type: DocumentSourceType }) {
  if (type === 'WEB_PAGE') return <Globe className="size-3.5 shrink-0 text-muted-foreground" />
  if (type === 'GIT_REPOSITORY') return <GitBranch className="size-3.5 shrink-0 text-muted-foreground" />
  return <FileText className="size-3.5 shrink-0 text-muted-foreground" />
}

interface DocumentScopePickerProps {
  documents: DocumentResponse[]
  selectedIds: string[]
  onChange: (ids: string[]) => void
}

export function DocumentScopePicker({ documents, selectedIds, onChange }: DocumentScopePickerProps) {
  const [query, setQuery] = useState('')

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return documents
    return documents.filter(
      (doc) =>
        doc.title.toLowerCase().includes(q) ||
        doc.sourceUri.toLowerCase().includes(q) ||
        doc.sourceType.toLowerCase().includes(q),
    )
  }, [documents, query])

  function toggle(id: string) {
    onChange(selectedIds.includes(id) ? selectedIds.filter((item) => item !== id) : [...selectedIds, id])
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2">
        <Input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search PDFs, URLs, repos…"
          className="h-8"
        />
        <Button type="button" variant="ghost" size="sm" onClick={() => onChange([])}>
          All
        </Button>
      </div>
      <div className="flex max-h-52 flex-col gap-1 overflow-y-auto rounded-md border p-2">
        {filtered.length === 0 ? (
          <p className="px-1 py-2 text-xs text-muted-foreground">No completed documents match.</p>
        ) : (
          filtered.map((doc) => {
            const selected = selectedIds.includes(doc.id)
            return (
              <button
                key={doc.id}
                type="button"
                onClick={() => toggle(doc.id)}
                className={cn(
                  'flex items-center gap-2 rounded px-2 py-1.5 text-left text-sm transition-colors',
                  selected ? 'bg-primary/10 text-foreground' : 'hover:bg-muted',
                )}
              >
                <div
                  className={cn(
                    'flex h-4 w-4 shrink-0 items-center justify-center rounded-sm border',
                    selected ? 'border-primary bg-primary text-primary-foreground' : 'border-muted-foreground/40',
                  )}
                >
                  {selected && <Check className="size-3" />}
                </div>
                <SourceIcon type={doc.sourceType} />
                <span className="min-w-0 flex-1 truncate">{doc.title}</span>
                <span className="max-w-[40%] truncate text-[10px] text-muted-foreground">{doc.sourceType === 'WEB_PAGE' ? doc.sourceUri : doc.sourceType}</span>
              </button>
            )
          })
        )}
      </div>
      <p className="text-xs text-muted-foreground">
        {selectedIds.length === 0
          ? 'Searching all completed documents.'
          : `${selectedIds.length} source${selectedIds.length > 1 ? 's' : ''} — answers only use these.`}
      </p>
    </div>
  )
}
