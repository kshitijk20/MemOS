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
import { useDocumentsQuery } from '@/hooks/useDocuments'
import { ScopeSelector } from '@/components/ScopeSelector'
import { useCollectionsQuery } from '@/hooks/useCollections'
import { normalizeScope } from '@/lib/scope'
import type { AiProvider, ConversationScope } from '@/api/types'

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
  const [scope, setScope] = useState<ConversationScope>({ kind: 'all' })
  const createMutation = useCreateConversationMutation()
  const { data: docsPage } = useDocumentsQuery(0, 100)
  const { data: collections = [] } = useCollectionsQuery()

  const readyDocs = docsPage?.items.filter((d) => d.status === 'COMPLETED') ?? []

  function resetAndClose() {
    setTitle('')
    setProvider('GEMINI')
    setModel('')
    setScope({ kind: 'all' })
    onOpenChange(false)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const effective = normalizeScope(scope)
    try {
      const conversation = await createMutation.mutateAsync({
        title: title.trim() === '' ? undefined : title.trim(),
        provider,
        model: model.trim() === '' ? undefined : model.trim(),
        documentIds: effective.kind === 'documents' ? effective.documentIds : undefined,
        collectionId: effective.kind === 'collection' ? effective.collectionId : undefined,
      })
      resetAndClose()
      navigate(`/conversations/${conversation.id}`)
    } catch {
      // error toast shown by mutation
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? onOpenChange(next) : resetAndClose())}>
      <DialogContent className="max-w-md">
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

          {readyDocs.length > 0 && (
            <div className="flex flex-col gap-2">
              <Label>What should this chat search?</Label>
              <ScopeSelector value={scope} onChange={setScope} collections={collections} documents={readyDocs} />
            </div>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={resetAndClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Creating…' : 'Create'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
