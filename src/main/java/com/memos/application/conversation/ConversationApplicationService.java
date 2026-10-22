package com.memos.application.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.memos.domain.ai.AiProvider;
import com.memos.domain.common.PageRequest;
import com.memos.domain.common.PageResult;
import com.memos.domain.conversation.Conversation;
import com.memos.domain.conversation.ConversationRepositoryPort;
import com.memos.domain.conversation.Message;
import com.memos.domain.conversation.MessageRepositoryPort;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentRepositoryPort;
import com.memos.domain.document.DocumentStatus;
import com.memos.domain.exception.IngestionException;
import com.memos.domain.exception.ResourceNotFoundException;
import com.memos.infrastructure.ai.config.AiProvidersProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationApplicationService {

    private static final String DEFAULT_TITLE = "New conversation";

    private final ConversationRepositoryPort conversationRepositoryPort;
    private final MessageRepositoryPort messageRepositoryPort;
    private final DocumentRepositoryPort documentRepositoryPort;
    private final AiProvidersProperties aiProvidersProperties;

    public Conversation create(UUID userId, String title, AiProvider provider, String model, List<UUID> documentScope) {
        Instant now = Instant.now();
        Conversation conversation = Conversation.builder()
                .userId(userId)
                .title(title != null && !title.isBlank() ? title : DEFAULT_TITLE)
                .provider(provider != null ? provider : aiProvidersProperties.getDefaultProvider())
                .model(model)
                .documentScope(documentScope != null ? documentScope : List.of())
                .createdAt(now)
                .updatedAt(now)
                .build();
        return conversationRepositoryPort.save(conversation);
    }

    public PageResult<Conversation> list(UUID userId, PageRequest pageRequest) {
        return conversationRepositoryPort.findAllByUserId(userId, pageRequest);
    }

    public Conversation get(UUID userId, UUID conversationId) {
        return conversationRepositoryPort.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
    }

    public Conversation updateDocumentScope(UUID userId, UUID conversationId, List<UUID> documentIds) {
        Conversation conversation = get(userId, conversationId);
        List<UUID> scope = documentIds == null ? List.of() : List.copyOf(documentIds);
        for (UUID documentId : scope) {
            Document document = documentRepositoryPort.findByIdAndOwnerId(documentId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
            if (document.getStatus() != DocumentStatus.COMPLETED) {
                throw new IngestionException(
                        "Only completed documents can be used as chat filters. \"" + document.getTitle() + "\" is "
                                + document.getStatus() + ".");
            }
        }
        return conversationRepositoryPort.save(conversation.toBuilder()
                .documentScope(scope)
                .updatedAt(Instant.now())
                .build());
    }

    public void delete(UUID userId, UUID conversationId) {
        if (!conversationRepositoryPort.existsByIdAndUserId(conversationId, userId)) {
            throw new ResourceNotFoundException("Conversation", conversationId);
        }
        conversationRepositoryPort.deleteById(conversationId);
    }

    public PageResult<Message> listMessages(UUID userId, UUID conversationId, PageRequest pageRequest) {
        if (!conversationRepositoryPort.existsByIdAndUserId(conversationId, userId)) {
            throw new ResourceNotFoundException("Conversation", conversationId);
        }
        return messageRepositoryPort.findByConversationId(conversationId, pageRequest);
    }
}
