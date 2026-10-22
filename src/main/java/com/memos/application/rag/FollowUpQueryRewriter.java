package com.memos.application.rag;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.memos.domain.ai.ChatCompletionRequest;
import com.memos.domain.ai.ChatMessage;
import com.memos.domain.conversation.Conversation;
import com.memos.domain.conversation.Message;
import com.memos.domain.conversation.MessageRole;
import com.memos.infrastructure.ai.chat.ChatPortResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a follow-up question ("and what about the second one?") into a standalone search query
 * using the conversation so far, so retrieval looks for what the user actually means. Only the
 * search uses the rewrite; the answer is still generated from the original question and history.
 *
 * <p>Costs one extra LLM call, so it runs only when there is earlier history and the question
 * looks like it depends on it. Any failure falls back to the original question.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpQueryRewriter {

    static final int MAX_QUERY_LENGTH = 300;
    private static final int HISTORY_MESSAGES = 6;
    private static final int HISTORY_MESSAGE_MAX_CHARS = 600;
    private static final int SHORT_QUESTION_WORDS = 6;

    /** Words that usually point back at something said earlier in the conversation. */
    private static final Pattern FOLLOW_UP_MARKERS = Pattern.compile(
            "\\b(it|its|it's|this|that|these|those|they|them|their|he|she|him|her|his|hers|one|ones|"
                    + "former|latter|above|previous|same|also|too|else|more|other|another|"
                    + "first|second|third|last|again|instead)\\b"
                    + "|^\\s*(and|but|so|or|what about|how about|why|then)\\b",
            Pattern.CASE_INSENSITIVE);

    private final ChatPortResolver chatPortResolver;

    @Value("${memos.rag.query-rewrite.enabled:true}")
    private boolean enabled = true;

    /**
     * @param priorHistory messages before the current question, oldest first
     * @return the query to search with; the original question when no rewrite is needed or possible
     */
    public String rewrite(Conversation conversation, List<Message> priorHistory, String question) {
        if (!enabled || priorHistory == null || priorHistory.isEmpty() || !looksLikeFollowUp(question)) {
            return question;
        }
        try {
            String rewritten = chatPortResolver.resolve(conversation.getProvider())
                    .complete(ChatCompletionRequest.builder()
                            .messages(List.of(ChatMessage.user(buildPrompt(priorHistory, question))))
                            .model(conversation.getModel())
                            .build())
                    .getContent();
            return sanitize(rewritten, question);
        } catch (Exception e) {
            log.warn("Follow-up rewrite failed for conversation {}, searching with the original question: {}",
                    conversation.getId(), e.getMessage());
            return question;
        }
    }

    static boolean looksLikeFollowUp(String question) {
        String trimmed = question == null ? "" : question.strip();
        if (trimmed.isEmpty()) {
            return false;
        }
        return trimmed.split("\\s+").length <= SHORT_QUESTION_WORDS || FOLLOW_UP_MARKERS.matcher(trimmed).find();
    }

    private static String buildPrompt(List<Message> priorHistory, String question) {
        StringBuilder prompt = new StringBuilder("""
                Rewrite the user's latest question as a single standalone search query that can be \
                understood without the conversation. Replace pronouns and references like "it", \
                "the second one" or "what about X" with what they refer to. Keep names, codes and \
                technical terms exactly as written. If the question is already standalone, return it \
                unchanged. Reply with the query only: no quotes, no explanation.

                Conversation:
                """);
        List<Message> recent = priorHistory.subList(Math.max(0, priorHistory.size() - HISTORY_MESSAGES), priorHistory.size());
        for (Message message : recent) {
            if (message.getRole() == MessageRole.SYSTEM) {
                continue;
            }
            String content = message.getContent();
            if (content.length() > HISTORY_MESSAGE_MAX_CHARS) {
                content = content.substring(0, HISTORY_MESSAGE_MAX_CHARS) + "…";
            }
            prompt.append(message.getRole() == MessageRole.USER ? "User: " : "Assistant: ")
                    .append(content)
                    .append('\n');
        }
        prompt.append("\nLatest question: ").append(question).append("\nStandalone query:");
        return prompt.toString();
    }

    static String sanitize(String rewritten, String fallback) {
        if (rewritten == null) {
            return fallback;
        }
        String query = rewritten.strip();
        int newline = query.indexOf('\n');
        if (newline >= 0) {
            query = query.substring(0, newline).strip();
        }
        query = query.replaceFirst("(?i)^standalone query:\\s*", "");
        if (query.length() >= 2 && (query.startsWith("\"") && query.endsWith("\"")
                || query.startsWith("'") && query.endsWith("'"))) {
            query = query.substring(1, query.length() - 1).strip();
        }
        if (query.isEmpty() || query.length() > MAX_QUERY_LENGTH) {
            return fallback;
        }
        return query;
    }
}
