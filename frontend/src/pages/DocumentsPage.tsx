import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Folder, FolderPlus, GitBranch, Globe, MessageSquarePlus, Pencil, Trash2, Upload } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { DocumentUploadDialog } from '@/components/DocumentUploadDialog'
import { DocumentGitDialog } from '@/components/DocumentGitDialog'
import { DocumentUrlDialog } from '@/components/DocumentUrlDialog'
import { DocumentsTable } from '@/components/DocumentsTable'
import { CollectionDialog } from '@/components/CollectionDialog'
import { DocumentCollectionsDialog } from '@/components/DocumentCollectionsDialog'
import { useCollectionsQuery, useDeleteCollectionMutation } from '@/hooks/useCollections'
import { useCreateConversationMutation } from '@/hooks/useConversations'
import { cn } from '@/lib/utils'
import type { CollectionResponse, DocumentResponse } from '@/api/types'

export function DocumentsPage() {
  const navigate = useNavigate()
  const [uploadOpen, setUploadOpen] = useState(false)
  const [gitOpen, setGitOpen] = useState(false)
  const [urlOpen, setUrlOpen] = useState(false)
  const [activeCollectionId, setActiveCollectionId] = useState<string | null>(null)
  const [collectionDialog, setCollectionDialog] = useState<{
    open: boolean
    collection?: CollectionResponse
    /** New key per opening so the dialog's form state starts from the collection. */
    key: number
  }>({ open: false, key: 0 })

  function openCollectionDialog(collection?: CollectionResponse) {
    setCollectionDialog((current) => ({ open: true, collection, key: current.key + 1 }))
  }
  const [deleteTarget, setDeleteTarget] = useState<CollectionResponse | null>(null)
  const [membershipTarget, setMembershipTarget] = useState<DocumentResponse | null>(null)

  const { data: collections = [] } = useCollectionsQuery()
  const deleteCollection = useDeleteCollectionMutation()
  const createConversation = useCreateConversationMutation()
  const activeCollection = collections.find((c) => c.id === activeCollectionId) ?? null

  async function chatWithCollection(collection: CollectionResponse) {
    try {
      const conversation = await createConversation.mutateAsync({ collectionId: collection.id })
      navigate(`/conversations/${conversation.id}`)
    } catch {
      // error toast shown by the mutation
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    await deleteCollection.mutateAsync(deleteTarget.id)
    if (activeCollectionId === deleteTarget.id) setActiveCollectionId(null)
    setDeleteTarget(null)
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Documents</h1>
          <p className="text-muted-foreground">Upload files, ingest a Git repository, or fetch a web page.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => setUrlOpen(true)}>
            <Globe className="size-4" />
            From URL
          </Button>
          <Button variant="outline" onClick={() => setGitOpen(true)}>
            <GitBranch className="size-4" />
            From Git
          </Button>
          <Button onClick={() => setUploadOpen(true)}>
            <Upload className="size-4" />
            Upload file
          </Button>
        </div>
      </div>

      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => setActiveCollectionId(null)}
            className={cn(
              'rounded-full border px-3 py-1 text-sm transition-colors',
              activeCollectionId === null ? 'border-primary bg-primary text-primary-foreground' : 'hover:bg-muted',
            )}
          >
            All documents
          </button>
          {collections.map((collection) => (
            <button
              key={collection.id}
              type="button"
              onClick={() => setActiveCollectionId(collection.id)}
              className={cn(
                'inline-flex max-w-[14rem] items-center gap-1.5 rounded-full border px-3 py-1 text-sm transition-colors',
                activeCollectionId === collection.id
                  ? 'border-primary bg-primary text-primary-foreground'
                  : 'hover:bg-muted',
              )}
            >
              <Folder className="size-3.5 shrink-0" />
              <span className="truncate">{collection.name}</span>
              <span className="opacity-70">{collection.documentIds.length}</span>
            </button>
          ))}
          <Button variant="ghost" size="sm" onClick={() => openCollectionDialog()}>
            <FolderPlus className="size-4" />
            New collection
          </Button>
        </div>

        {activeCollection && (
          <div className="flex flex-wrap items-center gap-2 rounded-lg border bg-muted/30 px-3 py-2">
            <p className="mr-auto text-sm text-muted-foreground">
              Chats limited to <span className="font-medium text-foreground">{activeCollection.name}</span> only
              search its {activeCollection.documentIds.length} document{activeCollection.documentIds.length === 1 ? '' : 's'}.
            </p>
            <Button
              size="sm"
              onClick={() => chatWithCollection(activeCollection)}
              disabled={createConversation.isPending}
            >
              <MessageSquarePlus className="size-4" />
              Chat with this collection
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => openCollectionDialog(activeCollection)}
            >
              <Pencil className="size-3.5" />
              Edit
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setDeleteTarget(activeCollection)}>
              <Trash2 className="size-3.5" />
              Delete
            </Button>
          </div>
        )}
      </div>

      <DocumentsTable collection={activeCollection} onManageCollections={setMembershipTarget} />

      <DocumentUploadDialog open={uploadOpen} onOpenChange={setUploadOpen} />
      <DocumentGitDialog open={gitOpen} onOpenChange={setGitOpen} />
      <DocumentUrlDialog open={urlOpen} onOpenChange={setUrlOpen} />
      <CollectionDialog
        key={collectionDialog.key}
        open={collectionDialog.open}
        collection={collectionDialog.collection}
        onOpenChange={(open) => setCollectionDialog((current) => ({ ...current, open }))}
        onSaved={(saved) => setActiveCollectionId(saved.id)}
      />
      <DocumentCollectionsDialog
        key={membershipTarget?.id ?? 'none'}
        document={membershipTarget}
        onOpenChange={(open) => {
          if (!open) setMembershipTarget(null)
        }}
        onCreateCollection={() => {
          setMembershipTarget(null)
          openCollectionDialog()
        }}
      />

      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete collection</DialogTitle>
            <DialogDescription>
              Delete “{deleteTarget?.name}”? Its documents are kept. Chats limited to this collection will search all
              documents instead.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={confirmDelete} disabled={deleteCollection.isPending}>
              {deleteCollection.isPending ? 'Deleting…' : 'Delete collection'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
