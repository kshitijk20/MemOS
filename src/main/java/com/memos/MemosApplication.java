package com.memos;

import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring AI's OpenAI/Anthropic autoconfiguration is excluded because the AI provider
 * adapters in {@code infrastructure.ai} build their own {@code ChatModel}/{@code
 * EmbeddingModel} instances per provider (including Gemini, via Google's OpenAI-compatible
 * endpoint) from {@code memos.ai.providers.*} - see {@code AiProvidersProperties}.
 */
@SpringBootApplication(exclude = {
        OpenAiChatAutoConfiguration.class,
        OpenAiEmbeddingAutoConfiguration.class,
        OpenAiImageAutoConfiguration.class,
        OpenAiAudioSpeechAutoConfiguration.class,
        OpenAiAudioTranscriptionAutoConfiguration.class,
        OpenAiModerationAutoConfiguration.class,
        AnthropicChatAutoConfiguration.class
})
@EnableAsync
@EnableCaching
@EnableScheduling
@ConfigurationPropertiesScan
public class MemosApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemosApplication.class, args);
    }
}
