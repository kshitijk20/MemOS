package com.memos.application.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

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
import com.memos.domain.ai.ChatMessage;
import com.memos.domain.ai.ChatPort;
import com.memos.domain.ai.EmbeddingPort;
import com.memos.domain.collection.Collection;
import com.memos.domain.collection.CollectionRepositoryPort;
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
    private CollectionRepositoryPort collectionRepositoryPort;

    @Mock
    private EmbeddingPort embeddingPort;

    @Mock
    private RetrievalPort retrievalPort;

    @Mock
    private ChatPortResolver chatPortResolver;

    @Mock
    private ChatPort chatPort;

    @Mock
    private FollowUpQueryRewriter followUpQueryRewriter;

    private RagQueryService ragQueryService;

    private final UUID userId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ragQueryService = new RagQueryService(
                conversationRepositoryPort,
                messageRepositoryPort,
                collectionRepositoryPort,
                embeddingPort,
                retrievalPort,
                new LexicalOverlapReranker(),
                chatPortResolver,
                followUpQueryRewriter);
        ReflectionTestUtils.setField(ragQueryService, "topK", 5);
        ReflectionTestUtils.setField(ragQueryService, "historyWindow", 10);
        ReflectionTestUtils.setField(ragQueryService, "maxContextChars", 12000);
        ReflectionTestUtils.setField(ragQueryService, "candidatePool", 20);
        ReflectionTestUtils.setField(ragQueryService, "minScore", 0.35);

        // By default no rewrite happens: the search query is the question itself.
        lenient().when(followUpQueryRewriter.rewrite(any(), anyList(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(messageRepositoryPort.findRecentByConversationId(any(), anyInt())).thenReturn(List.of());
        lenient().when(messageRepositoryPort.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            return message.toBuilder().id(UUID.randomUUID()).build();
        });
    }

    @Test
    void askQuestionRetrievesContextAndPersistsAssistantReplyWithCitations() {
        Conversation conversation = conversation("Test conversation", null);
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));

        float[] queryEmbedding = new float[] {0.1f, 0.2f};
        when(embeddingPort.embed("What is MemOS?")).thenReturn(queryEmbedding);

        UUID chunkId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        RetrievedChunk retrievedChunk = new RetrievedChunk(chunkId, documentId, 0, "MemOS is a RAG assistant.", "intro.md", 0.92);
        when(retrievalPort.findHybrid(eq(userId), eq("What is MemOS?"), eq(queryEmbedding), eq(List.of()), eq(20)))
                .thenReturn(List.of(retrievedChunk));

        Message earlier = Message.builder()
                .conversationId(conversationId).role(MessageRole.USER).content("Hi").createdAt(Instant.now()).build();
        when(messageRepositoryPort.findRecentByConversationId(conversationId, 9)).thenReturn(List.of(earlier));

        when(chatPortResolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        when(chatPort.complete(any(ChatCompletionRequest.class))).thenReturn(ChatCompletionResult.builder()
                .content("MemOS is a RAG-based knowledge assistant [1].")
                .build());

        Message result = ragQueryService.askQuestion(userId, conversationId, "What is MemOS?");

        assertThat(result.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(result.getContent()).isEqualTo("MemOS is a RAG-based knowledge assistant [1].");
        assertThat(result.isUnanswered()).isFalse();
        assertThat(result.getSearchQuery()).isNull();
        assertThat(result.getCitations()).hasSize(1);
        assertThat(result.getCitations().get(0).getDocumentChunkId()).isEqualTo(chunkId);
        assertThat(result.getCitations().get(0).getDocumentId()).isEqualTo(documentId);
        assertThat(result.getCitations().get(0).getSourceTitle()).isEqualTo("intro.md");
        assertThat(result.getCitations().get(0).getSourceNumber()).isEqualTo(1);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepositoryPort, times(2)).save(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues().get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(messageCaptor.getAllValues().get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);

        // Prompt = system sources + earlier history + the current question, in order.
        ArgumentCaptor<ChatCompletionRequest> requestCaptor = ArgumentCaptor.forClass(ChatCompletionRequest.class);
        verify(chatPort).complete(requestCaptor.capture());
        List<ChatMessage> prompt = requestCaptor.getValue().getMessages();
        assertThat(prompt).hasSize(3);
        assertThat(prompt.get(0).getContent()).contains("[1] Source: intro.md");
        assertThat(prompt.get(1).getContent()).isEqualTo("Hi");
        assertThat(prompt.get(2).getContent()).isEqualTo("What is MemOS?");

        verify(conversationRepositoryPort).save(any(Conversation.class));
    }

    @Test
    void citationsKeepOnlyTheSourcesTheAnswerReferences() {
        Conversation conversation = conversation("Test conversation", null);
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(embeddingPort.embed(any())).thenReturn(new float[] {0.1f});
        RetrievedChunk first = chunk("alpha release notes", "a.md", 0.9);
        RetrievedChunk second = chunk("beta release notes", "b.md", 0.8);
        RetrievedChunk third = chunk("gamma release notes", "c.md", 0.7);
        when(retrievalPort.findHybrid(any(), anyString(), any(), anyList(), anyInt()))
                .thenReturn(List.of(first, second, third));
        when(chatPortResolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        when(chatPort.complete(any())).thenReturn(ChatCompletionResult.builder()
                .content("Release notes are in [3] and also [1].").build());

        Message result = ragQueryService.askQuestion(userId, conversationId, "release notes");

        assertThat(result.getCitations()).extracting(c -> c.getSourceNumber()).containsExactly(1, 3);
        assertThat(result.getCitations()).extracting(c -> c.getSourceTitle()).containsExactly("a.md", "c.md");
    }

    @Test
    void citationsFallBackToAllSourcesWhenAnswerCitesNone() {
        Conversation conversation = conversation("Test conversation", null);
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(embeddingPort.embed(any())).thenReturn(new float[] {0.1f});
        when(retrievalPort.findHybrid(any(), anyString(), any(), anyList(), anyInt()))
                .thenReturn(List.of(chunk("alpha notes", "a.md", 0.9), chunk("beta notes", "b.md", 0.8)));
        when(chatPortResolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        when(chatPort.complete(any())).thenReturn(ChatCompletionResult.builder().content("Some notes.").build());

        Message result = ragQueryService.askQuestion(userId, conversationId, "notes");

        assertThat(result.getCitations()).extracting(c -> c.getSourceNumber()).containsExactly(1, 2);
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
        when(chatPortResolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        when(chatPort.complete(any())).thenReturn(ChatCompletionResult.builder().content("ok").build());

        ragQueryService.askQuestion(userId, conversationId, "scoped?");

        verify(retrievalPort).findHybrid(eq(userId), eq("scoped?"), any(), eq(List.of(scopedDoc)), eq(20));
    }

    @Test
    void askQuestionSearchesOnlyTheConversationsCollection() {
        UUID collectionId = UUID.randomUUID();
        UUID docInCollection = UUID.randomUUID();
        Conversation conversation = conversation("Test conversation", null).toBuilder().collectionId(collectionId).build();
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(collectionRepositoryPort.findByIdAndOwnerId(collectionId, userId))
                .thenReturn(Optional.of(collection(collectionId, "HR policies", Set.of(docInCollection))));
        when(embeddingPort.embed(any())).thenReturn(new float[] {0.2f});
        when(retrievalPort.findHybrid(eq(userId), anyString(), any(), eq(List.of(docInCollection)), anyInt()))
                .thenReturn(List.of(new RetrievedChunk(UUID.randomUUID(), docInCollection, 0, "leave policy", "hr.pdf", 0.9)));
        when(chatPortResolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        when(chatPort.complete(any())).thenReturn(ChatCompletionResult.builder().content("20 days [1]").build());

        Message result = ragQueryService.askQuestion(userId, conversationId, "leave policy");

        assertThat(result.getContent()).isEqualTo("20 days [1]");
        verify(retrievalPort).findHybrid(eq(userId), anyString(), any(), eq(List.of(docInCollection)), anyInt());
    }

    @Test
    void emptyCollectionNeverFallsBackToSearchingEverything() {
        UUID collectionId = UUID.randomUUID();
        Conversation conversation = conversation("Test conversation", null).toBuilder().collectionId(collectionId).build();
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(collectionRepositoryPort.findByIdAndOwnerId(collectionId, userId))
                .thenReturn(Optional.of(collection(collectionId, "Project X", Set.of())));

        Message result = ragQueryService.askQuestion(userId, conversationId, "status?");

        assertThat(result.isUnanswered()).isTrue();
        assertThat(result.getContent()).contains("\"Project X\"").contains("no documents");
        verify(embeddingPort, never()).embed(any());
        verify(retrievalPort, never()).findHybrid(any(), any(), any(), any(), anyInt());
    }

    @Test
    void askQuestionAbstainsWhenRetrievalIsWeak() {
        Conversation conversation = conversation("Test conversation", null);
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(embeddingPort.embed("unrelated trivia")).thenReturn(new float[] {0.1f});
        RetrievedChunk weak = new RetrievedChunk(
                UUID.randomUUID(), UUID.randomUUID(), 0, "zzzz unrelated passage", "notes.md", 0.12, 0.12, false);
        when(retrievalPort.findHybrid(any(), anyString(), any(), anyList(), anyInt())).thenReturn(List.of(weak));

        Message result = ragQueryService.askQuestion(userId, conversationId, "unrelated trivia");

        assertThat(result.isUnanswered()).isTrue();
        assertThat(result.getContent()).contains("couldn't find an answer to this in your documents");
        assertThat(result.getCitations()).isEmpty();
        verify(chatPortResolver, never()).resolve(any());
        verify(chatPort, never()).complete(any());
    }

    @Test
    void weakRetrievalInScopedChatSuggestsWideningTheFilter() {
        Conversation conversation = conversation("Test conversation", List.of(UUID.randomUUID(), UUID.randomUUID()));
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(embeddingPort.embed(any())).thenReturn(new float[] {0.1f});
        when(retrievalPort.findHybrid(any(), anyString(), any(), anyList(), anyInt())).thenReturn(List.of());

        Message result = ragQueryService.askQuestion(userId, conversationId, "anything");

        assertThat(result.isUnanswered()).isTrue();
        assertThat(result.getContent()).contains("2 selected documents").contains("All documents");
    }

    @Test
    void modelSayingNoAnswerIsStoredAsUnansweredWithoutCitations() {
        Conversation conversation = conversation("Test conversation", null);
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(embeddingPort.embed(any())).thenReturn(new float[] {0.1f});
        when(retrievalPort.findHybrid(any(), anyString(), any(), anyList(), anyInt()))
                .thenReturn(List.of(chunk("pricing for 2023", "pricing.md", 0.8)));
        when(chatPortResolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        when(chatPort.complete(any())).thenReturn(ChatCompletionResult.builder().content("NO_ANSWER").build());

        Message result = ragQueryService.askQuestion(userId, conversationId, "pricing for 2025?");

        assertThat(result.isUnanswered()).isTrue();
        assertThat(result.getContent()).doesNotContain("NO_ANSWER").contains("couldn't find an answer");
        assertThat(result.getCitations()).isEmpty();
    }

    @Test
    void streamingHidesTheNoAnswerMarkerAndSendsTheFriendlyMessage() {
        Conversation conversation = conversation("Test conversation", null);
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(embeddingPort.embed(any())).thenReturn(new float[] {0.1f});
        when(retrievalPort.findHybrid(any(), anyString(), any(), anyList(), anyInt()))
                .thenReturn(List.of(chunk("pricing for 2023", "pricing.md", 0.8)));
        when(chatPortResolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        streamTokens(" NO_", "ANS", "WER");

        List<String> received = new ArrayList<>();
        RagQueryService.StreamResult result = ragQueryService.streamQuestion(userId, conversationId, "2025?", received::add);

        assertThat(String.join("", received)).doesNotContain("NO_").contains("couldn't find an answer");
        assertThat(result.assistantMessage().isUnanswered()).isTrue();
    }

    @Test
    void streamingPassesNormalAnswersThrough() {
        Conversation conversation = conversation("Test conversation", null);
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(embeddingPort.embed(any())).thenReturn(new float[] {0.1f});
        when(retrievalPort.findHybrid(any(), anyString(), any(), anyList(), anyInt()))
                .thenReturn(List.of(chunk("MemOS stores chunks", "a.md", 0.9)));
        when(chatPortResolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        // "NO" is a prefix of the marker, so it is held back until "body" proves otherwise.
        streamTokens("NO", "body stores ", "chunks [1]");

        List<String> received = new ArrayList<>();
        RagQueryService.StreamResult result = ragQueryService.streamQuestion(userId, conversationId, "q", received::add);

        assertThat(String.join("", received)).isEqualTo("NObody stores chunks [1]");
        assertThat(result.assistantMessage().getContent()).isEqualTo("NObody stores chunks [1]");
        assertThat(result.assistantMessage().isUnanswered()).isFalse();
        assertThat(result.assistantMessage().getCitations()).hasSize(1);
    }

    @Test
    void followUpIsSearchedWithTheRewrittenQueryAndAnsweredFromTheOriginal() {
        Conversation conversation = conversation("Test conversation", null);
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        Message earlier = Message.builder()
                .conversationId(conversationId).role(MessageRole.USER).content("Tell me about Postgres")
                .createdAt(Instant.now()).build();
        when(messageRepositoryPort.findRecentByConversationId(conversationId, 9)).thenReturn(List.of(earlier));
        when(followUpQueryRewriter.rewrite(eq(conversation), eq(List.of(earlier)), eq("what about its indexes?")))
                .thenReturn("Postgres indexes");
        when(embeddingPort.embed("Postgres indexes")).thenReturn(new float[] {0.3f});
        when(retrievalPort.findHybrid(eq(userId), eq("Postgres indexes"), any(), anyList(), anyInt()))
                .thenReturn(List.of(chunk("Postgres supports btree and gin indexes", "pg.md", 0.9)));
        when(chatPortResolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        when(chatPort.complete(any())).thenReturn(ChatCompletionResult.builder().content("btree and gin [1]").build());

        Message result = ragQueryService.askQuestion(userId, conversationId, "what about its indexes?");

        assertThat(result.getSearchQuery()).isEqualTo("Postgres indexes");
        ArgumentCaptor<ChatCompletionRequest> requestCaptor = ArgumentCaptor.forClass(ChatCompletionRequest.class);
        verify(chatPort).complete(requestCaptor.capture());
        List<ChatMessage> prompt = requestCaptor.getValue().getMessages();
        assertThat(prompt.get(prompt.size() - 1).getContent()).isEqualTo("what about its indexes?");
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
        when(chatPortResolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        when(chatPort.complete(any())).thenReturn(ChatCompletionResult.builder().content("connection reset").build());

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
        Conversation conversation = conversation("Test conversation", null).toBuilder().provider(null).build();
        when(conversationRepositoryPort.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(embeddingPort.embed(any())).thenReturn(new float[] {0.1f});
        when(retrievalPort.findHybrid(any(), anyString(), any(), anyList(), anyInt()))
                .thenReturn(List.of(chunk("hello there", "intro.md", 0.9)));
        when(chatPortResolver.resolve(null)).thenReturn(chatPort);
        when(chatPort.complete(any(ChatCompletionRequest.class))).thenReturn(ChatCompletionResult.builder()
                .content("Hello!")
                .build());

        Message result = ragQueryService.askQuestion(userId, conversationId, "hi");

        assertThat(result.getContent()).isEqualTo("Hello!");
        verify(chatPortResolver).resolve(null);
    }

    @SuppressWarnings("unchecked")
    private void streamTokens(String... tokens) {
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            for (String token : tokens) {
                consumer.accept(token);
            }
            return null;
        }).when(chatPort).streamComplete(any(), any(Consumer.class));
    }

    private static RetrievedChunk chunk(String content, String title, double score) {
        return new RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), 0, content, title, score);
    }

    private Collection collection(UUID id, String name, Set<UUID> documentIds) {
        return Collection.builder()
                .id(id)
                .ownerId(userId)
                .name(name)
                .documentIds(documentIds)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
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
