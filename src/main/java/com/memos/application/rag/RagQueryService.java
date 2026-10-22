package com.memos.application.rag;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.memos.domain.ai.ChatCompletionRequest;
import com.memos.domain.ai.ChatCompletionResult;
import com.memos.domain.ai.ChatMessage;
import com.memos.domain.ai.ChatPort;
import com.memos.domain.ai.EmbeddingPort;
import com.memos.domain.conversation.Citation;
import com.memos.domain.conversation.Conversation;
import com.memos.domain.conversation.ConversationRepositoryPort;
import com.memos.domain.conversation.Message;
import com.memos.domain.conversation.MessageRepositoryPort;
import com.memos.domain.conversation.MessageRole;
import com.memos.domain.exception.ResourceNotFoundException;
import com.memos.infrastructure.ai.chat.ChatPortResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the retrieval-augmented generation flow for a single user question: persists the
 * question, retrieves relevant document chunks, builds a grounded prompt (with conversation
 * history), calls the conversation's configured chat provider, and persists the assistant's
 * reply together with citations to the chunks used as context.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagQueryService {

    private static final int CITATION_SNIPPET_MAX_CHARS = 500;
    private static final String DEFAULT_TITLE = "New conversation";
    private static final int GENERATED_TITLE_MAX_LENGTH = 100;
    static final String UNSURE_ANSWER =
            "I don't know based on your documents. Nothing retrieved was similar enough to answer confidently.";

    public record StreamResult(Message assistantMessage, String generatedTitle) {}

    private record PersistedReply(Message message, String generatedTitle) {}

    private final ConversationRepositoryPort conversationRepositoryPort;
    private final MessageRepositoryPort messageRepositoryPort;
    private final EmbeddingPort embeddingPort;
    private final RetrievalPort retrievalPort;
    private final RerankPort rerankPort;
    private final ChatPortResolver chatPortResolver;

    @Value("${memos.rag.top-k}")
    private int topK;

    @Value("${memos.rag.history-window}")
    private int historyWindow;

    @Value("${memos.rag.max-context-chars}")
    private int maxContextChars;

    @Value("${memos.rag.candidate-pool:20}")
    private int candidatePool;

    @Value("${memos.rag.min-score:0.35}")
    private double minScore;

    public Message askQuestion(UUID userId, UUID conversationId, String question) {
        Conversation conversation = conversationRepositoryPort.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        persistUserQuestion(conversationId, question);

        float[] queryEmbedding = embeddingPort.embed(question);
        List<RetrievedChunk> retrievedChunks = retrieve(userId, question, queryEmbedding, conversation);

        if (shouldAbstain(retrievedChunks)) {
            return persistAssistant(conversation, question, UNSURE_ANSWER, List.of()).message();
        }

        List<ChatMessage> promptMessages = buildPrompt(conversationId, retrievedChunks);

        ChatPort chatPort = chatPortResolver.resolve(conversation.getProvider());
        ChatCompletionResult result = chatPort.complete(ChatCompletionRequest.builder()
                .messages(promptMessages)
                .model(conversation.getModel())
                .build());

        return persistAssistant(conversation, question, result.getContent(), toCitations(retrievedChunks)).message();
    }

    public StreamResult streamQuestion(UUID userId, UUID conversationId, String question, Consumer<String> tokenConsumer) {
        Conversation conversation = conversationRepositoryPort.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        persistUserQuestion(conversationId, question);

        float[] queryEmbedding = embeddingPort.embed(question);
        List<RetrievedChunk> retrievedChunks = retrieve(userId, question, queryEmbedding, conversation);

        if (shouldAbstain(retrievedChunks)) {
            tokenConsumer.accept(UNSURE_ANSWER);
            PersistedReply persisted = persistAssistant(conversation, question, UNSURE_ANSWER, List.of());
            return new StreamResult(persisted.message(), persisted.generatedTitle());
        }

        List<ChatMessage> promptMessages = buildPrompt(conversationId, retrievedChunks);

        ChatPort chatPort = chatPortResolver.resolve(conversation.getProvider());
        StringBuilder fullContent = new StringBuilder();
        chatPort.streamComplete(
                ChatCompletionRequest.builder()
                        .messages(promptMessages)
                        .model(conversation.getModel())
                        .build(),
                token -> {
                    fullContent.append(token);
                    tokenConsumer.accept(token);
                });

        PersistedReply persisted = persistAssistant(
                conversation, question, fullContent.toString(), toCitations(retrievedChunks));
        return new StreamResult(persisted.message(), persisted.generatedTitle());
    }

    private void persistUserQuestion(UUID conversationId, String question) {
        messageRepositoryPort.save(Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.USER)
                .content(question)
                .createdAt(Instant.now())
                .build());
    }

    private PersistedReply persistAssistant(
            Conversation conversation,
            String question,
            String content,
            List<Citation> citations) {
        Message assistantMessage = messageRepositoryPort.save(Message.builder()
                .conversationId(conversation.getId())
                .role(MessageRole.ASSISTANT)
                .content(content)
                .citations(citations)
                .createdAt(Instant.now())
                .build());

        conversationRepositoryPort.save(conversation.withTouchedTimestamp());
        String generatedTitle = generateTitleIfNeeded(conversation, question);
        return new PersistedReply(assistantMessage, generatedTitle);
    }

    private List<Citation> toCitations(List<RetrievedChunk> retrievedChunks) {
        return retrievedChunks.stream()
                .map(chunk -> Citation.builder()
                        .documentId(chunk.documentId())
                        .documentChunkId(chunk.chunkId())
                        .sourceTitle(chunk.sourceTitle())
                        .snippet(truncateSnippet(chunk.content()))
                        .score(chunk.vectorScore() > 0 ? chunk.vectorScore() : chunk.score())
                        .createdAt(Instant.now())
                        .build())
                .toList();
    }

    private List<RetrievedChunk> retrieve(
            UUID userId, String question, float[] queryEmbedding, Conversation conversation) {
        List<UUID> scope = conversation.getDocumentScope() == null ? List.of() : conversation.getDocumentScope();
        int candidateLimit = Math.max(topK * 4, candidatePool);
        List<RetrievedChunk> candidates =
                retrievalPort.findHybrid(userId, question, queryEmbedding, scope, candidateLimit);
        return rerankPort.rerank(question, candidates, topK);
    }

    private boolean shouldAbstain(List<RetrievedChunk> retrievedChunks) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return true;
        }
        boolean lexicalHit = retrievedChunks.stream().anyMatch(RetrievedChunk::lexicalHit);
        if (lexicalHit) {
            return false;
        }
        double bestVector = retrievedChunks.stream()
                .mapToDouble(RetrievedChunk::vectorScore)
                .max()
                .orElse(0);
        return bestVector < minScore;
    }

    /**
     * History is fetched after the user's question has been persisted, so the most recent
     * entry in {@code history} is the current question itself - no need to append it again.
     */
    private List<ChatMessage> buildPrompt(UUID conversationId, List<RetrievedChunk> retrievedChunks) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt(retrievedChunks)));

        List<Message> history = messageRepositoryPort.findRecentByConversationId(conversationId, historyWindow);
        for (Message message : history) {
            messages.add(switch (message.getRole()) {
                case USER -> ChatMessage.user(message.getContent());
                case ASSISTANT -> ChatMessage.assistant(message.getContent());
                case SYSTEM -> ChatMessage.system(message.getContent());
            });
        }

        return messages;
    }

    private String buildSystemPrompt(List<RetrievedChunk> retrievedChunks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are MemOS, a knowledge assistant. Answer only from the context sources below. "
                + "If the sources do not contain the answer, say you don't know. Do not guess or use general knowledge. "
                + "Refer to sources by their [number] when relevant.\n\n");

        int remaining = maxContextChars;
        for (int i = 0; i < retrievedChunks.size(); i++) {
            RetrievedChunk chunk = retrievedChunks.get(i);
            String entry = "[" + (i + 1) + "] Source: " + chunk.sourceTitle() + "\n" + chunk.content() + "\n\n";
            if (entry.length() > remaining) {
                break;
            }
            prompt.append(entry);
            remaining -= entry.length();
        }

        return prompt.toString();
    }

    private String generateTitleIfNeeded(Conversation conversation, String userQuestion) {
        if (!DEFAULT_TITLE.equals(conversation.getTitle())) {
            return null;
        }
        long messageCount = messageRepositoryPort.countByConversationId(conversation.getId());
        if (messageCount != 2) {
            return null;
        }

        try {
            ChatPort chatPort = chatPortResolver.resolve(conversation.getProvider());
            ChatCompletionResult result = chatPort.complete(ChatCompletionRequest.builder()
                    .messages(List.of(ChatMessage.user(
                            "Write a concise title (5 words max, no quotes or punctuation) "
                                    + "for a conversation that starts with this question: " + userQuestion)))
                    .build());
            String title = result.getContent().trim().replaceAll("[\"'.,!?]", "");
            if (title.length() > GENERATED_TITLE_MAX_LENGTH) {
                title = title.substring(0, GENERATED_TITLE_MAX_LENGTH);
            }
            conversationRepositoryPort.save(conversation.toBuilder()
                    .title(title)
                    .updatedAt(Instant.now())
                    .build());
            return title;
        } catch (Exception e) {
            log.warn("Auto-title generation failed for conversation {}: {}", conversation.getId(), e.getMessage());
            return null;
        }
    }

    private String truncateSnippet(String content) {
        if (content.length() <= CITATION_SNIPPET_MAX_CHARS) {
            return content;
        }
        return content.substring(0, CITATION_SNIPPET_MAX_CHARS) + "...";
    }
}
