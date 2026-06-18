package com.memos.application.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.memos.domain.ai.AiProvider;
import com.memos.domain.ai.ChatCompletionRequest;
import com.memos.domain.ai.ChatCompletionResult;
import com.memos.domain.ai.ChatPort;
import com.memos.domain.ai.EmbeddingPort;
import com.memos.domain.conversation.Conversation;
import com.memos.domain.conversation.ConversationRepositoryPort;
import com.memos.domain.conversation.Message;
import com.memos.domain.conversation.MessageRepositoryPort;
import com.memos.domain.conversation.MessageRole;
import com.memos.domain.exception.ResourceNotFoundException;
import com.memos.infrastructure.ai.chat.ChatPortResolver;

@ExtendWith(MockitoExtension.class)
class RagQueryServiceTest {

    @Mock
    private ConversationRepositoryPort conversationRepositoryPort;

    @Mock
    private MessageRepositoryPort messageRepositoryPort;

    @Mock
    private EmbeddingPort embeddingPort;

    @Mock
    private RetrievalPort retrievalPort;

    @Mock
    private ChatPortResolver chatPortResolver;

    @Mock
    private ChatPort chatPort;

    private RagQueryService ragQueryService;

    private final UUID userId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ragQueryService = new RagQueryService(
                conversationRepositoryPort, messageRepositoryPort, embeddingPort, retrievalPort, chatPortResolver);
        ReflectionTestUtils.setField(ragQueryService, "topK", 5);
        ReflectionTestUtils.setField(ragQueryService, "historyWindow", 10);
        ReflectionTestUtils.setField(ragQueryService, "maxContextChars", 12000);
    }

    @Test
    void askQuestionRetrievesContextAndPersistsAssistantReplyWithCitations() {
        Instant now = Instant.now();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .userId(userId)
                .title("Test conversation")
                .provider(AiProvider.GEMINI)
                .model(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));

        float[] queryEmbedding = new float[] {0.1f, 0.2f};
        when(embeddingPort.embed("What is MemOS?")).thenReturn(queryEmbedding);

        UUID chunkId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        RetrievedChunk retrievedChunk = new RetrievedChunk(chunkId, documentId, 0, "MemOS is a RAG assistant.", "intro.md", 0.92);
        when(retrievalPort.findSimilar(userId, queryEmbedding, 5)).thenReturn(List.of(retrievedChunk));

        Message persistedUserMessage = Message.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .role(MessageRole.USER)
                .content("What is MemOS?")
                .createdAt(now)
                .build();
        when(messageRepositoryPort.findRecentByConversationId(conversationId, 10)).thenReturn(List.of(persistedUserMessage));

        when(chatPortResolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        when(chatPort.complete(any(ChatCompletionRequest.class))).thenReturn(ChatCompletionResult.builder()
                .content("MemOS is a RAG-based knowledge assistant.")
                .modelUsed("gemini-2.0-flash")
                .promptTokens(42)
                .completionTokens(8)
                .build());

        when(messageRepositoryPort.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            return message.toBuilder().id(UUID.randomUUID()).build();
        });

        Message result = ragQueryService.askQuestion(userId, conversationId, "What is MemOS?");

        assertThat(result.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(result.getContent()).isEqualTo("MemOS is a RAG-based knowledge assistant.");
        assertThat(result.getCitations()).hasSize(1);
        assertThat(result.getCitations().get(0).getDocumentChunkId()).isEqualTo(chunkId);
        assertThat(result.getCitations().get(0).getDocumentId()).isEqualTo(documentId);
        assertThat(result.getCitations().get(0).getSourceTitle()).isEqualTo("intro.md");
        assertThat(result.getCitations().get(0).getScore()).isEqualTo(0.92);

        // first save() call persists the user's question
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepositoryPort, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues().get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(messageCaptor.getAllValues().get(0).getContent()).isEqualTo("What is MemOS?");
        assertThat(messageCaptor.getAllValues().get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);

        // the conversation's updated timestamp is touched
        verify(conversationRepositoryPort).save(any(Conversation.class));
    }

    @Test
    void askQuestionThrowsWhenConversationNotFoundOrNotOwned() {
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(ResourceNotFoundException.class,
                () -> ragQueryService.askQuestion(userId, conversationId, "anything"));

        verify(messageRepositoryPort, org.mockito.Mockito.never()).save(any());
        verify(embeddingPort, org.mockito.Mockito.never()).embed(any());
    }

    @Test
    void askQuestionFallsBackToDefaultProviderResolutionWhenConversationProviderNull() {
        Instant now = Instant.now();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .userId(userId)
                .title("Test conversation")
                .provider(null)
                .model(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(embeddingPort.embed(any())).thenReturn(new float[] {0.1f});
        when(retrievalPort.findSimilar(any(), any(), anyInt())).thenReturn(List.of());
        when(messageRepositoryPort.findRecentByConversationId(any(), anyInt())).thenReturn(List.of());
        when(chatPortResolver.resolve(null)).thenReturn(chatPort);
        when(chatPort.complete(any(ChatCompletionRequest.class))).thenReturn(ChatCompletionResult.builder()
                .content("Hello!")
                .modelUsed("gemini-2.0-flash")
                .build());
        when(messageRepositoryPort.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            return message.toBuilder().id(UUID.randomUUID()).build();
        });

        Message result = ragQueryService.askQuestion(userId, conversationId, "hi");

        assertThat(result.getContent()).isEqualTo("Hello!");
        verify(chatPortResolver).resolve(null);
    }
}
