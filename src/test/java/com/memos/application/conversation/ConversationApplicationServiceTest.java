package com.memos.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.memos.domain.ai.AiProvider;
import com.memos.domain.collection.Collection;
import com.memos.domain.collection.CollectionRepositoryPort;
import com.memos.domain.conversation.Conversation;
import com.memos.domain.conversation.ConversationRepositoryPort;
import com.memos.domain.conversation.Message;
import com.memos.domain.conversation.MessageFeedback;
import com.memos.domain.conversation.MessageRepositoryPort;
import com.memos.domain.conversation.MessageRole;
import com.memos.domain.document.DocumentRepositoryPort;
import com.memos.domain.exception.InvalidRequestException;
import com.memos.domain.exception.ResourceNotFoundException;
import com.memos.infrastructure.ai.config.AiProvidersProperties;

@ExtendWith(MockitoExtension.class)
class ConversationApplicationServiceTest {

    @Mock
    private ConversationRepositoryPort conversationRepositoryPort;

    @Mock
    private MessageRepositoryPort messageRepositoryPort;

    @Mock
    private DocumentRepositoryPort documentRepositoryPort;

    @Mock
    private CollectionRepositoryPort collectionRepositoryPort;

    private ConversationApplicationService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ConversationApplicationService(
                conversationRepositoryPort, messageRepositoryPort, documentRepositoryPort, collectionRepositoryPort,
                new AiProvidersProperties());
    }

    @Test
    void setFeedbackStoresRatingAndTrimmedComment() {
        when(conversationRepositoryPort.existsByIdAndUserId(conversationId, userId)).thenReturn(true);
        when(messageRepositoryPort.findByIdAndConversationId(messageId, conversationId))
                .thenReturn(Optional.of(message(MessageRole.ASSISTANT)));

        Message result = service.setFeedback(userId, conversationId, messageId, MessageFeedback.DOWN, "  wrong year  ");

        assertThat(result.getFeedback()).isEqualTo(MessageFeedback.DOWN);
        assertThat(result.getFeedbackComment()).isEqualTo("wrong year");
        verify(messageRepositoryPort).updateFeedback(eq(messageId), eq(MessageFeedback.DOWN), eq("wrong year"), notNull());
    }

    @Test
    void clearingFeedbackAlsoClearsCommentAndTimestamp() {
        when(conversationRepositoryPort.existsByIdAndUserId(conversationId, userId)).thenReturn(true);
        when(messageRepositoryPort.findByIdAndConversationId(messageId, conversationId))
                .thenReturn(Optional.of(message(MessageRole.ASSISTANT)));

        service.setFeedback(userId, conversationId, messageId, null, "ignored");

        verify(messageRepositoryPort).updateFeedback(eq(messageId), isNull(), isNull(), isNull());
    }

    @Test
    void setFeedbackRejectsUserMessages() {
        when(conversationRepositoryPort.existsByIdAndUserId(conversationId, userId)).thenReturn(true);
        when(messageRepositoryPort.findByIdAndConversationId(messageId, conversationId))
                .thenReturn(Optional.of(message(MessageRole.USER)));

        assertThatThrownBy(() -> service.setFeedback(userId, conversationId, messageId, MessageFeedback.UP, null))
                .isInstanceOf(InvalidRequestException.class);
        verify(messageRepositoryPort, never()).updateFeedback(any(), any(), any(), any());
    }

    @Test
    void setFeedbackRejectsConversationsTheUserDoesNotOwn() {
        when(conversationRepositoryPort.existsByIdAndUserId(conversationId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.setFeedback(userId, conversationId, messageId, MessageFeedback.UP, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void scopingToACollectionClearsTheDocumentList() {
        UUID collectionId = UUID.randomUUID();
        Conversation existing = conversation().toBuilder().documentScope(List.of(UUID.randomUUID())).build();
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(existing));
        when(collectionRepositoryPort.findByIdAndOwnerId(collectionId, userId)).thenReturn(Optional.of(
                Collection.builder().id(collectionId).ownerId(userId).name("HR").documentIds(Set.of()).build()));
        when(conversationRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Conversation result = service.updateScope(userId, conversationId, List.of(UUID.randomUUID()), collectionId);

        assertThat(result.getCollectionId()).isEqualTo(collectionId);
        assertThat(result.getDocumentScope()).isEmpty();
    }

    @Test
    void scopingToDocumentsClearsTheCollection() {
        Conversation existing = conversation().toBuilder().collectionId(UUID.randomUUID()).build();
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(existing));
        when(conversationRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Conversation result = service.updateScope(userId, conversationId, List.of(), null);

        assertThat(result.getCollectionId()).isNull();
    }

    @Test
    void createRejectsSomeoneElsesCollection() {
        UUID collectionId = UUID.randomUUID();
        when(collectionRepositoryPort.findByIdAndOwnerId(collectionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(userId, null, AiProvider.GEMINI, null, List.of(), collectionId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(conversationRepositoryPort, never()).save(any());
    }

    @Test
    void createWithCollectionIgnoresDocumentList() {
        UUID collectionId = UUID.randomUUID();
        when(collectionRepositoryPort.findByIdAndOwnerId(collectionId, userId)).thenReturn(Optional.of(
                Collection.builder().id(collectionId).ownerId(userId).name("HR").documentIds(Set.of()).build()));
        when(conversationRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(userId, null, AiProvider.GEMINI, null, List.of(UUID.randomUUID()), collectionId);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepositoryPort).save(captor.capture());
        assertThat(captor.getValue().getCollectionId()).isEqualTo(collectionId);
        assertThat(captor.getValue().getDocumentScope()).isEmpty();
    }

    private Message message(MessageRole role) {
        return Message.builder()
                .id(messageId).conversationId(conversationId).role(role).content("x").createdAt(Instant.now()).build();
    }

    private Conversation conversation() {
        return Conversation.builder()
                .id(conversationId).userId(userId).title("t").provider(AiProvider.GEMINI)
                .documentScope(List.of()).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }
}
