package com.memos.web.conversation;

import java.util.UUID;

import com.memos.domain.conversation.Citation;

public record CitationResponse(
        UUID documentId,
        UUID documentChunkId,
        String sourceTitle,
        String snippet,
        double score) {

    public static CitationResponse from(Citation citation) {
        return new CitationResponse(
                citation.getDocumentId(),
                citation.getDocumentChunkId(),
                citation.getSourceTitle(),
                citation.getSnippet(),
                citation.getScore());
    }
}
