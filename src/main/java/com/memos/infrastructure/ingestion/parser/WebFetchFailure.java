package com.memos.infrastructure.ingestion.parser;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;

final class WebFetchFailure {

    private WebFetchFailure() {
    }

    static String describe(String url, IOException error) {
        if (error instanceof SocketTimeoutException) {
            return "Timed out fetching " + url + " after 30s. The site may be slow or blocking automated requests. Retry to try again.";
        }
        if (error instanceof UnknownHostException) {
            return "Could not resolve host for " + url + ". Check the URL and retry.";
        }
        if (error instanceof HttpStatusException statusException) {
            return describeHttpStatus(url, statusException.getStatusCode());
        }
        String message = error.getMessage() == null ? "" : error.getMessage();
        Matcher statusMatch = Pattern.compile("Status=(\\d+)").matcher(message);
        if (statusMatch.find()) {
            return describeHttpStatus(url, Integer.parseInt(statusMatch.group(1)));
        }
        if (message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        return "Failed to fetch URL " + url + ": " + message + ". Retry to try again.";
    }

    private static String describeHttpStatus(String url, int code) {
        if (code == 401 || code == 403) {
            return "Access denied (HTTP " + code + ") fetching " + url
                    + ". This is often Cloudflare or bot protection. Retry in a moment, or save the page and upload it as a file.";
        }
        if (code == 404) {
            return "Page not found (HTTP 404): " + url;
        }
        if (code == 429) {
            return "The site rate-limited the fetch (HTTP 429) for " + url + ". Wait a bit and retry.";
        }
        return "The site returned HTTP " + code + " for " + url + ". Retry to try again.";
    }

    static boolean looksLikeCloudflareChallenge(Document html) {
        if (html == null) {
            return false;
        }
        String title = html.title() == null ? "" : html.title().toLowerCase(Locale.ROOT);
        if (title.contains("just a moment") || title.contains("attention required")) {
            return true;
        }
        if (html.selectFirst("#challenge-running, .cf-browser-verification, #cf-error-details, iframe[src*='challenges.cloudflare.com']") != null) {
            return true;
        }
        String body = html.body() == null ? "" : html.body().text().toLowerCase(Locale.ROOT);
        return body.contains("cloudflare") && (body.contains("attention required") || body.contains("checking your browser"));
    }

    static String emptyPageMessage(String url) {
        return "No readable text found at " + url
                + ". The page may be empty, JavaScript-rendered, or a Cloudflare challenge. Retry, or upload a saved copy.";
    }

    static String cloudflareMessage(String url) {
        return "Blocked by Cloudflare (or similar bot protection) at " + url
                + ". Retry in a moment, or save the page and upload it as a file.";
    }
}
