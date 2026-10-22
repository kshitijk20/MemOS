package com.memos.application.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceReferencesTest {

    @Test
    void findsSingleGroupedAndAdjacentReferencesInOrder() {
        assertThat(SourceReferences.referenced("See [2]. Also [1, 3] and [4][2].", 5))
                .containsExactly(2, 1, 3, 4);
    }

    @Test
    void ignoresOutOfRangeNumbersAndMarkdownLinks() {
        assertThat(SourceReferences.referenced("[9] and [1](https://example.com) and [0]", 3)).isEmpty();
    }
}
