import { useState } from 'react'
import { MessageSquarePlus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ConversationsList } from '@/components/ConversationsList'
import { NewConversationDialog } from '@/components/NewConversationDialog'

export function ConversationsPage() {
  const [newConversationOpen, setNewConversationOpen] = useState(false)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Conversations</h1>
          <p className="text-muted-foreground">Chat with your documents using retrieval-augmented generation.</p>
        </div>
        <Button onClick={() => setNewConversationOpen(true)}>
          <MessageSquarePlus className="size-4" />
          New conversation
        </Button>
      </div>

      <ConversationsList onNewConversation={() => setNewConversationOpen(true)} />

      <NewConversationDialog open={newConversationOpen} onOpenChange={setNewConversationOpen} />
    </div>
  )
}
