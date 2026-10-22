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
import type { Citation } from '@/api/types'

interface CitationSourceDialogProps {
  citation: Citation | null
  onOpenChange: (open: boolean) => void
}

export function CitationSourceDialog({ citation, onOpenChange }: CitationSourceDialogProps) {
  const open = citation !== null
  const { data, isLoading, error } = useQuery({
    queryKey: ['document-chunk', citation?.documentId, citation?.documentChunkId],
    queryFn: () => getDocumentChunk(citation!.documentId, citation!.documentChunkId),
    enabled: open,
  })

  const sourcePath =
    data?.metadata && typeof data.metadata.path === 'string' ? data.metadata.path : null
  const webLink = data?.sourceType === 'WEB_PAGE' && data.sourceUri ? data.sourceUri : null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{data?.documentTitle ?? citation?.sourceTitle ?? 'Source chunk'}</DialogTitle>
          <DialogDescription>
            {data
              ? `Chunk ${data.chunkIndex + 1}${sourcePath ? ` · ${sourcePath}` : ''}`
              : 'Loading the cited passage from the original source.'}
          </DialogDescription>
        </DialogHeader>
        {isLoading && <p className="text-sm text-muted-foreground">Loading chunk…</p>}
        {error && <p className="text-sm text-destructive">Could not load this chunk.</p>}
        {data && (
          <div className="flex flex-col gap-3">
            <pre className="max-h-[50vh] overflow-auto whitespace-pre-wrap rounded-md border bg-muted/40 p-3 text-xs leading-relaxed">
              {data.content}
            </pre>
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
