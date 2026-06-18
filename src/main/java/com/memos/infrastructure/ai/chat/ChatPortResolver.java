package com.memos.infrastructure.ai.chat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.memos.domain.ai.AiProvider;
import com.memos.domain.ai.ChatPort;
import com.memos.domain.exception.AiProviderException;
import com.memos.infrastructure.ai.config.AiProvidersProperties;

/**
 * Resolves the {@link ChatPort} adapter to use for a conversation, based on the
 * conversation's configured {@link AiProvider} (falling back to {@code
 * memos.ai.default-provider} when unspecified).
 */
@Component
public class ChatPortResolver {

    private final Map<AiProvider, ChatPort> adaptersByProvider;
    private final AiProvider defaultProvider;

    public ChatPortResolver(List<ChatPort> chatPorts, AiProvidersProperties properties) {
        this.adaptersByProvider = chatPorts.stream()
                .collect(Collectors.toMap(ChatPort::provider, Function.identity()));
        this.defaultProvider = properties.getDefaultProvider();
    }

    public ChatPort resolve(AiProvider provider) {
        AiProvider target = provider != null ? provider : defaultProvider;
        ChatPort port = adaptersByProvider.get(target);
        if (port == null) {
            throw new AiProviderException("No chat adapter configured for provider: " + target);
        }
        return port;
    }
}
