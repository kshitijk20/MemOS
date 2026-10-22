package com.memos.infrastructure.ingestion.chunking;

import java.util.ArrayList;
import java.util.HashMap;
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

        boolean paged = content.contains(ChunkingPort.PAGE_BREAK);
        List<String> chunkTexts = new ArrayList<>();
        List<int[]> chunkPages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int firstPage = -1;
        int lastPage = -1;

        for (Paragraph paragraph : splitIntoParagraphs(content)) {
            int paragraphTokens = estimateTokens(paragraph.text());

            if (paragraphTokens > chunkSizeTokens) {
                if (!current.isEmpty()) {
                    chunkTexts.add(current.toString().trim());
                    chunkPages.add(new int[] {firstPage, lastPage});
                    current.setLength(0);
                    firstPage = -1;
                }
                for (String piece : splitOversizedParagraph(paragraph.text())) {
                    chunkTexts.add(piece);
                    chunkPages.add(new int[] {paragraph.page(), paragraph.page()});
                }
                continue;
            }

            if (!current.isEmpty() && estimateTokens(current.toString()) + paragraphTokens > chunkSizeTokens) {
                chunkTexts.add(current.toString().trim());
                chunkPages.add(new int[] {firstPage, lastPage});
                String overlap = takeTrailingTokens(current.toString(), overlapTokens);
                current.setLength(0);
                current.append(overlap);
                if (!overlap.isEmpty()) {
                    current.append("\n\n");
                }
                // The overlap repeats the previous chunk's tail; pages count from the new content.
                firstPage = -1;
            }

            if (firstPage < 0) {
                firstPage = paragraph.page();
            }
            lastPage = paragraph.page();
            current.append(paragraph.text()).append("\n\n");
        }

        if (!current.toString().isBlank()) {
            chunkTexts.add(current.toString().trim());
            chunkPages.add(new int[] {firstPage, lastPage});
        }

        List<TextChunk> result = new ArrayList<>(chunkTexts.size());
        for (int i = 0; i < chunkTexts.size(); i++) {
            String text = chunkTexts.get(i);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("chunkIndexInSource", i);
            if (paged && chunkPages.get(i)[0] > 0) {
                metadata.put("pageStart", chunkPages.get(i)[0]);
                metadata.put("pageEnd", chunkPages.get(i)[1]);
            }
            result.add(new TextChunk(text, estimateTokens(text), Map.copyOf(metadata)));
        }
        return result;
    }

    private record Paragraph(String text, int page) {}

    /** Paragraphs in order, each tagged with its 1-based page (always 1 for unpaged content). */
    private List<Paragraph> splitIntoParagraphs(String content) {
        List<Paragraph> paragraphs = new ArrayList<>();
        int page = 1;
        for (String paragraph : content.split("\\n\\s*\\n")) {
            String trimmed = paragraph.strip();
            if (trimmed.equals(ChunkingPort.PAGE_BREAK)) {
                page++;
                continue;
            }
            trimmed = trimmed.replace(ChunkingPort.PAGE_BREAK, "").strip();
            if (!trimmed.isEmpty()) {
                paragraphs.add(new Paragraph(trimmed, page));
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
