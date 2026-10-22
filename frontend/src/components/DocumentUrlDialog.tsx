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
import { useUrlIngestMutation } from '@/hooks/useDocuments'

interface DocumentUrlDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function DocumentUrlDialog({ open, onOpenChange }: DocumentUrlDialogProps) {
  const [url, setUrl] = useState('')
  const [title, setTitle] = useState('')
  const mutation = useUrlIngestMutation()

  function resetAndClose() {
    setUrl('')
    setTitle('')
    onOpenChange(false)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    try {
      await mutation.mutateAsync({ url: url.trim(), title: title.trim() })
      resetAndClose()
    } catch {
      // error toast shown by mutation
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? onOpenChange(next) : resetAndClose())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Ingest from URL</DialogTitle>
          <DialogDescription>
            Paste a web page URL. MemOS will fetch and index its content.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="url-title">Title</Label>
            <Input
              id="url-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. React Documentation"
              required
              maxLength={500}
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="url-input">URL</Label>
            <Input
              id="url-input"
              type="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://example.com/page"
              required
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={resetAndClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Fetching…' : 'Ingest'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
