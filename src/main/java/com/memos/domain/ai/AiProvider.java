package com.memos.domain.ai;

/**
 * Supported AI providers for chat completion. New providers can be added here and by
 * registering a new {@link ChatPort} adapter bean in infrastructure/ai/chat.
 */
public enum AiProvider {
    GEMINI,
    OPENAI,
    CLAUDE
}
