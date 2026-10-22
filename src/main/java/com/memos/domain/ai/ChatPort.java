package com.memos.domain.ai;

import java.util.function.Consumer;

/**
 * Port for chat-completion AI providers. Each implementation wraps a single provider
 * (Gemini, OpenAI, Claude) and is resolved at runtime via the provider's {@link AiProvider}.
 */
public interface ChatPort {

    AiProvider provider();

    ChatCompletionResult complete(ChatCompletionRequest request);

    /**
     * Streams the completion token by token, calling {@code tokenConsumer} for each non-empty
     * chunk. Blocks until the stream is fully consumed. Throws {@link
     * com.memos.domain.exception.AiProviderException} on provider errors.
     */
    void streamComplete(ChatCompletionRequest request, Consumer<String> tokenConsumer);
}
