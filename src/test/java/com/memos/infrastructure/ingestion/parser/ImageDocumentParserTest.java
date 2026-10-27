package com.memos.infrastructure.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.memos.domain.ai.TextRecognitionPort;
import com.memos.domain.document.Document;
import com.memos.domain.exception.IngestionException;

class ImageDocumentParserTest {

    @TempDir
    Path tempDir;

    private final TextRecognitionPort ocr = mock(TextRecognitionPort.class);
    private final ImageDocumentParser parser = new ImageDocumentParser(ocr);

    @Test
    void readsThePhotoWithOcrUsingItsMimeType() throws IOException {
        Path photo = Files.write(tempDir.resolve("memos-upload-1.HEIC"), new byte[] {1, 2, 3});
        when(ocr.recognizeText(any(), eq("image/heic"))).thenReturn("Buy milk\n- eggs");

        assertThat(parser.parse(document(photo)).getFirst().content()).isEqualTo("Buy milk\n- eggs");
    }

    @Test
    void photoWithoutTextFailsClearly() throws IOException {
        Path photo = Files.write(tempDir.resolve("memos-upload-2.jpg"), new byte[] {1});
        when(ocr.recognizeText(any(), any())).thenReturn("");

        assertThatThrownBy(() -> parser.parse(document(photo)))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("No readable text");
    }

    @Test
    void recognizesSupportedImageExtensions() {
        assertThat(ImageDocumentParser.mimeTypeFor("IMG_0001.JPG")).isEqualTo("image/jpeg");
        assertThat(ImageDocumentParser.mimeTypeFor("scan.webp")).isEqualTo("image/webp");
        assertThat(ImageDocumentParser.mimeTypeFor("anim.gif")).isNull();
        assertThat(ImageDocumentParser.mimeTypeFor("noextension")).isNull();
    }

    private static Document document(Path path) {
        return Document.builder().title("Notes").sourceUri(path.toString()).build();
    }
}
