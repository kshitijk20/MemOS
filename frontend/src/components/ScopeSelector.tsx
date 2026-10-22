import { Link } from 'react-router-dom'
import { Check, Folder } from 'lucide-react'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { DocumentScopePicker } from '@/components/DocumentScopePicker'
import { cn } from '@/lib/utils'
import type { CollectionResponse, ConversationScope, DocumentResponse } from '@/api/types'

interface ScopeSelectorProps {
  value: ConversationScope
  onChange: (scope: ConversationScope) => void
  collections: CollectionResponse[]
  /** Documents ready to search (COMPLETED). */
  documents: DocumentResponse[]
}

/** Choose what a chat searches: all documents, one collection, or hand-picked documents. */
export function ScopeSelector({ value, onChange, collections, documents }: ScopeSelectorProps) {
  const readyIds = new Set(documents.map((doc) => doc.id))

  return (
    <Tabs
      value={value.kind}
      onValueChange={(kind) => {
        if (kind === 'all') onChange({ kind: 'all' })
        // The Collection tab is disabled when there are none, so collections[0] exists here.
        else if (kind === 'collection') onChange(value.kind === 'collection' ? value : { kind: 'collection', collectionId: collections[0].id })
        else onChange({ kind: 'documents', documentIds: value.kind === 'documents' ? value.documentIds : [] })
      }}
    >
      <TabsList className="w-full">
        <TabsTrigger value="all">All documents</TabsTrigger>
        <TabsTrigger value="collection" disabled={collections.length === 0}>
          Collection
        </TabsTrigger>
        <TabsTrigger value="documents">Pick documents</TabsTrigger>
      </TabsList>

      <TabsContent value="all">
        <p className="text-xs text-muted-foreground">Answers can use any of your {documents.length} ready documents.</p>
      </TabsContent>

      <TabsContent value="collection">
        <div className="flex max-h-52 flex-col gap-1 overflow-y-auto rounded-md border p-2">
          {collections.map((collection) => {
            const selected = value.kind === 'collection' && value.collectionId === collection.id
            const ready = collection.documentIds.filter((id) => readyIds.has(id)).length
            return (
              <button
                key={collection.id}
                type="button"
                onClick={() => onChange({ kind: 'collection', collectionId: collection.id })}
                className={cn(
                  'flex items-center gap-2 rounded px-2 py-1.5 text-left text-sm transition-colors',
                  selected ? 'bg-primary/10' : 'hover:bg-muted',
                )}
              >
                <Folder className="size-3.5 shrink-0 text-muted-foreground" />
                <span className="min-w-0 flex-1 truncate">{collection.name}</span>
                <span className="text-xs text-muted-foreground">
                  {ready} doc{ready === 1 ? '' : 's'}
                </span>
                {selected && <Check className="size-3.5 text-primary" />}
              </button>
            )
          })}
        </div>
        <p className="mt-2 text-xs text-muted-foreground">
          Documents added to the collection later are included automatically.{' '}
          <Link to="/documents" className="underline underline-offset-2">
            Manage collections
          </Link>
        </p>
      </TabsContent>

      <TabsContent value="documents">
        <DocumentScopePicker
          documents={documents}
          selectedIds={value.kind === 'documents' ? value.documentIds : []}
          onChange={(documentIds) => onChange({ kind: 'documents', documentIds })}
          summary={(count) =>
            count === 0
              ? 'Pick at least one document, or switch to All documents.'
              : `${count} document${count > 1 ? 's' : ''} — answers only use these.`
          }
        />
      </TabsContent>
    </Tabs>
  )
}

