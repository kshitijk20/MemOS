package com.memos.infrastructure.ingestion.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.memos.application.ingestion.DocumentParserPort;
import com.memos.application.ingestion.ParsedDocument;
import com.memos.domain.ai.TextRecognitionPort;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.exception.IngestionException;

import lombok.RequiredArgsConstructor;

/** Reads the text in an uploaded photo or screenshot with OCR. */
@Component
@RequiredArgsConstructor
public class ImageDocumentParser implements DocumentParserPort {

    /** Image formats the OCR model accepts, by file extension. */
    static final Map<String, String> MIME_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "heic", "image/heic",
            "heif", "image/heif");

    private final TextRecognitionPort textRecognitionPort;

    @Override
    public DocumentSourceType sourceType() {
        return DocumentSourceType.IMAGE;
    }

    @Override
    public List<ParsedDocument> parse(Document document) {
        Path path = Path.of(document.getSourceUri());
        String mimeType = mimeTypeFor(path.getFileName().toString());
        if (mimeType == null) {
            throw new IngestionException("Unsupported image format for '" + document.getTitle() + "'.");
        }
        byte[] image;
        try {
            image = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IngestionException("Failed to read image '" + document.getTitle() + "': " + e.getMessage(), e);
        }
        String text = textRecognitionPort.recognizeText(image, mimeType);
        if (text.isBlank()) {
            throw new IngestionException("No readable text found in the image '" + document.getTitle() + "'.");
        }
        return List.of(new ParsedDocument(document.getTitle(), text, Map.of("ocr", true)));
    }

    /** The image MIME type for a file name, or null if it isn't a supported image. */
    public static String mimeTypeFor(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? null : MIME_TYPES.get(filename.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
