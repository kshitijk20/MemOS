package com.memos.infrastructure.ingestion.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.memos.application.ingestion.DocumentParserPort;
import com.memos.application.ingestion.ParsedDocument;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.exception.IngestionException;

import lombok.extern.slf4j.Slf4j;

/**
 * Extracts plain text from a PDF file on local disk (the path stored as the document's
 * {@code sourceUri} by the upload endpoint).
 */
@Slf4j
@Component
public class PdfDocumentParser implements DocumentParserPort {

    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z]+");
    private static final int ALPHABET_SIZE = 26;
    private static final int MIN_WORDS_FOR_SHIFT_DETECTION = 50;
    private static final double MIN_SHIFTED_MATCH_RATIO = 0.05;
    private static final double MIN_SHIFT_IMPROVEMENT_FACTOR = 3.0;

    /** A handful of very common English words, used to detect whole-document Caesar shifts. */
    private static final Set<String> COMMON_WORDS = Set.of(
            "the", "of", "and", "to", "in", "is", "that", "for", "with", "as",
            "are", "this", "on", "be", "by", "an", "or", "from", "at", "which",
            "we", "it", "its", "was", "not", "have", "but", "can", "their", "these");

    @Override
    public DocumentSourceType sourceType() {
        return DocumentSourceType.PDF;
    }

    @Override
    public List<ParsedDocument> parse(Document document) {
        Path path = Path.of(document.getSourceUri());
        try (PDDocument pdf = Loader.loadPDF(path.toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            // PDFBox can emit NUL characters for glyphs with no Unicode mapping (e.g. broken
            // font subsets); Postgres's text type rejects 0x00, so strip them before storage.
            text = text.replace("\u0000", "");
            text = repairCaesarShift(text);
            Map<String, Object> metadata = Map.of("pageCount", pdf.getNumberOfPages());
            return List.of(new ParsedDocument(document.getTitle(), text, metadata));
        } catch (IOException e) {
            throw new IngestionException("Failed to parse PDF '" + document.getTitle() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Some PDFs (often LaTeX output with non-standard subsetted fonts and a broken
     * {@code /ToUnicode} CMap) extract as text that is uniformly shifted by a constant offset in
     * the alphabet (e.g. "Sgd" instead of "The"), even though the PDF renders correctly
     * visually. Detect this by checking, for every possible shift, how many extracted words
     * become common English words once un-shifted; if one non-zero shift is a clear winner over
     * no shift at all, the whole document is corrupted this way and is corrected in place.
     */
    private String repairCaesarShift(String text) {
        Matcher matcher = WORD_PATTERN.matcher(text);
        int totalWords = 0;
        int[] matchesByShift = new int[ALPHABET_SIZE];
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() < 2 || word.length() > 12) {
                continue;
            }
            totalWords++;
            for (int shift = 0; shift < ALPHABET_SIZE; shift++) {
                if (COMMON_WORDS.contains(shift(word, shift).toLowerCase())) {
                    matchesByShift[shift]++;
                }
            }
        }
        if (totalWords < MIN_WORDS_FOR_SHIFT_DETECTION) {
            return text;
        }

        int bestShift = 0;
        for (int shift = 1; shift < ALPHABET_SIZE; shift++) {
            if (matchesByShift[shift] > matchesByShift[bestShift]) {
                bestShift = shift;
            }
        }

        double bestRatio = (double) matchesByShift[bestShift] / totalWords;
        double noShiftRatio = (double) matchesByShift[0] / totalWords;
        if (bestShift == 0 || bestRatio < MIN_SHIFTED_MATCH_RATIO
                || bestRatio < noShiftRatio * MIN_SHIFT_IMPROVEMENT_FACTOR) {
            return text;
        }

        log.warn("Detected a constant +{} character shift in extracted PDF text; correcting before storage", bestShift);
        return shift(text, bestShift);
    }

    /** Shifts each letter forward by {@code amount} positions, wrapping within its case's alphabet. */
    private String shift(String text, int amount) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                result.append((char) ('A' + (c - 'A' + amount) % ALPHABET_SIZE));
            } else if (c >= 'a' && c <= 'z') {
                result.append((char) ('a' + (c - 'a' + amount) % ALPHABET_SIZE));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
