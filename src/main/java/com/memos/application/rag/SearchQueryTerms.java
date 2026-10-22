package com.memos.application.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokens that keyword search should treat as identifiers (error codes, API names, class names).
 */
public final class SearchQueryTerms {

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9_.\\-/]*");
    private static final Pattern CAMEL = Pattern.compile(".*[a-z][A-Z].*");

    private SearchQueryTerms() {
    }

    public static List<String> tokens(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Matcher matcher = TOKEN.matcher(query);
        Set<String> tokens = new LinkedHashSet<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    public static List<String> identifiers(String query) {
        List<String> identifiers = new ArrayList<>();
        for (String token : tokens(query)) {
            if (isIdentifier(token)) {
                identifiers.add(token);
            }
        }
        return identifiers;
    }

    public static boolean isIdentifier(String token) {
        if (token.length() < 2) {
            return false;
        }
        boolean hasDigit = false;
        boolean hasSeparator = false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isDigit(c)) {
                hasDigit = true;
            }
            if (c == '_' || c == '-' || c == '.' || c == '/') {
                hasSeparator = true;
            }
        }
        return hasDigit || hasSeparator || CAMEL.matcher(token).matches() || token.equals(token.toUpperCase(Locale.ROOT));
    }

    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
