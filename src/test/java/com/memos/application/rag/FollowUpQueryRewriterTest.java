package com.memos.application.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.memos.domain.ai.AiProvider;
import com.memos.domain.ai.ChatCompletionResult;
import com.memos.domain.ai.ChatPort;
import com.memos.domain.conversation.Conversation;
import com.memos.domain.conversation.Message;
import com.memos.domain.conversation.MessageRole;
import com.memos.domain.exception.AiProviderException;
import com.memos.infrastructure.ai.chat.ChatPortResolver;

class FollowUpQueryRewriterTest {

    private final ChatPortResolver resolver = mock(ChatPortResolver.class);
    private final ChatPort chatPort = mock(ChatPort.class);
    private final FollowUpQueryRewriter rewriter = new FollowUpQueryRewriter(resolver);
    private final Conversation conversation = Conversation.builder()
            .id(UUID.randomUUID()).provider(AiProvider.GEMINI).build();
    private final List<Message> history = List.of(
            message(MessageRole.USER, "How does MemOS chunk PDFs?"),
            message(MessageRole.ASSISTANT, "It splits them by paragraph [1]."));

    @Test
    void rewritesFollowUpsUsingHistory() {
        when(resolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        when(chatPort.complete(any())).thenReturn(result("\"How does MemOS chunk Markdown files?\"\n"));

        assertThat(rewriter.rewrite(conversation, history, "and markdown?"))
                .isEqualTo("How does MemOS chunk Markdown files?");
    }

    @Test
    void skipsTheModelWithoutHistory() {
        assertThat(rewriter.rewrite(conversation, List.of(), "and markdown?")).isEqualTo("and markdown?");
        verify(resolver, never()).resolve(any());
    }

    @Test
    void skipsTheModelForLongSelfContainedQuestions() {
        String question = "How does the ingestion pipeline handle Git repositories with large binary files";
        assertThat(rewriter.rewrite(conversation, history, question)).isEqualTo(question);
        verify(resolver, never()).resolve(any());
    }

    @Test
    void fallsBackToTheOriginalQuestionOnFailure() {
        when(resolver.resolve(AiProvider.GEMINI)).thenReturn(chatPort);
        when(chatPort.complete(any())).thenThrow(new AiProviderException("429"));

        assertThat(rewriter.rewrite(conversation, history, "what about it?")).isEqualTo("what about it?");
    }

    @Test
    void detectsFollowUpMarkers() {
        assertThat(FollowUpQueryRewriter.looksLikeFollowUp("what about the second one?")).isTrue();
        assertThat(FollowUpQueryRewriter.looksLikeFollowUp("Why is that slower than the cache layer in production?")).isTrue();
        assertThat(FollowUpQueryRewriter.looksLikeFollowUp(
                "Which embedding model does the ingestion service use for PDF documents")).isFalse();
    }

    @Test
    void sanitizeRejectsEmptyOrRunawayOutput() {
        assertThat(FollowUpQueryRewriter.sanitize("   ", "orig")).isEqualTo("orig");
        assertThat(FollowUpQueryRewriter.sanitize("x".repeat(FollowUpQueryRewriter.MAX_QUERY_LENGTH + 1), "orig"))
                .isEqualTo("orig");
        assertThat(FollowUpQueryRewriter.sanitize("Standalone query: pgvector HNSW", "orig")).isEqualTo("pgvector HNSW");
    }

    private static ChatCompletionResult result(String content) {
        return ChatCompletionResult.builder().content(content).build();
    }

    private static Message message(MessageRole role, String content) {
        return Message.builder().role(role).content(content).createdAt(Instant.now()).build();
    }
}
