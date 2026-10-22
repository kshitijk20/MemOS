ALTER TABLE documents
    ADD COLUMN version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN source_fingerprint VARCHAR(64);

CREATE UNIQUE INDEX uq_documents_owner_fingerprint
    ON documents (owner_id, source_fingerprint)
    WHERE source_fingerprint IS NOT NULL;
