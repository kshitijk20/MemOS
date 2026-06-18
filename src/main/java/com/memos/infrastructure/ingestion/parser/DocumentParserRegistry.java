package com.memos.infrastructure.ingestion.parser;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.memos.application.ingestion.DocumentParserPort;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.exception.IngestionException;

/**
 * Resolves the {@link DocumentParserPort} to use for a document, based on its {@link
 * DocumentSourceType}.
 */
@Component
public class DocumentParserRegistry {

    private final Map<DocumentSourceType, DocumentParserPort> parsersBySourceType;

    public DocumentParserRegistry(List<DocumentParserPort> parsers) {
        this.parsersBySourceType = parsers.stream()
                .collect(Collectors.toMap(DocumentParserPort::sourceType, Function.identity()));
    }

    public DocumentParserPort resolve(DocumentSourceType sourceType) {
        DocumentParserPort parser = parsersBySourceType.get(sourceType);
        if (parser == null) {
            throw new IngestionException("No parser registered for source type: " + sourceType);
        }
        return parser;
    }
}
