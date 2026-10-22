import type { ConversationScope } from '@/api/types'

/** Converts a conversation's stored scope into a {@link ConversationScope}. */
export function scopeOf(conversation: { collectionId: string | null; documentScope: string[] }): ConversationScope {
  if (conversation.collectionId) return { kind: 'collection', collectionId: conversation.collectionId }
  if (conversation.documentScope?.length) return { kind: 'documents', documentIds: conversation.documentScope }
  return { kind: 'all' }
}

/** A scope that would actually search something: picking zero documents means all documents. */
export function normalizeScope(scope: ConversationScope): ConversationScope {
  return scope.kind === 'documents' && scope.documentIds.length === 0 ? { kind: 'all' } : scope
}
