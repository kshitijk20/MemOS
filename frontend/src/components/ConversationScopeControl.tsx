import { useEffect, useState } from 'react'
import { Filter } from 'lucide-react'
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
import { DocumentScopePicker } from '@/components/DocumentScopePicker'
import { useDocumentsQuery } from '@/hooks/useDocuments'
import { useUpdateConversationScopeMutation } from '@/hooks/useConversations'
import type { ConversationResponse } from '@/api/types'

export function ConversationScopeControl({ conversation }: { conversation: ConversationResponse }) {
  const [open, setOpen] = useState(false)
  const [selectedIds, setSelectedIds] = useState<string[]>(conversation.documentScope ?? [])
  const { data: docsPage } = useDocumentsQuery(0, 100)
  const updateScope = useUpdateConversationScopeMutation(conversation.id)
  const readyDocs = docsPage?.items.filter((doc) => doc.status === 'COMPLETED') ?? []

  useEffect(() => {
    if (open) {
      setSelectedIds(conversation.documentScope ?? [])
    }
  }, [open, conversation.documentScope])

  const selectedTitles = readyDocs
    .filter((doc) => (conversation.documentScope ?? []).includes(doc.id))
    .map((doc) => doc.title)

  const label =
    (conversation.documentScope?.length ?? 0) === 0
      ? 'All documents'
      : selectedTitles.length > 0
        ? selectedTitles.length === 1
          ? selectedTitles[0]
          : `${selectedTitles[0]} +${selectedTitles.length - 1}`
        : `${conversation.documentScope.length} source${conversation.documentScope.length > 1 ? 's' : ''}`

  async function handleSave() {
    await updateScope.mutateAsync(selectedIds)
    setOpen(false)
  }

  return (
    <>
      <Button type="button" variant="outline" size="sm" className="max-w-[16rem] gap-1.5" onClick={() => setOpen(true)}>
        <Filter className="size-3.5 shrink-0" />
        <span className="truncate">{label}</span>
      </Button>
      {(conversation.documentScope?.length ?? 0) > 0 && selectedTitles.length > 1 && (
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
            <DialogTitle>Filter sources</DialogTitle>
            <DialogDescription>
              Limit this chat to specific PDFs, pages, or repositories. Leave empty to search everything.
            </DialogDescription>
          </DialogHeader>
          <DocumentScopePicker documents={readyDocs} selectedIds={selectedIds} onChange={setSelectedIds} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="button" onClick={handleSave} disabled={updateScope.isPending}>
              {updateScope.isPending ? 'Saving…' : 'Apply filter'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
