package com.memos.domain.conversation;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * A reference from an assistant {@link Message} back to the {@link
 * com.memos.domain.document.DocumentChunk} that was used as context to produce the answer.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Citation {

    private final UUID id;
    private final UUID messageId;
    private final UUID documentId;
    private final UUID documentChunkId;
    private final String sourceTitle;
    private final String snippet;
    private final double score;

    /** The [n] this source had in the prompt, i.e. how the answer refers to it. */
    private final Integer sourceNumber;
    private final Instant createdAt;
}
