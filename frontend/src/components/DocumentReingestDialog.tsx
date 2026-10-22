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
import { useReingestDocumentMutation } from '@/hooks/useDocuments'
import type { DocumentResponse } from '@/api/types'

const MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024

interface DocumentReingestDialogProps {
  document: DocumentResponse | null
  onOpenChange: (open: boolean) => void
}

export function DocumentReingestDialog({ document, onOpenChange }: DocumentReingestDialogProps) {
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const mutation = useReingestDocumentMutation()

  function resetAndClose() {
    setFile(null)
    setError(null)
    onOpenChange(false)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!document) return
    setError(null)

    if (!file) {
      setError('Please select the file to re-ingest')
      return
    }
    if (file.size > MAX_FILE_SIZE_BYTES) {
      setError('File exceeds the 25MB upload limit')
      return
    }

    try {
      await mutation.mutateAsync({ id: document.id, file })
      resetAndClose()
    } catch {
      // error toast is shown by the mutation
    }
  }

  return (
    <Dialog open={document !== null} onOpenChange={(open) => !open && resetAndClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Re-ingest file</DialogTitle>
          <DialogDescription>
            Replace the indexed content of “{document?.title}” with a new upload. Conversations stay
            attached to this document.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="reingest-file">File</Label>
            <Input
              id="reingest-file"
              type="file"
              accept=".pdf,.md,.markdown"
              required
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            <p className="text-xs text-muted-foreground">Must match the original type. Up to 25MB.</p>
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={resetAndClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Re-ingesting…' : 'Re-ingest'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
