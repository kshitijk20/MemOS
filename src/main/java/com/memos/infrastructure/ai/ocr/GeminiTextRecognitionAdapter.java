package com.memos.infrastructure.ai.ocr;

import java.util.concurrent.locks.ReentrantLock;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import com.memos.domain.ai.AiProvider;
import com.memos.domain.ai.TextRecognitionPort;
import com.memos.domain.exception.AiProviderException;
import com.memos.domain.exception.ProviderErrorMessages;
import com.memos.infrastructure.ai.config.AiProvidersProperties;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * OCR with Gemini's vision model (through the same OpenAI-compatible endpoint as chat). A vision
 * model reads handwriting and messy phone photos far better than classic OCR engines, and needs
 * no native libraries on the server.
 *
 * <p>Calls are spaced out to stay under the free tier's requests-per-minute limit, since a
 * scanned PDF needs one call per page.
 */
@Slf4j
@Component
public class GeminiTextRecognitionAdapter implements TextRecognitionPort {

    static final String NO_TEXT = "NO_TEXT";

    private static final String PROMPT = """
            Transcribe all the text in this image exactly as written, including handwriting.
            Keep the reading order. Use Markdown for headings, lists and tables when the layout \
            clearly shows them. Do not describe the image, summarize, translate or add anything.
            If the image contains no readable text, reply with exactly NO_TEXT.""";

    private final OpenAiChatModel chatModel;
    private final String model;
    private final long minIntervalMillis;

    /**
     * Guards {@link #lastCallMillis}. A lock rather than {@code synchronized}, so waiting virtual
     * threads don't pin their carrier threads (see GeminiEmbeddingAdapter).
     */
    private final ReentrantLock paceLock = new ReentrantLock(true);
    private long lastCallMillis;

    public GeminiTextRecognitionAdapter(
            AiProvidersProperties properties,
            @Value("${memos.ai.ocr.model:}") String ocrModel,
            @Value("${memos.ai.ocr.requests-per-minute:8}") int requestsPerMinute) {
        AiProvidersProperties.ProviderConfig config = properties.provider(AiProvider.GEMINI);
        this.model = ocrModel == null || ocrModel.isBlank() ? config.getChatModel() : ocrModel;
        this.minIntervalMillis = requestsPerMinute > 0 ? 60_000L / requestsPerMinute : 0;
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();
        this.chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.0).build())
                .toolCallingManager(ToolCallingManager.builder().build())
                .retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    @Override
    public String recognizeText(byte[] image, String mimeType) {
        waitForTurn();
        try {
            UserMessage message = UserMessage.builder()
                    .text(PROMPT)
                    .media(new Media(MimeType.valueOf(mimeType), new ByteArrayResource(image)))
                    .build();
            String text = chatModel.call(new Prompt(message)).getResult().getOutput().getText();
            return clean(text);
        } catch (Exception e) {
            throw new AiProviderException(ProviderErrorMessages.forChat("Gemini", e), e);
        }
    }

    static String clean(String text) {
        if (text == null) {
            return "";
        }
        String stripped = text.strip();
        // Some replies wrap the transcription in a code fence despite the prompt.
        if (stripped.startsWith("```") && stripped.endsWith("```") && stripped.length() > 6) {
            stripped = stripped.substring(stripped.indexOf('\n') + 1, stripped.length() - 3).strip();
        }
        return stripped.equals(NO_TEXT) ? "" : stripped;
    }

    private void waitForTurn() {
        paceLock.lock();
        try {
            long wait = lastCallMillis + minIntervalMillis - System.currentTimeMillis();
            if (wait > 0) {
                Thread.sleep(wait);
            }
            lastCallMillis = System.currentTimeMillis();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Interrupted while waiting to call the OCR provider", e);
        } finally {
            paceLock.unlock();
        }
    }
}
