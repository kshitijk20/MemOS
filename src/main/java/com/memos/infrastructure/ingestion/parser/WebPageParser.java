package com.memos.infrastructure.ingestion.parser;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.memos.application.ingestion.DocumentParserPort;
import com.memos.application.ingestion.ParsedDocument;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.exception.IngestionException;

@Component
public class WebPageParser implements DocumentParserPort {

    private static final int TIMEOUT_MS = 30_000;
    private static final int MAX_REDIRECTS = 5;

    @Override
    public DocumentSourceType sourceType() {
        return DocumentSourceType.WEB_PAGE;
    }

    @Override
    public List<ParsedDocument> parse(Document document) {
        String url = document.getSourceUri();
        try {
            return toParsedDocuments(document, url, fetch(url));
        } catch (IOException e) {
            throw new IngestionException(WebFetchFailure.describe(url, e), e);
        }
    }

    /** Follows redirects by hand so every hop is checked by {@link PublicUrlGuard}, not just the first. */
    private org.jsoup.nodes.Document fetch(String url) throws IOException {
        String current = url;
        for (int hop = 0; ; hop++) {
            PublicUrlGuard.requirePublicHttpUrl(current);
            Connection.Response response = Jsoup.connect(current)
                    .userAgent("MemOS/1.0 (knowledge assistant; +https://github.com/memos)")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en")
                    .timeout(TIMEOUT_MS)
                    .followRedirects(false)
                    // Redirect responses often have no content type; checked below for the final page.
                    .ignoreContentType(true)
                    .execute();
            String location = response.header("Location");
            if (response.statusCode() / 100 == 3 && location != null) {
                if (hop >= MAX_REDIRECTS) {
                    throw new IngestionException("Too many redirects fetching " + url + ".");
                }
                current = new URL(response.url(), location).toString();
                continue;
            }
            String contentType = response.contentType() == null ? "" : response.contentType().toLowerCase(Locale.ROOT);
            if (!contentType.isEmpty() && !contentType.startsWith("text/") && !contentType.contains("xml")) {
                throw new IngestionException("The page at " + url + " isn't HTML (" + contentType
                        + "). Download the file and upload it instead.");
            }
            return response.parse();
        }
    }

    List<ParsedDocument> toParsedDocuments(Document document, String url, org.jsoup.nodes.Document html) {
        if (WebFetchFailure.looksLikeCloudflareChallenge(html)) {
            throw new IngestionException(WebFetchFailure.cloudflareMessage(url));
        }

        String pageTitle = html.title();
        String text = extractReadableText(html);

        if (text.isBlank()) {
            throw new IngestionException(WebFetchFailure.emptyPageMessage(url));
        }

        String sourceLabel = pageTitle.isBlank() ? document.getTitle() : pageTitle;
        return List.of(new ParsedDocument(sourceLabel, text, Map.of("url", url)));
    }

    /**
     * Prefer the article region when present. Strip chrome tags only inside that region so
     * class names on {@code <html>} / {@code <body>} (e.g. Wikipedia Vector's {@code ...menu...})
     * cannot delete the whole document.
     */
    String extractReadableText(org.jsoup.nodes.Document html) {
        Element content = firstPresent(
                html.selectFirst("#mw-content-text"),
                html.selectFirst("article"),
                html.selectFirst("main"),
                html.body());
        if (content == null) {
            return "";
        }

        Element copy = content.clone();
        copy.select("script, style, noscript, nav, footer, header, aside").remove();
        return copy.text();
    }

    private static Element firstPresent(Element... candidates) {
        for (Element candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }
}
