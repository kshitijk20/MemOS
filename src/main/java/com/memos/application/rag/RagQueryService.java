package com.memos.application.rag;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

/**
 * Orchestrates the retrieval-augmented generation flow for a single user question: persists the
 * question, retrieves relevant document chunks, builds a grounded prompt (with conversation
 * history), calls the conversation's configured chat provider, and persists the assistant's
 * reply together with citations to the chunks used as context.
 */
@Service
@RequiredArgsConstructor
public class RagQueryService {

    private static final int CITATION_SNIPPET_MAX_CHARS = 500;

    private final ConversationRepositoryPort conversationRepositoryPort;
    private final MessageRepositoryPort messageRepositoryPort;
    private final EmbeddingPort embeddingPort;
    private final RetrievalPort retrievalPort;
    private final ChatPortResolver chatPortResolver;

    @Value("${memos.rag.top-k}")
    private int topK;

    @Value("${memos.rag.history-window}")
    private int historyWindow;

    @Value("${memos.rag.max-context-chars}")
    private int maxContextChars;

    public Message askQuestion(UUID userId, UUID conversationId, String question) {
        Conversation conversation = conversationRepositoryPort.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        messageRepositoryPort.save(Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.USER)
                .content(question)
                .createdAt(Instant.now())
                .build());

        float[] queryEmbedding = embeddingPort.embed(question);
        List<RetrievedChunk> retrievedChunks = retrievalPort.findSimilar(userId, queryEmbedding, topK);

        List<ChatMessage> promptMessages = buildPrompt(conversationId, retrievedChunks);

        ChatPort chatPort = chatPortResolver.resolve(conversation.getProvider());
        ChatCompletionResult result = chatPort.complete(ChatCompletionRequest.builder()
                .messages(promptMessages)
                .model(conversation.getModel())
                .build());

        List<Citation> citations = retrievedChunks.stream()
                .map(chunk -> Citation.builder()
                        .documentId(chunk.documentId())
                        .documentChunkId(chunk.chunkId())
                        .sourceTitle(chunk.sourceTitle())
                        .snippet(truncateSnippet(chunk.content()))
                        .score(chunk.score())
                        .createdAt(Instant.now())
                        .build())
                .toList();

        Message assistantMessage = messageRepositoryPort.save(Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.ASSISTANT)
                .content(result.getContent())
                .citations(citations)
                .createdAt(Instant.now())
                .build());

        conversationRepositoryPort.save(conversation.withTouchedTimestamp());

        return assistantMessage;
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
        prompt.append("You are MemOS, a knowledge assistant. Answer the user's question using only the "
                + "context sources below. If the context does not contain the answer, say you don't know "
                + "rather than guessing. Refer to sources by their [number] when relevant.\n\n");

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

    private String truncateSnippet(String content) {
        if (content.length() <= CITATION_SNIPPET_MAX_CHARS) {
            return content;
        }
        return content.substring(0, CITATION_SNIPPET_MAX_CHARS) + "...";
    }
}
