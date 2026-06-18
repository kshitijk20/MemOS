package com.memos.infrastructure.ai.chat;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.stereotype.Component;

import com.memos.domain.ai.AiProvider;
import com.memos.domain.ai.ChatCompletionRequest;
import com.memos.domain.ai.ChatCompletionResult;
import com.memos.domain.ai.ChatPort;
import com.memos.domain.exception.AiProviderException;
import com.memos.infrastructure.ai.config.AiProvidersProperties;

import io.micrometer.observation.ObservationRegistry;

/**
 * {@link ChatPort} for OpenAI. Requires the user's own paid API key
 * ({@code memos.ai.providers.openai.api-key}).
 */
@Component
public class OpenAiChatAdapter implements ChatPort {

    private final OpenAiChatModel chatModel;
    private final AiProvidersProperties.ProviderConfig config;

    public OpenAiChatAdapter(AiProvidersProperties properties) {
        this.config = properties.provider(AiProvider.OPENAI);

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();

        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .model(config.getChatModel())
                .temperature(config.getTemperature())
                .build();

        this.chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(defaultOptions)
                .toolCallingManager(ToolCallingManager.builder().build())
                .retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.OPENAI;
    }

    @Override
    public ChatCompletionResult complete(ChatCompletionRequest request) {
        try {
            List<Message> messages = request.getMessages().stream().map(this::toSpringMessage).toList();
            String model = request.getModel() != null ? request.getModel() : config.getChatModel();
            double temperature = request.getTemperature() != null ? request.getTemperature() : config.getTemperature();

            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(model)
                    .temperature(temperature)
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
            throw new AiProviderException("OpenAI chat completion failed: " + e.getMessage(), e);
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
