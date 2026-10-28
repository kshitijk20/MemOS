import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { extractErrorMessage } from '@/api/client'
import { useAuth } from '@/lib/use-auth'

interface DeleteAccountDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function DeleteAccountDialog({ open, onOpenChange }: DeleteAccountDialogProps) {
  const { deleteAccount } = useAuth()
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const confirmed = confirmation.trim().toUpperCase() === 'DELETE'

  function handleOpenChange(next: boolean) {
    if (isDeleting) return
    if (!next) {
      setPassword('')
      setConfirmation('')
      setError(null)
    }
    onOpenChange(next)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setIsDeleting(true)
    try {
      await deleteAccount(password)
      toast.success('Your account and all its data have been deleted.')
      navigate('/login', { replace: true })
    } catch (err) {
      setError(extractErrorMessage(err))
      setIsDeleting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-md">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>Delete your account?</DialogTitle>
            <DialogDescription>
              This permanently deletes your account, documents, conversations and collections. It can't be undone.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <Label htmlFor="delete-password">Password</Label>
            <Input
              id="delete-password"
              type="password"
              autoComplete="current-password"
              required
              className="h-10"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="delete-confirmation">
              Type <span className="font-mono font-semibold">DELETE</span> to confirm
            </Label>
            <Input
              id="delete-confirmation"
              autoComplete="off"
              required
              className="h-10"
              value={confirmation}
              onChange={(e) => setConfirmation(e.target.value)}
            />
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          <DialogFooter>
            <Button type="button" variant="outline" disabled={isDeleting} onClick={() => handleOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="destructive" disabled={!password || !confirmed || isDeleting}>
              {isDeleting ? 'Deleting…' : 'Delete account'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
