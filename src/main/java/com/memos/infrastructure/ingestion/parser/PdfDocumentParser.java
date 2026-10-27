package com.memos.infrastructure.ingestion.parser;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import javax.imageio.ImageIO;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.memos.application.ingestion.ChunkingPort;
import com.memos.application.ingestion.DocumentParserPort;
import com.memos.application.ingestion.ParsedDocument;
import com.memos.domain.ai.TextRecognitionPort;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.exception.IngestionException;

import lombok.extern.slf4j.Slf4j;

/**
 * Extracts plain text from a PDF file on local disk (the path stored as the document's
 * {@code sourceUri} by the upload endpoint). Pages without a text layer (scans, photos saved as
 * PDF) are rendered to an image and read with OCR, up to {@code memos.ingestion.ocr.max-pages}.
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

    /** A page with fewer visible characters than this is treated as a scan and read with OCR. */
    static final int MIN_TEXT_CHARS_PER_PAGE = 20;

    /** Render resolution for OCR: enough for small print, while keeping images small. */
    private static final float OCR_DPI = 150f;
    private static final int OCR_MAX_IMAGE_SIDE_PX = 2000;

    private final TextRecognitionPort textRecognitionPort;
    private final int maxOcrPages;

    public PdfDocumentParser(
            TextRecognitionPort textRecognitionPort,
            @Value("${memos.ingestion.ocr.max-pages:30}") int maxOcrPages) {
        this.textRecognitionPort = textRecognitionPort;
        this.maxOcrPages = maxOcrPages;
    }

    @Override
    public DocumentSourceType sourceType() {
        return DocumentSourceType.PDF;
    }

    @Override
    public List<ParsedDocument> parse(Document document) {
        Path path = Path.of(document.getSourceUri());
        try (PDDocument pdf = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(pdf);
            StringBuilder text = new StringBuilder();
            int ocrPages = 0;
            int skippedScans = 0;
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                // PDFBox can emit NUL characters for glyphs with no Unicode mapping (e.g. broken
                // font subsets); Postgres's text type rejects 0x00, so strip them before storage.
                String pageText = stripper.getText(pdf).replace("\u0000", "");
                if (visibleChars(pageText) < MIN_TEXT_CHARS_PER_PAGE && hasImage(pdf.getPage(page - 1))) {
                    if (ocrPages < maxOcrPages) {
                        pageText = recognizePage(pdf, renderer, page - 1);
                        ocrPages++;
                    } else {
                        skippedScans++;
                    }
                }
                // Same page separator the chunker uses to track page numbers for citations.
                text.append(pageText).append("\n\n").append(ChunkingPort.PAGE_BREAK).append("\n\n");
            }
            if (ocrPages > 0) {
                log.info("Read {} scanned page(s) of '{}' with OCR", ocrPages, document.getTitle());
            }
            if (skippedScans > 0) {
                log.warn("Skipped OCR for {} scanned page(s) of '{}' (limit {} per document)",
                        skippedScans, document.getTitle(), maxOcrPages);
            }
            String content = repairCaesarShift(text.toString());
            if (visibleChars(content.replace(ChunkingPort.PAGE_BREAK, "")) == 0) {
                throw new IngestionException("No readable text found in '" + document.getTitle() + "'.");
            }
            Map<String, Object> metadata = Map.of("pageCount", pdf.getNumberOfPages(), "ocrPages", ocrPages);
            return List.of(new ParsedDocument(document.getTitle(), content, metadata));
        } catch (IOException e) {
            throw new IngestionException("Failed to parse PDF '" + document.getTitle() + "': " + e.getMessage(), e);
        }
    }

    private String recognizePage(PDDocument pdf, PDFRenderer renderer, int pageIndex) throws IOException {
        PDRectangle box = pdf.getPage(pageIndex).getCropBox();
        float longestSidePoints = Math.max(box.getWidth(), box.getHeight());
        // Points are 1/72 inch; cap the size so huge pages (posters, plans) stay small.
        float scale = Math.min(OCR_DPI / 72f, OCR_MAX_IMAGE_SIDE_PX / Math.max(longestSidePoints, 1f));
        BufferedImage image = renderer.renderImage(pageIndex, scale, ImageType.RGB);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", jpeg);
        return textRecognitionPort.recognizeText(jpeg.toByteArray(), "image/jpeg");
    }

    /** Scanned pages are images; blank pages and short title pages have none and are left alone. */
    private static boolean hasImage(PDPage page) throws IOException {
        PDResources resources = page.getResources();
        if (resources == null) {
            return false;
        }
        for (COSName name : resources.getXObjectNames()) {
            if (resources.isImageXObject(name)) {
                return true;
            }
        }
        return false;
    }

    private static int visibleChars(String text) {
        return (int) text.codePoints().filter(c -> !Character.isWhitespace(c)).count();
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
