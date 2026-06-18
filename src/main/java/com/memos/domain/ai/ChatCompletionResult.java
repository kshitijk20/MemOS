package com.memos.domain.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ChatCompletionResult {

    private final String content;
    private final String modelUsed;
    private final Integer promptTokens;
    private final Integer completionTokens;
}
