package com.memos.infrastructure.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.memos.application.ingestion.ChunkingPort;
import com.memos.application.ingestion.ParsedDocument;
import com.memos.domain.ai.TextRecognitionPort;
import com.memos.domain.document.Document;
import com.memos.domain.exception.IngestionException;

class PdfDocumentParserTest {

    @TempDir
    Path tempDir;

    private final TextRecognitionPort ocr = mock(TextRecognitionPort.class);

    @Test
    void scannedPagesAreReadWithOcrAndTextPagesAreNot() throws IOException {
        Path pdf = writePdf(PageKind.TEXT, PageKind.SCAN, PageKind.BLANK);
        when(ocr.recognizeText(any(), eq("image/jpeg"))).thenReturn("Handwritten meeting notes");

        List<ParsedDocument> parsed = new PdfDocumentParser(ocr, 30).parse(document(pdf));

        String[] pages = parsed.getFirst().content().split(ChunkingPort.PAGE_BREAK);
        assertThat(pages[0]).contains("The quarterly report covers revenue");
        assertThat(pages[1]).contains("Handwritten meeting notes");
        assertThat(parsed.getFirst().metadata()).containsEntry("ocrPages", 1).containsEntry("pageCount", 3);
        // Only the scanned page; the blank page has no image, so it isn't sent to OCR.
        verify(ocr, times(1)).recognizeText(any(), any());
    }

    @Test
    void ocrStopsAtThePerDocumentLimit() throws IOException {
        Path pdf = writePdf(PageKind.SCAN, PageKind.SCAN, PageKind.SCAN);
        when(ocr.recognizeText(any(), any())).thenReturn("scanned text");

        new PdfDocumentParser(ocr, 2).parse(document(pdf));

        verify(ocr, times(2)).recognizeText(any(), any());
    }

    @Test
    void textPdfNeedsNoOcr() throws IOException {
        Path pdf = writePdf(PageKind.TEXT);

        new PdfDocumentParser(ocr, 30).parse(document(pdf));

        verify(ocr, never()).recognizeText(any(), any());
    }

    @Test
    void pdfWithNoTextAnywhereFailsClearly() throws IOException {
        Path pdf = writePdf(PageKind.SCAN);
        when(ocr.recognizeText(any(), any())).thenReturn("");

        assertThatThrownBy(() -> new PdfDocumentParser(ocr, 30).parse(document(pdf)))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("No readable text");
    }

    private enum PageKind { TEXT, SCAN, BLANK }

    private Path writePdf(PageKind... kinds) throws IOException {
        Path path = tempDir.resolve("test.pdf");
        try (PDDocument pdf = new PDDocument()) {
            for (PageKind kind : kinds) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                if (kind == PageKind.BLANK) {
                    continue;
                }
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    if (kind == PageKind.TEXT) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.newLineAtOffset(72, 700);
                        content.showText("The quarterly report covers revenue, costs and hiring.");
                        content.endText();
                    } else {
                        PDImageXObject image = LosslessFactory.createFromImage(pdf, scannedImage());
                        content.drawImage(image, 72, 400, 300, 200);
                    }
                }
            }
            pdf.save(path.toFile());
        }
        return path;
    }

    private static BufferedImage scannedImage() {
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 300, 200);
        g.setColor(Color.BLACK);
        g.fillRect(20, 90, 260, 4);
        g.dispose();
        return image;
    }

    private static Document document(Path path) {
        return Document.builder().title("Report").sourceUri(path.toString()).build();
    }
}
