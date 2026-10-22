package com.memos.application.rag;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.memos.domain.ai.ChatCompletionRequest;
import com.memos.domain.ai.ChatCompletionResult;
import com.memos.domain.ai.ChatMessage;
import com.memos.domain.ai.ChatPort;
import com.memos.domain.ai.EmbeddingPort;
import com.memos.domain.collection.Collection;
import com.memos.domain.collection.CollectionRepositoryPort;
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
 * question, rewrites follow-ups into a standalone search query, retrieves relevant document chunks
 * within the conversation's scope (all documents, a collection, or selected documents), builds a
 * grounded prompt (with conversation history), calls the conversation's configured chat provider,
 * and persists the assistant's reply together with citations to the sources it referenced.
 *
 * <p>When nothing relevant is retrieved, or the model reports that the sources don't answer the
 * question, the reply is a scope-aware "not found in your documents" message flagged as
 * unanswered, instead of a guess.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagQueryService {

    private static final int CITATION_SNIPPET_MAX_CHARS = 500;
    private static final String DEFAULT_TITLE = "New conversation";
    private static final int GENERATED_TITLE_MAX_LENGTH = 100;

    public record StreamResult(Message assistantMessage, String generatedTitle) {}

    private record PersistedReply(Message message, String generatedTitle) {}

    /** Documents a question may search. {@code documentIds} empty with {@code all} false matches nothing. */
    private record Scope(boolean all, List<UUID> documentIds, String collectionName) {

        static Scope everything() {
            return new Scope(true, List.of(), null);
        }

        boolean isEmpty() {
            return !all && documentIds.isEmpty();
        }
    }

    /** Everything prepared before calling the model; {@code unsureAnswer} non-null means don't call it. */
    private record Turn(
            String searchQuery,
            List<RetrievedChunk> sources,
            List<ChatMessage> prompt,
            Scope scope,
            String unsureAnswer) {
    }

    private final ConversationRepositoryPort conversationRepositoryPort;
    private final MessageRepositoryPort messageRepositoryPort;
    private final CollectionRepositoryPort collectionRepositoryPort;
    private final EmbeddingPort embeddingPort;
    private final RetrievalPort retrievalPort;
    private final RerankPort rerankPort;
    private final ChatPortResolver chatPortResolver;
    private final FollowUpQueryRewriter followUpQueryRewriter;

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

        Turn turn = prepare(userId, conversation, question);
        if (turn.unsureAnswer() != null) {
            return persistUnanswered(conversation, question, turn, turn.unsureAnswer()).message();
        }

        ChatPort chatPort = chatPortResolver.resolve(conversation.getProvider());
        ChatCompletionResult result = chatPort.complete(ChatCompletionRequest.builder()
                .messages(turn.prompt())
                .model(conversation.getModel())
                .build());

        if (NoAnswerDetector.isNoAnswer(result.getContent())) {
            return persistUnanswered(conversation, question, turn, unsureAnswer(turn.scope())).message();
        }
        return persistAnswer(conversation, question, turn, result.getContent()).message();
    }

    public StreamResult streamQuestion(UUID userId, UUID conversationId, String question, Consumer<String> tokenConsumer) {
        Conversation conversation = conversationRepositoryPort.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        Turn turn = prepare(userId, conversation, question);
        if (turn.unsureAnswer() != null) {
            tokenConsumer.accept(turn.unsureAnswer());
            PersistedReply persisted = persistUnanswered(conversation, question, turn, turn.unsureAnswer());
            return new StreamResult(persisted.message(), persisted.generatedTitle());
        }

        ChatPort chatPort = chatPortResolver.resolve(conversation.getProvider());
        NoAnswerDetector detector = new NoAnswerDetector(tokenConsumer);
        chatPort.streamComplete(
                ChatCompletionRequest.builder()
                        .messages(turn.prompt())
                        .model(conversation.getModel())
                        .build(),
                detector);
        detector.finish();

        PersistedReply persisted;
        if (detector.noAnswer()) {
            String unsure = unsureAnswer(turn.scope());
            tokenConsumer.accept(unsure);
            persisted = persistUnanswered(conversation, question, turn, unsure);
        } else {
            persisted = persistAnswer(conversation, question, turn, detector.fullReply());
        }
        return new StreamResult(persisted.message(), persisted.generatedTitle());
    }

    private Turn prepare(UUID userId, Conversation conversation, String question) {
        // Read before saving the question, so the rewriter sees only what came before it.
        List<Message> priorHistory = historyWindow > 1
                ? messageRepositoryPort.findRecentByConversationId(conversation.getId(), historyWindow - 1)
                : List.of();
        persistUserQuestion(conversation.getId(), question);

        String searchQuery = followUpQueryRewriter.rewrite(conversation, priorHistory, question);
        Scope scope = resolveScope(userId, conversation);
        if (scope.isEmpty()) {
            return new Turn(searchQuery, List.of(), List.of(), scope, emptyScopeAnswer(scope));
        }

        float[] queryEmbedding = embeddingPort.embed(searchQuery);
        List<RetrievedChunk> retrieved = retrieve(userId, searchQuery, queryEmbedding, scope);
        if (shouldAbstain(retrieved)) {
            return new Turn(searchQuery, List.of(), List.of(), scope, unsureAnswer(scope));
        }

        List<RetrievedChunk> sources = fitToContext(retrieved);
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(ChatMessage.system(buildSystemPrompt(sources)));
        for (Message message : priorHistory) {
            prompt.add(switch (message.getRole()) {
                case USER -> ChatMessage.user(message.getContent());
                case ASSISTANT -> ChatMessage.assistant(message.getContent());
                case SYSTEM -> ChatMessage.system(message.getContent());
            });
        }
        prompt.add(ChatMessage.user(question));
        return new Turn(searchQuery, sources, prompt, scope, null);
    }

    private Scope resolveScope(UUID userId, Conversation conversation) {
        if (conversation.getCollectionId() != null) {
            Collection collection = collectionRepositoryPort
                    .findByIdAndOwnerId(conversation.getCollectionId(), userId)
                    .orElse(null);
            if (collection != null) {
                List<UUID> ids = collection.getDocumentIds() == null ? List.of() : List.copyOf(collection.getDocumentIds());
                return new Scope(false, ids, collection.getName());
            }
            // Deleted since the conversation was scoped to it: the FK clears it, so search everything.
            return Scope.everything();
        }
        List<UUID> documentScope = conversation.getDocumentScope() == null ? List.of() : conversation.getDocumentScope();
        return documentScope.isEmpty() ? Scope.everything() : new Scope(false, documentScope, null);
    }

    private List<RetrievedChunk> retrieve(UUID userId, String query, float[] queryEmbedding, Scope scope) {
        int candidateLimit = Math.max(topK * 4, candidatePool);
        List<RetrievedChunk> candidates =
                retrievalPort.findHybrid(userId, query, queryEmbedding, scope.documentIds(), candidateLimit);
        return rerankPort.rerank(query, candidates, topK);
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

    /** The retrieved chunks that fit in the context budget, in rank order; their position + 1 is their [n]. */
    private List<RetrievedChunk> fitToContext(List<RetrievedChunk> retrievedChunks) {
        List<RetrievedChunk> included = new ArrayList<>();
        int remaining = maxContextChars;
        for (RetrievedChunk chunk : retrievedChunks) {
            int length = sourceEntry(included.size() + 1, chunk).length();
            if (length > remaining) {
                break;
            }
            included.add(chunk);
            remaining -= length;
        }
        return included;
    }

    private String buildSystemPrompt(List<RetrievedChunk> sources) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are MemOS, a knowledge assistant. Answer only from the numbered sources below. "
                + "Cite the sources you use by their number in square brackets, like [1] or [2][3], right after "
                + "the statement they support. If the sources only partly answer the question, answer that part "
                + "and say what is missing. If they do not contain the answer at all, reply with exactly "
                + NoAnswerDetector.MARKER + " and nothing else. Do not guess or use general knowledge.\n\n");
        for (int i = 0; i < sources.size(); i++) {
            prompt.append(sourceEntry(i + 1, sources.get(i)));
        }
        return prompt.toString();
    }

    private static String sourceEntry(int number, RetrievedChunk chunk) {
        return "[" + number + "] Source: " + chunk.sourceTitle() + "\n" + chunk.content() + "\n\n";
    }

    /** Keeps only the sources the answer cites; if it cites none, all sources it was given. */
    private List<Citation> toCitations(List<RetrievedChunk> sources, String answer) {
        Set<Integer> referenced = SourceReferences.referenced(answer, sources.size());
        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            int number = i + 1;
            if (!referenced.isEmpty() && !referenced.contains(number)) {
                continue;
            }
            RetrievedChunk chunk = sources.get(i);
            citations.add(Citation.builder()
                    .documentId(chunk.documentId())
                    .documentChunkId(chunk.chunkId())
                    .sourceTitle(chunk.sourceTitle())
                    .snippet(truncateSnippet(chunk.content()))
                    .score(chunk.vectorScore() > 0 ? chunk.vectorScore() : chunk.score())
                    .sourceNumber(number)
                    .createdAt(Instant.now())
                    .build());
        }
        return citations;
    }

    private static String unsureAnswer(Scope scope) {
        if (scope.collectionName() != null) {
            return "I couldn't find an answer to this in the \"" + scope.collectionName()
                    + "\" collection this chat is limited to. Try rephrasing the question, "
                    + "or widen the filter to All documents.";
        }
        if (!scope.all()) {
            int count = scope.documentIds().size();
            return "I couldn't find an answer to this in the " + count + " selected document"
                    + (count == 1 ? "" : "s") + " this chat is limited to. Try rephrasing the question, "
                    + "or widen the filter to All documents.";
        }
        return "I couldn't find an answer to this in your documents. Try rephrasing the question, "
                + "or upload a document that covers it.";
    }

    private static String emptyScopeAnswer(Scope scope) {
        return "The \"" + scope.collectionName() + "\" collection this chat is limited to has no documents yet. "
                + "Add documents to it on the Documents page, or widen the filter to All documents.";
    }

    private void persistUserQuestion(UUID conversationId, String question) {
        messageRepositoryPort.save(Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.USER)
                .content(question)
                .createdAt(Instant.now())
                .build());
    }

    private PersistedReply persistAnswer(Conversation conversation, String question, Turn turn, String content) {
        return persistAssistant(conversation, question, turn, content, toCitations(turn.sources(), content), false);
    }

    private PersistedReply persistUnanswered(Conversation conversation, String question, Turn turn, String content) {
        return persistAssistant(conversation, question, turn, content, List.of(), true);
    }

    private PersistedReply persistAssistant(
            Conversation conversation,
            String question,
            Turn turn,
            String content,
            List<Citation> citations,
            boolean unanswered) {
        String searchQuery = turn.searchQuery().equalsIgnoreCase(question.strip()) ? null : turn.searchQuery();
        Message assistantMessage = messageRepositoryPort.save(Message.builder()
                .conversationId(conversation.getId())
                .role(MessageRole.ASSISTANT)
                .content(content)
                .citations(citations)
                .unanswered(unanswered)
                .searchQuery(searchQuery)
                .createdAt(Instant.now())
                .build());

        conversationRepositoryPort.save(conversation.withTouchedTimestamp());
        String generatedTitle = generateTitleIfNeeded(conversation, question);
        return new PersistedReply(assistantMessage, generatedTitle);
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
