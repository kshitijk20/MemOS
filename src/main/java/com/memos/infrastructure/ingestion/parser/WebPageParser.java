package com.memos.infrastructure.ingestion.parser;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    @Override
    public DocumentSourceType sourceType() {
        return DocumentSourceType.WEB_PAGE;
    }

    @Override
    public List<ParsedDocument> parse(Document document) {
        String url = document.getSourceUri();
        try {
            org.jsoup.nodes.Document html = Jsoup.connect(url)
                    .userAgent("MemOS/1.0 (knowledge assistant; +https://github.com/memos)")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en")
                    .timeout(TIMEOUT_MS)
                    .get();
            return toParsedDocuments(document, url, html);
        } catch (IOException e) {
            throw new IngestionException(WebFetchFailure.describe(url, e), e);
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
