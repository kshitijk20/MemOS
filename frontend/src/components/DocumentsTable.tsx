import { useState } from 'react'
import { Trash2 } from 'lucide-react'
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { StatusBadge } from '@/components/StatusBadge'
import { useDeleteDocumentMutation, useDocumentsQuery } from '@/hooks/useDocuments'
import type { DocumentResponse, DocumentSourceType } from '@/api/types'

const PAGE_SIZE = 10

const SOURCE_TYPE_LABELS: Record<DocumentSourceType, string> = {
  PDF: 'PDF',
  MARKDOWN: 'Markdown',
  GIT_REPOSITORY: 'Git repository',
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString()
}

export function DocumentsTable() {
  const [page, setPage] = useState(0)
  const [deleteTarget, setDeleteTarget] = useState<DocumentResponse | null>(null)
  const { data, isLoading } = useDocumentsQuery(page, PAGE_SIZE)
  const deleteMutation = useDeleteDocumentMutation()

  async function confirmDelete() {
    if (!deleteTarget) return
    await deleteMutation.mutateAsync(deleteTarget.id)
    setDeleteTarget(null)
  }

  if (isLoading) {
    return (
      <div className="flex flex-col gap-2">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-12 w-full" />
        ))}
      </div>
    )
  }

  if (!data || data.items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 rounded-lg border border-dashed py-12 text-center">
        <p className="text-sm font-medium">No documents yet</p>
        <p className="text-sm text-muted-foreground">
          Upload a file or ingest a Git repository to get started.
        </p>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Title</TableHead>
            <TableHead>Source</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Created</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.items.map((doc) => (
            <TableRow key={doc.id}>
              <TableCell className="font-medium">
                <div className="flex flex-col">
                  <span>{doc.title}</span>
                  {doc.status === 'FAILED' && doc.errorMessage && (
                    <span className="max-w-md truncate text-xs text-destructive" title={doc.errorMessage}>
                      {doc.errorMessage}
                    </span>
                  )}
                </div>
              </TableCell>
              <TableCell>{SOURCE_TYPE_LABELS[doc.sourceType]}</TableCell>
              <TableCell>
                <StatusBadge status={doc.status} />
              </TableCell>
              <TableCell className="text-muted-foreground">{formatDate(doc.createdAt)}</TableCell>
              <TableCell className="text-right">
                <Button variant="ghost" size="icon-sm" onClick={() => setDeleteTarget(doc)}>
                  <Trash2 className="size-4" />
                  <span className="sr-only">Delete</span>
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

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
    </div>
  )
}
