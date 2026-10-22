package com.memos.web.conversation;

import java.util.List;
import java.util.UUID;

/** Either a collection or an explicit document list; both empty means all documents. */
public record UpdateDocumentScopeRequest(List<UUID> documentIds, UUID collectionId) {
}
