package com.memos.domain.ai;

/**
 * Reads text from an image (OCR): photos of notes, whiteboards, scanned pages. Used for image
 * uploads and for PDF pages that have no text layer.
 */
public interface TextRecognitionPort {

    /**
     * @return the text in the image in reading order, or an empty string if it has none
     * @throws com.memos.domain.exception.AiProviderException if the provider call fails
     */
    String recognizeText(byte[] image, String mimeType);
}
