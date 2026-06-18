CREATE TABLE citations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id          UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    document_id         UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    document_chunk_id   UUID NOT NULL REFERENCES document_chunks(id) ON DELETE CASCADE,
    source_title        VARCHAR(500) NOT NULL,
    snippet             TEXT NOT NULL,
    score               DOUBLE PRECISION NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_citations_message_id ON citations (message_id);
