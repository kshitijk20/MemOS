import { useState } from 'react'
import { Check, Folder } from 'lucide-react'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useCollectionsQuery, useUpdateCollectionMutation } from '@/hooks/useCollections'
import type { DocumentResponse } from '@/api/types'

interface DocumentCollectionsDialogProps {
  document: DocumentResponse | null
  onOpenChange: (open: boolean) => void
  onCreateCollection: () => void
}

/** Adds one document to, or removes it from, any of the user's collections. */
export function DocumentCollectionsDialog({ document, onOpenChange, onCreateCollection }: DocumentCollectionsDialogProps) {
  const { data: collections = [] } = useCollectionsQuery()
  const updateMutation = useUpdateCollectionMutation()
  // Initialised once per document: the parent keys this dialog by document id.
  const [selected, setSelected] = useState<Set<string>>(
    () => new Set(document ? collections.filter((c) => c.documentIds.includes(document.id)).map((c) => c.id) : []),
  )
  const [saving, setSaving] = useState(false)

  function toggle(id: string) {
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  async function handleSave() {
    if (!document) return
    const changed = collections.filter((c) => c.documentIds.includes(document.id) !== selected.has(c.id))
    setSaving(true)
    try {
      for (const collection of changed) {
        const documentIds = selected.has(collection.id)
          ? [...collection.documentIds, document.id]
          : collection.documentIds.filter((id) => id !== document.id)
        await updateMutation.mutateAsync({ id: collection.id, documentIds })
      }
      if (changed.length > 0) toast.success('Collections updated')
      onOpenChange(false)
    } catch {
      // error toast shown by the mutation
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={document !== null} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Collections</DialogTitle>
          <DialogDescription className="truncate">Choose where “{document?.title}” belongs.</DialogDescription>
        </DialogHeader>
        {collections.length === 0 ? (
          <p className="text-sm text-muted-foreground">You don't have any collections yet.</p>
        ) : (
          <div className="flex max-h-60 flex-col gap-1 overflow-y-auto rounded-md border p-2">
            {collections.map((collection) => {
              const checked = selected.has(collection.id)
              return (
                <button
                  key={collection.id}
                  type="button"
                  onClick={() => toggle(collection.id)}
                  className={cn(
                    'flex items-center gap-2 rounded px-2 py-1.5 text-left text-sm transition-colors',
                    checked ? 'bg-primary/10' : 'hover:bg-muted',
                  )}
                >
                  <div
                    className={cn(
                      'flex h-4 w-4 shrink-0 items-center justify-center rounded-sm border',
                      checked ? 'border-primary bg-primary text-primary-foreground' : 'border-muted-foreground/40',
                    )}
                  >
                    {checked && <Check className="size-3" />}
                  </div>
                  <Folder className="size-3.5 shrink-0 text-muted-foreground" />
                  <span className="min-w-0 flex-1 truncate">{collection.name}</span>
                </button>
              )
            })}
          </div>
        )}
        <DialogFooter className="sm:justify-between">
          <Button type="button" variant="ghost" onClick={onCreateCollection}>
            New collection
          </Button>
          <div className="flex gap-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="button" onClick={handleSave} disabled={saving || collections.length === 0}>
              {saving ? 'Saving…' : 'Save'}
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
