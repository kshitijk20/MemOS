CREATE INDEX idx_documents_owner_id_created_at ON documents (owner_id, created_at DESC);
CREATE INDEX idx_conversations_user_id_updated_at ON conversations (user_id, updated_at DESC);
