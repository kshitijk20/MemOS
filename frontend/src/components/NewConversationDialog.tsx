import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useCreateConversationMutation } from '@/hooks/useConversations'
import type { AiProvider } from '@/api/types'

const PROVIDERS: AiProvider[] = ['GEMINI', 'OPENAI', 'CLAUDE']

interface NewConversationDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function NewConversationDialog({ open, onOpenChange }: NewConversationDialogProps) {
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [provider, setProvider] = useState<AiProvider>('GEMINI')
  const [model, setModel] = useState('')
  const createMutation = useCreateConversationMutation()

  function resetAndClose() {
    setTitle('')
    setProvider('GEMINI')
    setModel('')
    onOpenChange(false)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    try {
      const conversation = await createMutation.mutateAsync({
        title: title.trim() === '' ? undefined : title.trim(),
        provider,
        model: model.trim() === '' ? undefined : model.trim(),
      })
      resetAndClose()
      navigate(`/conversations/${conversation.id}`)
    } catch {
      // error toast is shown by the mutation; keep dialog open for retry
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? onOpenChange(next) : resetAndClose())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New conversation</DialogTitle>
          <DialogDescription>Start a new conversation with an AI provider.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="conv-title">Title (optional)</Label>
            <Input
              id="conv-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Project Q&A"
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="conv-provider">Provider</Label>
            <Select value={provider} onValueChange={(value) => setProvider(value as AiProvider)}>
              <SelectTrigger id="conv-provider" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PROVIDERS.map((p) => (
                  <SelectItem key={p} value={p}>
                    {p}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="conv-model">Model (optional)</Label>
            <Input
              id="conv-model"
              value={model}
              onChange={(e) => setModel(e.target.value)}
              placeholder="Uses server default if left blank"
            />
          </div>
          <DialogFooter>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Creating…' : 'Create'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
