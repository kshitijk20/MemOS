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
import { useGitIngestMutation } from '@/hooks/useDocuments'

interface DocumentGitDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function DocumentGitDialog({ open, onOpenChange }: DocumentGitDialogProps) {
  const [repoUrl, setRepoUrl] = useState('')
  const [title, setTitle] = useState('')
  const [branch, setBranch] = useState('')
  const gitIngestMutation = useGitIngestMutation()

  function resetAndClose() {
    setRepoUrl('')
    setTitle('')
    setBranch('')
    onOpenChange(false)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    try {
      await gitIngestMutation.mutateAsync({
        repoUrl,
        title,
        branch: branch.trim() === '' ? undefined : branch.trim(),
      })
      resetAndClose()
    } catch {
      // error toast is shown by the mutation; keep dialog open for retry
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? onOpenChange(next) : resetAndClose())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Ingest from Git repository</DialogTitle>
          <DialogDescription>Clone and ingest all supported files from a Git repository.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="git-title">Title</Label>
            <Input
              id="git-title"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. memos-backend"
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="git-repo-url">Repository URL</Label>
            <Input
              id="git-repo-url"
              required
              value={repoUrl}
              onChange={(e) => setRepoUrl(e.target.value)}
              placeholder="https://github.com/org/repo.git"
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="git-branch">Branch (optional)</Label>
            <Input
              id="git-branch"
              value={branch}
              onChange={(e) => setBranch(e.target.value)}
              placeholder="main"
            />
          </div>
          <DialogFooter>
            <Button type="submit" disabled={gitIngestMutation.isPending}>
              {gitIngestMutation.isPending ? 'Starting…' : 'Ingest'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
