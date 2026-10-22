package com.memos.application.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.memos.domain.ai.AiProvider;
import com.memos.domain.common.PageRequest;
import com.memos.domain.common.PageResult;
import com.memos.domain.collection.CollectionRepositoryPort;
import com.memos.domain.conversation.Conversation;
import com.memos.domain.conversation.ConversationRepositoryPort;
import com.memos.domain.conversation.FeedbackEntry;
import com.memos.domain.conversation.Message;
import com.memos.domain.conversation.MessageFeedback;
import com.memos.domain.conversation.MessageRepositoryPort;
import com.memos.domain.conversation.MessageRole;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentRepositoryPort;
import com.memos.domain.document.DocumentStatus;
import com.memos.domain.exception.IngestionException;
import com.memos.domain.exception.InvalidRequestException;
import com.memos.domain.exception.ResourceNotFoundException;
import com.memos.infrastructure.ai.config.AiProvidersProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationApplicationService {

    private static final String DEFAULT_TITLE = "New conversation";
    static final int MAX_FEEDBACK_COMMENT_LENGTH = 2000;

    private final ConversationRepositoryPort conversationRepositoryPort;
    private final MessageRepositoryPort messageRepositoryPort;
    private final DocumentRepositoryPort documentRepositoryPort;
    private final CollectionRepositoryPort collectionRepositoryPort;
    private final AiProvidersProperties aiProvidersProperties;

    public Conversation create(
            UUID userId, String title, AiProvider provider, String model, List<UUID> documentScope, UUID collectionId) {
        if (collectionId != null) {
            requireCollection(userId, collectionId);
        }
        Instant now = Instant.now();
        Conversation conversation = Conversation.builder()
                .userId(userId)
                .title(title != null && !title.isBlank() ? title : DEFAULT_TITLE)
                .provider(provider != null ? provider : aiProvidersProperties.getDefaultProvider())
                .model(model)
                .documentScope(collectionId != null || documentScope == null ? List.of() : documentScope)
                .collectionId(collectionId)
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

    /**
     * Limits the conversation to a collection or to specific documents (a collection wins if both
     * are given); neither searches all of the user's documents.
     */
    public Conversation updateScope(UUID userId, UUID conversationId, List<UUID> documentIds, UUID collectionId) {
        Conversation conversation = get(userId, conversationId);
        if (collectionId != null) {
            requireCollection(userId, collectionId);
            return conversationRepositoryPort.save(conversation.toBuilder()
                    .collectionId(collectionId)
                    .documentScope(List.of())
                    .updatedAt(Instant.now())
                    .build());
        }
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
                .collectionId(null)
                .updatedAt(Instant.now())
                .build());
    }

    private void requireCollection(UUID userId, UUID collectionId) {
        if (collectionRepositoryPort.findByIdAndOwnerId(collectionId, userId).isEmpty()) {
            throw new ResourceNotFoundException("Collection", collectionId);
        }
    }

    public void delete(UUID userId, UUID conversationId) {
        if (!conversationRepositoryPort.existsByIdAndUserId(conversationId, userId)) {
            throw new ResourceNotFoundException("Conversation", conversationId);
        }
        conversationRepositoryPort.deleteById(conversationId);
    }

    /** Rates an assistant answer; {@code feedback == null} clears the rating. */
    public Message setFeedback(
            UUID userId, UUID conversationId, UUID messageId, MessageFeedback feedback, String comment) {
        if (!conversationRepositoryPort.existsByIdAndUserId(conversationId, userId)) {
            throw new ResourceNotFoundException("Conversation", conversationId);
        }
        Message message = messageRepositoryPort.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));
        if (message.getRole() != MessageRole.ASSISTANT) {
            throw new InvalidRequestException("Only assistant answers can be rated.");
        }

        String normalizedComment = feedback == null || comment == null || comment.isBlank() ? null : comment.strip();
        if (normalizedComment != null && normalizedComment.length() > MAX_FEEDBACK_COMMENT_LENGTH) {
            throw new InvalidRequestException(
                    "Feedback comment must be at most " + MAX_FEEDBACK_COMMENT_LENGTH + " characters.");
        }
        Instant feedbackAt = feedback == null ? null : Instant.now();

        messageRepositoryPort.updateFeedback(messageId, feedback, normalizedComment, feedbackAt);
        return message.toBuilder()
                .feedback(feedback)
                .feedbackComment(normalizedComment)
                .feedbackAt(feedbackAt)
                .build();
    }

    public PageResult<FeedbackEntry> listFeedback(UUID userId, MessageFeedback feedback, PageRequest pageRequest) {
        return messageRepositoryPort.findFeedbackByUserId(userId, feedback, pageRequest);
    }

    public PageResult<Message> listMessages(UUID userId, UUID conversationId, PageRequest pageRequest) {
        if (!conversationRepositoryPort.existsByIdAndUserId(conversationId, userId)) {
            throw new ResourceNotFoundException("Conversation", conversationId);
        }
        return messageRepositoryPort.findByConversationId(conversationId, pageRequest);
    }
}
