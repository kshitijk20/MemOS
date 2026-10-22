package com.memos.web.conversation;

import java.util.List;
import java.util.UUID;

public record UpdateDocumentScopeRequest(List<UUID> documentIds) {
}
