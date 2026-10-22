ALTER TABLE conversations
    ADD COLUMN document_scope JSONB NOT NULL DEFAULT '[]';
