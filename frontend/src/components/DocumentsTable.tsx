import { useState } from 'react'
import { RefreshCw, RotateCcw, Trash2 } from 'lucide-react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Progress } from '@/components/ui/progress'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { StatusBadge } from '@/components/StatusBadge'
import { DocumentReingestDialog } from '@/components/DocumentReingestDialog'
import {
  useDeleteDocumentMutation,
  useDocumentProgressQuery,
  useDocumentsQuery,
  useReingestDocumentMutation,
  useRetryDocumentMutation,
} from '@/hooks/useDocuments'
import { explainIngestionError } from '@/lib/ingestion-error'
import type { DocumentResponse, DocumentSourceType } from '@/api/types'

const PAGE_SIZE = 10

const SOURCE_TYPE_LABELS: Record<DocumentSourceType, string> = {
  PDF: 'PDF',
  MARKDOWN: 'Markdown',
  GIT_REPOSITORY: 'Git repository',
  WEB_PAGE: 'Web page',
}

const PHASE_LABELS: Record<string, string> = {
  PARSING: 'Parsing…',
  CHUNKING: 'Chunking…',
  EMBEDDING: 'Embedding',
  SAVING: 'Saving…',
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString()
}

function isFileSource(sourceType: DocumentSourceType): boolean {
  return sourceType === 'PDF' || sourceType === 'MARKDOWN'
}

function IngestionProgressCell({ documentId }: { documentId: string }) {
  const { data: progress } = useDocumentProgressQuery(documentId, true)

  if (!progress) {
    return <StatusBadge status="PROCESSING" />
  }

  const label = PHASE_LABELS[progress.phase] ?? progress.phase

  return (
    <div className="flex min-w-[140px] flex-col gap-1.5">
      <div className="flex items-center justify-between gap-2 text-xs">
        <span className="text-muted-foreground">{label}</span>
        {progress.total > 0 && (
          <span className="font-medium tabular-nums">{progress.percentComplete}%</span>
        )}
      </div>
      <Progress
        value={progress.total === 0 ? 0 : progress.percentComplete}
        indeterminate={progress.total === 0}
        className="h-1.5"
      />
      {progress.total > 0 && (
        <span className="text-[10px] text-muted-foreground tabular-nums">
          {progress.processed} / {progress.total} chunks
        </span>
      )}
    </div>
  )
}

export function DocumentsTable() {
  const [page, setPage] = useState(0)
  const [deleteTarget, setDeleteTarget] = useState<DocumentResponse | null>(null)
  const [reingestTarget, setReingestTarget] = useState<DocumentResponse | null>(null)
  const [errorTarget, setErrorTarget] = useState<DocumentResponse | null>(null)
  const { data, isLoading } = useDocumentsQuery(page, PAGE_SIZE)
  const deleteMutation = useDeleteDocumentMutation()
  const retryMutation = useRetryDocumentMutation()
  const reingestMutation = useReingestDocumentMutation()

  async function confirmDelete() {
    if (!deleteTarget) return
    await deleteMutation.mutateAsync(deleteTarget.id)
    setDeleteTarget(null)
  }

  function handleReingest(doc: DocumentResponse) {
    if (isFileSource(doc.sourceType)) {
      setReingestTarget(doc)
      return
    }
    reingestMutation.mutate({ id: doc.id })
  }

  if (isLoading) {
    return (
      <div className="overflow-hidden rounded-lg border">
        <div className="flex flex-col gap-3 p-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      </div>
    )
  }

  if (!data || data.items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 rounded-lg border border-dashed py-14 text-center">
        <p className="text-sm font-medium">No documents yet</p>
        <p className="text-sm text-muted-foreground">
          Upload a file or ingest a Git repository to get started.
        </p>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="overflow-hidden rounded-lg border">
        <Table className="table-fixed">
          <TableHeader>
            <TableRow>
              <TableHead className="w-[36%]">Title</TableHead>
              <TableHead className="w-[16%]">Source</TableHead>
              <TableHead className="w-[16%]">Status</TableHead>
              <TableHead className="w-[20%]">Created</TableHead>
              <TableHead className="w-[12%] text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.items.map((doc) => {
              const errorView =
                doc.status === 'FAILED' && doc.errorMessage
                  ? explainIngestionError(doc.errorMessage)
                  : null
              return (
              <TableRow key={doc.id}>
                <TableCell className="max-w-0 whitespace-normal font-medium">
                  <div className="min-w-0">
                    <p className="truncate">{doc.title}
                      {doc.version > 1 && (
                        <span className="ml-2 text-xs font-normal text-muted-foreground">v{doc.version}</span>
                      )}
                    </p>
                    {errorView && (
                      <p className="mt-0.5 truncate text-xs font-normal text-destructive">
                        {errorView.label}
                        <button
                          type="button"
                          className="ml-1.5 font-medium text-destructive underline-offset-2 hover:underline"
                          onClick={() => setErrorTarget(doc)}
                        >
                          Details
                        </button>
                      </p>
                    )}
                  </div>
                </TableCell>
                <TableCell className="text-muted-foreground">{SOURCE_TYPE_LABELS[doc.sourceType]}</TableCell>
                <TableCell>
                  {doc.status === 'PROCESSING'
                    ? <IngestionProgressCell documentId={doc.id} />
                    : <StatusBadge status={doc.status} />}
                </TableCell>
                <TableCell className="text-muted-foreground">{formatDate(doc.createdAt)}</TableCell>
                <TableCell className="text-right">
                  <div className="flex items-center justify-end gap-1">
                    {doc.status === 'FAILED' && (
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => retryMutation.mutate(doc.id)}
                        disabled={retryMutation.isPending}
                        title="Retry ingestion"
                      >
                        <RotateCcw className="size-4" />
                        <span className="sr-only">Retry</span>
                      </Button>
                    )}
                    {(doc.status === 'COMPLETED' || doc.status === 'FAILED') && (
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => handleReingest(doc)}
                        disabled={reingestMutation.isPending}
                        title="Re-ingest / replace content"
                      >
                        <RefreshCw className="size-4" />
                        <span className="sr-only">Re-ingest</span>
                      </Button>
                    )}
                    <Button variant="ghost" size="icon-sm" onClick={() => setDeleteTarget(doc)}>
                      <Trash2 className="size-4" />
                      <span className="sr-only">Delete</span>
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>

      {data.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Page {data.page + 1} of {data.totalPages}
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}

      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete document</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete "{deleteTarget?.title}"? This action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={confirmDelete} disabled={deleteMutation.isPending}>
              {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={errorTarget !== null} onOpenChange={(open) => !open && setErrorTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {errorTarget?.errorMessage
                ? explainIngestionError(errorTarget.errorMessage).title
                : 'Ingestion error'}
            </DialogTitle>
            <DialogDescription>
              {errorTarget?.errorMessage
                ? explainIngestionError(errorTarget.errorMessage).guidance
                : null}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            {errorTarget?.status === 'FAILED' && (
              <Button
                onClick={() => {
                  const id = errorTarget.id
                  setErrorTarget(null)
                  retryMutation.mutate(id)
                }}
                disabled={retryMutation.isPending}
              >
                Retry
              </Button>
            )}
            <Button variant="outline" onClick={() => setErrorTarget(null)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <DocumentReingestDialog
        document={reingestTarget}
        onOpenChange={(open) => {
          if (!open) setReingestTarget(null)
        }}
      />
    </div>
  )
}
