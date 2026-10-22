package com.memos.application.rag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the {@code [n]} source references an answer uses, e.g. "[2]", "[1, 3]" or "[1][4]". */
final class SourceReferences {

    /** A bracket of numbers not followed by "(" (that would be a markdown link like [1](url)). */
    private static final Pattern REFERENCE = Pattern.compile("\\[(\\d{1,3}(?:\\s*,\\s*\\d{1,3})*)](?!\\()");

    private SourceReferences() {
    }

    /** Source numbers in the order they first appear, limited to 1..{@code sourceCount}. */
    static Set<Integer> referenced(String answer, int sourceCount) {
        Set<Integer> numbers = new LinkedHashSet<>();
        if (answer == null) {
            return numbers;
        }
        Matcher matcher = REFERENCE.matcher(answer);
        while (matcher.find()) {
            for (String part : matcher.group(1).split(",")) {
                int number = Integer.parseInt(part.strip());
                if (number >= 1 && number <= sourceCount) {
                    numbers.add(number);
                }
            }
        }
        return numbers;
    }
}
