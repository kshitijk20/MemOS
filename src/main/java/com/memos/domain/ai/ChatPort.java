package com.memos.domain.ai;

/**
 * Port for chat-completion AI providers. Each implementation wraps a single provider
 * (Gemini, OpenAI, Claude) and is resolved at runtime via the provider's {@link AiProvider}.
 */
public interface ChatPort {

    AiProvider provider();

    ChatCompletionResult complete(ChatCompletionRequest request);
}
