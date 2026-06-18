package com.memos.domain.ai;

/**
 * Role of a message in a chat completion prompt, independent of the persisted
 * conversation {@code MessageRole} (kept separate so the AI port abstraction does not
 * depend on the conversation aggregate).
 */
public enum ChatRole {
    SYSTEM,
    USER,
    ASSISTANT
}
