import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { MessageSquarePlus, Trash2 } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useConversationsQuery, useDeleteConversationMutation } from '@/hooks/useConversations'
import type { ConversationResponse } from '@/api/types'

const PAGE_SIZE = 10

function formatDate(value: string): string {
  return new Date(value).toLocaleString()
}

export function ConversationsList({ onNewConversation }: { onNewConversation: () => void }) {
  const [page, setPage] = useState(0)
  const [deleteTarget, setDeleteTarget] = useState<ConversationResponse | null>(null)
  const { data, isLoading } = useConversationsQuery(page, PAGE_SIZE)
  const deleteMutation = useDeleteConversationMutation()
  const navigate = useNavigate()

  async function confirmDelete() {
    if (!deleteTarget) return
    await deleteMutation.mutateAsync(deleteTarget.id)
    setDeleteTarget(null)
  }

  if (isLoading) {
    return (
      <div className="flex flex-col gap-2">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-20 w-full" />
        ))}
      </div>
    )
  }

  if (!data || data.items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed py-12 text-center">
        <p className="text-sm font-medium">No conversations yet</p>
        <p className="text-sm text-muted-foreground">Start a new conversation to chat with your documents.</p>
        <Button onClick={onNewConversation}>
          <MessageSquarePlus className="size-4" />
          New conversation
        </Button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        {data.items.map((conversation) => (
          <Card
            key={conversation.id}
            className="flex cursor-pointer flex-row items-center justify-between gap-4 p-4 transition-colors hover:bg-muted/50"
            onClick={() => navigate(`/conversations/${conversation.id}`)}
          >
            <div className="flex flex-col gap-1">
              <span className="font-medium">{conversation.title ?? 'Untitled conversation'}</span>
              <div className="flex items-center gap-2">
                <Badge variant="secondary">{conversation.provider}</Badge>
                <span className="text-xs text-muted-foreground">{conversation.model}</span>
              </div>
              <span className="text-xs text-muted-foreground">Updated {formatDate(conversation.updatedAt)}</span>
            </div>
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={(e) => {
                e.stopPropagation()
                setDeleteTarget(conversation)
              }}
            >
              <Trash2 className="size-4" />
              <span className="sr-only">Delete</span>
            </Button>
          </Card>
        ))}
      </div>

      {data.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Page {data.page + 1} of {data.totalPages}
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}

      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete conversation</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete "{deleteTarget?.title ?? 'Untitled conversation'}"? This action cannot
              be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={confirmDelete} disabled={deleteMutation.isPending}>
              {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
