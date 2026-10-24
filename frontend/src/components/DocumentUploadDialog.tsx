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
import { MAX_FILE_SIZE_BYTES, MAX_UPLOAD_MB } from '@/lib/upload-limit'
import { useUploadDocumentMutation } from '@/hooks/useDocuments'


function formatMegabytes(bytes: number) {
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`
}

interface DocumentUploadDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function DocumentUploadDialog({ open, onOpenChange }: DocumentUploadDialogProps) {
  const [title, setTitle] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const uploadMutation = useUploadDocumentMutation()

  function resetAndClose() {
    setTitle('')
    setFile(null)
    setError(null)
    onOpenChange(false)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (!file) {
      setError('Please select a file')
      return
    }
    if (file.size > MAX_FILE_SIZE_BYTES) {
      setError(`File exceeds the ${MAX_UPLOAD_MB}MB upload limit`)
      return
    }

    try {
      await uploadMutation.mutateAsync({ file, title })
      resetAndClose()
    } catch {
      // error toast is shown by the mutation; keep dialog open for retry
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? onOpenChange(next) : resetAndClose())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Upload document</DialogTitle>
          <DialogDescription>Upload a PDF or Markdown file to ingest into MemOS.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="doc-title">Title</Label>
            <Input
              id="doc-title"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Architecture overview"
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="doc-file">File</Label>
            <Input
              id="doc-file"
              type="file"
              accept=".pdf,.md,.markdown"
              required
              onChange={(e) => {
                const selected = e.target.files?.[0] ?? null
                setFile(selected)
                setError(
                  selected && selected.size > MAX_FILE_SIZE_BYTES
                    ? `File is ${formatMegabytes(selected.size)} — exceeds the ${MAX_UPLOAD_MB}MB upload limit`
                    : null,
                )
              }}
            />
            <p className="text-xs text-muted-foreground">{`PDF or Markdown, up to ${MAX_UPLOAD_MB}MB.`}</p>
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          <DialogFooter>
            <Button type="submit" disabled={uploadMutation.isPending}>
              {uploadMutation.isPending ? 'Uploading…' : 'Upload'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
