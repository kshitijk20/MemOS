package com.memos.infrastructure.ai.chat;

import java.util.List;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.stereotype.Component;

import com.memos.domain.ai.AiProvider;
import com.memos.domain.ai.ChatCompletionRequest;
import com.memos.domain.ai.ChatCompletionResult;
import com.memos.domain.ai.ChatPort;
import com.memos.domain.exception.AiProviderException;
import com.memos.infrastructure.ai.config.AiProvidersProperties;

import io.micrometer.observation.ObservationRegistry;

/**
 * {@link ChatPort} for Anthropic Claude. Requires the user's own paid API key
 * ({@code memos.ai.providers.claude.api-key}).
 */
@Component
public class ClaudeChatAdapter implements ChatPort {

    private final AnthropicChatModel chatModel;
    private final AiProvidersProperties.ProviderConfig config;

    public ClaudeChatAdapter(AiProvidersProperties properties) {
        this.config = properties.provider(AiProvider.CLAUDE);

        AnthropicApi api = AnthropicApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();

        AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
                .model(config.getChatModel())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .build();

        this.chatModel = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(defaultOptions)
                .toolCallingManager(ToolCallingManager.builder().build())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.CLAUDE;
    }

    @Override
    public ChatCompletionResult complete(ChatCompletionRequest request) {
        try {
            List<Message> messages = request.getMessages().stream().map(this::toSpringMessage).toList();
            String model = request.getModel() != null ? request.getModel() : config.getChatModel();
            double temperature = request.getTemperature() != null ? request.getTemperature() : config.getTemperature();

            AnthropicChatOptions options = AnthropicChatOptions.builder()
                    .model(model)
                    .temperature(temperature)
                    .maxTokens(config.getMaxTokens())
                    .build();

            ChatResponse response = chatModel.call(new Prompt(messages, options));
            Generation result = response.getResult();
            Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;

            return ChatCompletionResult.builder()
                    .content(result.getOutput().getText())
                    .modelUsed(model)
                    .promptTokens(usage != null ? usage.getPromptTokens() : null)
                    .completionTokens(usage != null ? usage.getCompletionTokens() : null)
                    .build();
        } catch (Exception e) {
            throw new AiProviderException("Claude chat completion failed: " + e.getMessage(), e);
        }
    }

    private Message toSpringMessage(com.memos.domain.ai.ChatMessage message) {
        return switch (message.getRole()) {
            case SYSTEM -> new SystemMessage(message.getContent());
            case USER -> new UserMessage(message.getContent());
            case ASSISTANT -> new AssistantMessage(message.getContent());
        };
    }
}
