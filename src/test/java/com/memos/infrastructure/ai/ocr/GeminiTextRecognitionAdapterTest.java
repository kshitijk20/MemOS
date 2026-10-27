package com.memos.infrastructure.ai.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeminiTextRecognitionAdapterTest {

    @Test
    void noTextReplyBecomesEmpty() {
        assertThat(GeminiTextRecognitionAdapter.clean(" NO_TEXT \n")).isEmpty();
        assertThat(GeminiTextRecognitionAdapter.clean(null)).isEmpty();
    }

    @Test
    void codeFenceAroundTheTranscriptionIsRemoved() {
        assertThat(GeminiTextRecognitionAdapter.clean("```markdown\n# Notes\n- one\n```")).isEqualTo("# Notes\n- one");
        assertThat(GeminiTextRecognitionAdapter.clean("Plain text")).isEqualTo("Plain text");
    }
}
