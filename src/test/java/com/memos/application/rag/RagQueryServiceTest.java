package com.memos.application.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
                conversationRepositoryPort,
                messageRepositoryPort,
                embeddingPort,
                retrievalPort,
                new LexicalOverlapReranker(),
                chatPortResolver);
        ReflectionTestUtils.setField(ragQueryService, "topK", 5);
        ReflectionTestUtils.setField(ragQueryService, "historyWindow", 10);
        ReflectionTestUtils.setField(ragQueryService, "maxContextChars", 12000);
        ReflectionTestUtils.setField(ragQueryService, "candidatePool", 20);
        ReflectionTestUtils.setField(ragQueryService, "minScore", 0.35);
    }

    @Test
    void askQuestionRetrievesContextAndPersistsAssistantReplyWithCitations() {
        Instant now = Instant.now();
        Conversation conversation = conversation("Test conversation", null);

        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));

        float[] queryEmbedding = new float[] {0.1f, 0.2f};
        when(embeddingPort.embed("What is MemOS?")).thenReturn(queryEmbedding);

        UUID chunkId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        RetrievedChunk retrievedChunk = new RetrievedChunk(chunkId, documentId, 0, "MemOS is a RAG assistant.", "intro.md", 0.92);
        when(retrievalPort.findHybrid(eq(userId), eq("What is MemOS?"), eq(queryEmbedding), eq(List.of()), eq(20)))
                .thenReturn(List.of(retrievedChunk));

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

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepositoryPort, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues().get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(messageCaptor.getAllValues().get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);

        verify(conversationRepositoryPort).save(any(Conversation.class));
    }

    @Test
    void askQuestionUsesDocumentScopeWhenPresent() {
        UUID scopedDoc = UUID.randomUUID();
        Conversation conversation = conversation("Test conversation", List.of(scopedDoc));
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(embeddingPort.embed("scoped?")).thenReturn(new float[] {0.2f});
        RetrievedChunk chunk = new RetrievedChunk(
                UUID.randomUUID(), scopedDoc, 0, "scoped content about APIs", "only.pdf", 0.88);
        when(retrievalPort.findHybrid(eq(userId), eq("scoped?"), any(), eq(List.of(scopedDoc)), eq(20)))
                .thenReturn(List.of(chunk));
        when(messageRepositoryPort.findRecentByConversationId(any(), anyInt())).thenReturn(List.of());
        when(chatPortResolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        when(chatPort.complete(any())).thenReturn(ChatCompletionResult.builder().content("ok").build());
        when(messageRepositoryPort.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ragQueryService.askQuestion(userId, conversationId, "scoped?");

        verify(retrievalPort).findHybrid(eq(userId), eq("scoped?"), any(), eq(List.of(scopedDoc)), eq(20));
    }

    @Test
    void askQuestionAbstainsWhenRetrievalIsWeak() {
        Conversation conversation = conversation("Test conversation", null);
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(embeddingPort.embed("unrelated trivia")).thenReturn(new float[] {0.1f});
        RetrievedChunk weak = new RetrievedChunk(
                UUID.randomUUID(), UUID.randomUUID(), 0, "zzzz unrelated passage", "notes.md", 0.12, 0.12, false);
        when(retrievalPort.findHybrid(any(), anyString(), any(), anyList(), anyInt())).thenReturn(List.of(weak));
        when(messageRepositoryPort.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            return message.toBuilder().id(UUID.randomUUID()).build();
        });

        Message result = ragQueryService.askQuestion(userId, conversationId, "unrelated trivia");

        assertThat(result.getContent()).isEqualTo(RagQueryService.UNSURE_ANSWER);
        assertThat(result.getCitations()).isEmpty();
        verify(chatPortResolver, never()).resolve(any());
        verify(chatPort, never()).complete(any());
    }

    @Test
    void askQuestionDoesNotAbstainOnLexicalHitEvenIfVectorIsWeak() {
        Conversation conversation = conversation("Test conversation", null);
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(embeddingPort.embed("ECONNRESET from fetch")).thenReturn(new float[] {0.1f});
        RetrievedChunk lexical = new RetrievedChunk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                "Node threw ECONNRESET during fetch",
                "errors.md",
                0.02,
                0.11,
                true);
        when(retrievalPort.findHybrid(any(), anyString(), any(), anyList(), anyInt())).thenReturn(List.of(lexical));
        when(messageRepositoryPort.findRecentByConversationId(any(), anyInt())).thenReturn(List.of());
        when(chatPortResolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        when(chatPort.complete(any())).thenReturn(ChatCompletionResult.builder().content("connection reset").build());
        when(messageRepositoryPort.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message result = ragQueryService.askQuestion(userId, conversationId, "ECONNRESET from fetch");

        assertThat(result.getContent()).isEqualTo("connection reset");
        verify(chatPort).complete(any());
    }

    @Test
    void askQuestionThrowsWhenConversationNotFoundOrNotOwned() {
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(ResourceNotFoundException.class,
                () -> ragQueryService.askQuestion(userId, conversationId, "anything"));

        verify(messageRepositoryPort, never()).save(any());
        verify(embeddingPort, never()).embed(any());
    }

    @Test
    void askQuestionFallsBackToDefaultProviderResolutionWhenConversationProviderNull() {
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .userId(userId)
                .title("Test conversation")
                .provider(null)
                .model(null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(embeddingPort.embed(any())).thenReturn(new float[] {0.1f});
        RetrievedChunk chunk = new RetrievedChunk(
                UUID.randomUUID(), UUID.randomUUID(), 0, "hello there", "intro.md", 0.9);
        when(retrievalPort.findHybrid(any(), anyString(), any(), anyList(), anyInt())).thenReturn(List.of(chunk));
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

    private Conversation conversation(String title, List<UUID> scope) {
        Instant now = Instant.now();
        return Conversation.builder()
                .id(conversationId)
                .userId(userId)
                .title(title)
                .provider(AiProvider.GEMINI)
                .model(null)
                .documentScope(scope)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
