package com.memos.application.rag;

import java.util.function.Consumer;

/**
 * Detects the model's "the sources don't answer this" reply, which the prompt asks it to signal
 * by starting with {@link #MARKER}. While streaming, tokens are held back only until it is clear
 * whether the reply starts with the marker, so the user never sees the marker and normal answers
 * stream with a delay of a few characters at most.
 */
final class NoAnswerDetector implements Consumer<String> {

    static final String MARKER = "NO_ANSWER";

    private final Consumer<String> downstream;
    private final StringBuilder held = new StringBuilder();
    private final StringBuilder full = new StringBuilder();
    private Boolean noAnswer;

    NoAnswerDetector(Consumer<String> downstream) {
        this.downstream = downstream;
    }

    /** True when a complete (non-streamed) reply is the marker, possibly followed by an explanation. */
    static boolean isNoAnswer(String reply) {
        return reply != null && stripLeading(reply).startsWith(MARKER);
    }

    @Override
    public void accept(String token) {
        full.append(token);
        if (Boolean.TRUE.equals(noAnswer)) {
            return;
        }
        if (Boolean.FALSE.equals(noAnswer)) {
            downstream.accept(token);
            return;
        }
        held.append(token);
        decide(false);
    }

    /** Call once the stream ends; flushes anything still held back. */
    void finish() {
        if (noAnswer == null) {
            decide(true);
        }
    }

    boolean noAnswer() {
        return Boolean.TRUE.equals(noAnswer);
    }

    String fullReply() {
        return full.toString();
    }

    private void decide(boolean streamEnded) {
        String candidate = stripLeading(held.toString());
        if (candidate.startsWith(MARKER)) {
            noAnswer = true;
        } else if (!MARKER.startsWith(candidate) || streamEnded) {
            // Diverged from the marker (or the stream ended on a partial match): a normal answer.
            noAnswer = false;
            if (!held.isEmpty()) {
                downstream.accept(held.toString());
            }
        } else {
            return;
        }
        held.setLength(0);
    }

    private static String stripLeading(String text) {
        // Models sometimes wrap the marker in markdown emphasis or code ticks.
        return text.stripLeading().replaceFirst("^[*_`]+", "");
    }
}
