package com.memos.infrastructure.ingestion.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

import com.memos.application.ingestion.DocumentParserPort;
import com.memos.application.ingestion.ParsedDocument;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.exception.IngestionException;

/**
 * Reads a Markdown file from local disk and extracts its plain-text content (headings,
 * paragraphs, and code blocks), stripping inline formatting syntax.
 */
@Component
public class MarkdownDocumentParser implements DocumentParserPort {

    private final Parser parser = Parser.builder().build();

    @Override
    public DocumentSourceType sourceType() {
        return DocumentSourceType.MARKDOWN;
    }

    @Override
    public List<ParsedDocument> parse(Document document) {
        Path path = Path.of(document.getSourceUri());
        try {
            String markdown = Files.readString(path, StandardCharsets.UTF_8);
            Node root = parser.parse(markdown);
            PlainTextVisitor visitor = new PlainTextVisitor();
            root.accept(visitor);
            return List.of(new ParsedDocument(document.getTitle(), visitor.toString(), Map.of()));
        } catch (IOException e) {
            throw new IngestionException("Failed to read markdown file '" + document.getTitle() + "': " + e.getMessage(), e);
        }
    }

    private static final class PlainTextVisitor extends AbstractVisitor {

        private final StringBuilder text = new StringBuilder();

        @Override
        public void visit(Heading heading) {
            text.append('\n');
            visitChildren(heading);
            text.append("\n\n");
        }

        @Override
        public void visit(Paragraph paragraph) {
            visitChildren(paragraph);
            text.append("\n\n");
        }

        @Override
        public void visit(Text textNode) {
            text.append(textNode.getLiteral());
        }

        @Override
        public void visit(Code code) {
            text.append(code.getLiteral());
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            text.append('\n').append(fencedCodeBlock.getLiteral()).append('\n');
        }

        @Override
        public void visit(IndentedCodeBlock indentedCodeBlock) {
            text.append('\n').append(indentedCodeBlock.getLiteral()).append('\n');
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            text.append(' ');
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            text.append('\n');
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }
}
