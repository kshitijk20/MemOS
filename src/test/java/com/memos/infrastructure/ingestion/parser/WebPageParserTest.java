package com.memos.infrastructure.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import com.memos.application.ingestion.ParsedDocument;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.document.DocumentStatus;
import com.memos.domain.exception.IngestionException;

class WebPageParserTest {

    private final WebPageParser parser = new WebPageParser();

    @Test
    void wikipediaVectorHtmlKeepsArticleTextDespiteMenuClassesOnHtml() {
        String html = """
                <html class="client-nojs vector-feature-language-in-main-menu-disabled vector-feature-main-menu-pinned-disabled">
                  <head><title>Kannada - Wikipedia</title></head>
                  <body>
                    <nav>Main menu Contents</nav>
                    <div id="mw-content-text">
                      <p>Kannada is a Dravidian language.</p>
                    </div>
                    <footer>Privacy policy</footer>
                  </body>
                </html>
                """;

        List<ParsedDocument> parsed = parser.toParsedDocuments(
                document("Kannada"), "https://en.wikipedia.org/wiki/Kannada", Jsoup.parse(html));

        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).sourceLabel()).isEqualTo("Kannada - Wikipedia");
        assertThat(parsed.get(0).content()).contains("Kannada is a Dravidian language.");
        assertThat(parsed.get(0).content()).doesNotContain("Main menu").doesNotContain("Privacy policy");
    }

    @Test
    void fallsBackToArticleThenMain() {
        String html = """
                <html>
                  <body>
                    <nav>Site nav</nav>
                    <article><p>Article body here.</p></article>
                  </body>
                </html>
                """;

        String text = parser.extractReadableText(Jsoup.parse(html));

        assertThat(text).contains("Article body here.");
        assertThat(text).doesNotContain("Site nav");
    }

    @Test
    void blankContentThrows() {
        org.jsoup.nodes.Document html = Jsoup.parse("<html><body></body></html>");

        assertThatThrownBy(() -> parser.toParsedDocuments(document("Empty"), "https://example.com", html))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("No readable text found at https://example.com");
    }

    @Test
    void cloudflareChallengePageThrows() {
        String html = """
                <html>
                  <head><title>Just a moment...</title></head>
                  <body>
                    <div id="challenge-running">Checking your browser before accessing example.com</div>
                  </body>
                </html>
                """;

        assertThatThrownBy(() -> parser.toParsedDocuments(
                document("Blocked"), "https://example.com/docs", Jsoup.parse(html)))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("Cloudflare");
    }

    @Test
    void fetchFailureDescribesForbiddenAndTimeout() {
        assertThat(WebFetchFailure.describe("https://example.com", new org.jsoup.HttpStatusException("denied", 403, "https://example.com")))
                .contains("Access denied")
                .contains("Cloudflare");
        assertThat(WebFetchFailure.describe("https://example.com", new java.net.SocketTimeoutException("Read timed out")))
                .contains("Timed out")
                .contains("Retry");
        assertThat(WebFetchFailure.describe(
                        "https://medium.com/post",
                        new java.io.IOException("HTTP error fetching URL. Status=403, URL=https://medium.com/post")))
                .contains("Access denied")
                .contains("HTTP 403");
        assertThat(WebFetchFailure.emptyPageMessage("https://example.com"))
                .contains("JavaScript-rendered");
    }

    private static Document document(String title) {
        Instant now = Instant.now();
        return Document.builder()
                .id(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .title(title)
                .sourceType(DocumentSourceType.WEB_PAGE)
                .sourceUri("https://example.com")
                .status(DocumentStatus.PENDING)
                .metadata(Map.of())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
