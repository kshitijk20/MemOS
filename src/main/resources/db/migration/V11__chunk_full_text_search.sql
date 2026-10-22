-- Keyword side of hybrid retrieval (names, error codes, APIs).
-- 'simple' config avoids English stemming that would mangle identifiers.
ALTER TABLE document_chunks
    ADD COLUMN content_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content, ''))) STORED;

CREATE INDEX idx_document_chunks_content_tsv ON document_chunks USING gin (content_tsv);
