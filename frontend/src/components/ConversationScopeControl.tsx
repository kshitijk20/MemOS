import { useState } from 'react'
import { Filter, Folder } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { ScopeSelector } from '@/components/ScopeSelector'
import { useDocumentsQuery } from '@/hooks/useDocuments'
import { useCollectionsQuery } from '@/hooks/useCollections'
import { useUpdateConversationScopeMutation } from '@/hooks/useConversations'
import { normalizeScope, scopeOf } from '@/lib/scope'
import type { ConversationResponse, ConversationScope } from '@/api/types'

export function ConversationScopeControl({ conversation }: { conversation: ConversationResponse }) {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState<ConversationScope>(() => scopeOf(conversation))
  const { data: docsPage } = useDocumentsQuery(0, 100)
  const { data: collections = [] } = useCollectionsQuery()
  const updateScope = useUpdateConversationScopeMutation(conversation.id)
  const readyDocs = docsPage?.items.filter((doc) => doc.status === 'COMPLETED') ?? []

  const current = scopeOf(conversation)
  const collection =
    current.kind === 'collection' ? collections.find((c) => c.id === current.collectionId) : undefined
  const selectedTitles =
    current.kind === 'documents'
      ? readyDocs.filter((doc) => current.documentIds.includes(doc.id)).map((doc) => doc.title)
      : []

  const label =
    current.kind === 'all'
      ? 'All documents'
      : current.kind === 'collection'
        ? (collection?.name ?? 'Collection')
        : selectedTitles.length === 1
          ? selectedTitles[0]
          : selectedTitles.length > 1
            ? `${selectedTitles[0]} +${selectedTitles.length - 1}`
            : `${current.documentIds.length} source${current.documentIds.length > 1 ? 's' : ''}`

  async function handleSave() {
    await updateScope.mutateAsync(normalizeScope(draft))
    setOpen(false)
  }

  return (
    <>
      <Button type="button" variant="outline" size="sm" className="max-w-[16rem] gap-1.5" onClick={() => {
        setDraft(scopeOf(conversation))
        setOpen(true)
      }}>
        {current.kind === 'collection' ? (
          <Folder className="size-3.5 shrink-0" />
        ) : (
          <Filter className="size-3.5 shrink-0" />
        )}
        <span className="truncate">{label}</span>
      </Button>
      {selectedTitles.length > 1 && (
        <div className="hidden gap-1 sm:flex">
          {selectedTitles.slice(0, 3).map((title) => (
            <Badge key={title} variant="outline" className="max-w-[8rem] truncate text-xs font-normal">
              {title}
            </Badge>
          ))}
        </div>
      )}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>What should this chat search?</DialogTitle>
            <DialogDescription>
              Limit answers to a collection or to specific documents, or search everything.
            </DialogDescription>
          </DialogHeader>
          <ScopeSelector value={draft} onChange={setDraft} collections={collections} documents={readyDocs} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="button" onClick={handleSave} disabled={updateScope.isPending}>
              {updateScope.isPending ? 'Saving…' : 'Apply'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
