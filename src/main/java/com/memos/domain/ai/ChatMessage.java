package com.memos.domain.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ChatMessage {

    private final ChatRole role;
    private final String content;

    public static ChatMessage system(String content) {
        return ChatMessage.builder().role(ChatRole.SYSTEM).content(content).build();
    }

    public static ChatMessage user(String content) {
        return ChatMessage.builder().role(ChatRole.USER).content(content).build();
    }

    public static ChatMessage assistant(String content) {
        return ChatMessage.builder().role(ChatRole.ASSISTANT).content(content).build();
    }
}
