import { useState, type FormEvent } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { DocumentScopePicker } from '@/components/DocumentScopePicker'
import { useDocumentsQuery } from '@/hooks/useDocuments'
import { useCreateCollectionMutation, useUpdateCollectionMutation } from '@/hooks/useCollections'
import type { CollectionResponse } from '@/api/types'

interface CollectionDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Edits this collection; creates a new one when absent. */
  collection?: CollectionResponse
  onSaved?: (collection: CollectionResponse) => void
}

export function CollectionDialog({ open, onOpenChange, collection, onSaved }: CollectionDialogProps) {
  // Initialised from props: the parent gives each opening its own key, so state starts fresh.
  const [name, setName] = useState(collection?.name ?? '')
  const [documentIds, setDocumentIds] = useState<string[]>(collection?.documentIds ?? [])
  const { data: docsPage } = useDocumentsQuery(0, 200)
  const createMutation = useCreateCollectionMutation()
  const updateMutation = useUpdateCollectionMutation()
  const saving = createMutation.isPending || updateMutation.isPending

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) return
    try {
      const saved = collection
        ? await updateMutation.mutateAsync({ id: collection.id, name: trimmed, documentIds })
        : await createMutation.mutateAsync({ name: trimmed, documentIds })
      onSaved?.(saved)
      onOpenChange(false)
    } catch {
      // error toast shown by the mutation; keep the dialog open
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{collection ? 'Edit collection' : 'New collection'}</DialogTitle>
          <DialogDescription>
            Group related documents, like "HR policies" or "Project X", then chat with just that group.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="collection-name">Name</Label>
            <Input
              id="collection-name"
              required
              maxLength={100}
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="e.g. HR policies"
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label>Documents</Label>
            <DocumentScopePicker
              documents={docsPage?.items ?? []}
              selectedIds={documentIds}
              onChange={setDocumentIds}
              emptyText="No documents match."
              summary={(count) =>
                count === 0 ? 'No documents yet — you can add them later.' : `${count} document${count > 1 ? 's' : ''} selected.`
              }
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving || !name.trim()}>
              {saving ? 'Saving…' : collection ? 'Save' : 'Create collection'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
