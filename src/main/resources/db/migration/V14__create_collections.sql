CREATE TABLE collections (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_collections_owner_name ON collections (owner_id, lower(name));

CREATE TABLE collection_documents (
    collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    document_id   UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    PRIMARY KEY (collection_id, document_id)
);

CREATE INDEX idx_collection_documents_document_id ON collection_documents (document_id);

-- A conversation is limited either to a collection (resolved at question time, so documents
-- added later are included) or to an explicit document list, never both.
ALTER TABLE conversations
    ADD COLUMN collection_id UUID REFERENCES collections(id) ON DELETE SET NULL;
