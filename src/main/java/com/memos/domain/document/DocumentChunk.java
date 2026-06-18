package com.memos.domain.document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class DocumentChunk {

    private final UUID id;
    private final UUID documentId;
    private final int chunkIndex;
    private final String content;
    private final float[] embedding;
    private final int tokenCount;
    private final Map<String, Object> metadata;
    private final Instant createdAt;
}
