package com.memos.domain.ai;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatCompletionRequest {

    private final List<ChatMessage> messages;

    /** Optional model override; if null, the adapter's configured default model is used. */
    private final String model;

    private final Double temperature;
}
