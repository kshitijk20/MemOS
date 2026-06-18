package com.memos.infrastructure.ingestion.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.memos.application.ingestion.ChunkingPort;
import com.memos.application.ingestion.TextChunk;

/**
 * Paragraph-aware recursive chunking: greedily groups consecutive paragraphs up to {@code
 * memos.ingestion.chunk-size-tokens}, carrying the trailing {@code
 * memos.ingestion.chunk-overlap-tokens} of each chunk into the next for continuity. Paragraphs
 * larger than the chunk size are hard-split.
 *
 * <p>Token counts are estimated as {@code ceil(characters / 4)}, a common rule-of-thumb for
 * English text that avoids pulling in a model-specific tokenizer.
 */
@Component
public class SemanticChunkingAdapter implements ChunkingPort {

    private static final int CHARS_PER_TOKEN = 4;

    private final int chunkSizeTokens;
    private final int overlapTokens;

    public SemanticChunkingAdapter(
            @Value("${memos.ingestion.chunk-size-tokens}") int chunkSizeTokens,
            @Value("${memos.ingestion.chunk-overlap-tokens}") int overlapTokens) {
        this.chunkSizeTokens = chunkSizeTokens;
        this.overlapTokens = overlapTokens;
    }

    @Override
    public List<TextChunk> chunk(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> chunkTexts = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : splitIntoParagraphs(content)) {
            int paragraphTokens = estimateTokens(paragraph);

            if (paragraphTokens > chunkSizeTokens) {
                if (!current.isEmpty()) {
                    chunkTexts.add(current.toString().trim());
                    current.setLength(0);
                }
                chunkTexts.addAll(splitOversizedParagraph(paragraph));
                continue;
            }

            if (!current.isEmpty() && estimateTokens(current.toString()) + paragraphTokens > chunkSizeTokens) {
                chunkTexts.add(current.toString().trim());
                String overlap = takeTrailingTokens(current.toString(), overlapTokens);
                current.setLength(0);
                current.append(overlap);
                if (!overlap.isEmpty()) {
                    current.append("\n\n");
                }
            }

            current.append(paragraph).append("\n\n");
        }

        if (!current.toString().isBlank()) {
            chunkTexts.add(current.toString().trim());
        }

        List<TextChunk> result = new ArrayList<>(chunkTexts.size());
        for (int i = 0; i < chunkTexts.size(); i++) {
            String text = chunkTexts.get(i);
            result.add(new TextChunk(text, estimateTokens(text), Map.of("chunkIndexInSource", i)));
        }
        return result;
    }

    private List<String> splitIntoParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : content.split("\\n\\s*\\n")) {
            String trimmed = paragraph.strip();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    /**
     * Splits a single paragraph that exceeds the chunk size on its own, first by sentence
     * boundaries and, if a "sentence" is still too large (e.g. a long line of code), by a hard
     * character-count window.
     */
    private List<String> splitOversizedParagraph(String paragraph) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sentence : paragraph.split("(?<=[.!?])\\s+")) {
            if (estimateTokens(sentence) > chunkSizeTokens) {
                if (!current.isEmpty()) {
                    result.add(current.toString().trim());
                    current.setLength(0);
                }
                result.addAll(hardSplit(sentence));
                continue;
            }

            if (!current.isEmpty() && estimateTokens(current.toString()) + estimateTokens(sentence) > chunkSizeTokens) {
                result.add(current.toString().trim());
                current.setLength(0);
            }

            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(sentence);
        }

        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }
        return result;
    }

    private List<String> hardSplit(String text) {
        int maxChars = chunkSizeTokens * CHARS_PER_TOKEN;
        List<String> result = new ArrayList<>();
        for (int start = 0; start < text.length(); start += maxChars) {
            result.add(text.substring(start, Math.min(start + maxChars, text.length())).trim());
        }
        return result;
    }

    private String takeTrailingTokens(String text, int tokens) {
        if (tokens <= 0) {
            return "";
        }
        int maxChars = tokens * CHARS_PER_TOKEN;
        if (text.length() <= maxChars) {
            return text.trim();
        }
        String tail = text.substring(text.length() - maxChars);
        int spaceIndex = tail.indexOf(' ');
        if (spaceIndex >= 0 && spaceIndex < tail.length() - 1) {
            tail = tail.substring(spaceIndex + 1);
        }
        return tail.trim();
    }

    private int estimateTokens(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / (double) CHARS_PER_TOKEN));
    }
}
