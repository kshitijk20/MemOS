# MemOS — Entity Relationship Diagram

```mermaid
erDiagram
    users {
        UUID id PK
        VARCHAR email UK
        VARCHAR password_hash
        VARCHAR full_name
        VARCHAR role
        BOOLEAN enabled
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    refresh_tokens {
        UUID id PK
        UUID user_id FK
        VARCHAR token_hash UK
        TIMESTAMPTZ expires_at
        BOOLEAN revoked
        TIMESTAMPTZ created_at
    }

    documents {
        UUID id PK
        UUID owner_id FK
        VARCHAR title
        VARCHAR source_type
        TEXT source_uri
        VARCHAR status
        TEXT error_message
        JSONB metadata
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    document_chunks {
        UUID id PK
        UUID document_id FK
        INT chunk_index
        TEXT content
        VECTOR embedding
        INT token_count
        JSONB metadata
        TIMESTAMPTZ created_at
    }

    conversations {
        UUID id PK
        UUID user_id FK
        VARCHAR title
        VARCHAR provider
        VARCHAR model
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    messages {
        UUID id PK
        UUID conversation_id FK
        VARCHAR role
        TEXT content
        TIMESTAMPTZ created_at
    }

    citations {
        UUID id PK
        UUID message_id FK
        UUID document_id FK
        UUID document_chunk_id FK
        VARCHAR source_title
        TEXT snippet
        DOUBLE score
        TIMESTAMPTZ created_at
    }

    users ||--o{ refresh_tokens   : "has"
    users ||--o{ documents        : "owns"
    users ||--o{ conversations    : "starts"
    documents ||--o{ document_chunks : "chunked into"
    conversations ||--o{ messages    : "contains"
    messages ||--o{ citations        : "cites"
    documents ||--o{ citations       : "referenced by"
    document_chunks ||--o{ citations : "sourced from"
```

## Relationships

| From | To | Cardinality | Description |
|---|---|---|---|
| `users` | `refresh_tokens` | 1 → many | Each user can have multiple active refresh tokens |
| `users` | `documents` | 1 → many | A user owns all the documents they upload/ingest |
| `users` | `conversations` | 1 → many | A user can have multiple RAG conversations |
| `documents` | `document_chunks` | 1 → many | Each document is split into text chunks with embeddings |
| `conversations` | `messages` | 1 → many | A conversation holds an ordered history of messages |
| `messages` | `citations` | 1 → many | An assistant reply can cite multiple chunks |
| `documents` | `citations` | 1 → many | A document can be cited across multiple messages |
| `document_chunks` | `citations` | 1 → many | A specific chunk can be cited across multiple messages |

## Notes

- `document_chunks.embedding` is a `vector(768)` column (pgvector). Dimensionality is fixed for Gemini `text-embedding-004`. Switching providers requires a migration + full re-embedding.
- `citations` is a three-way join between a `message`, the `document` it came from, and the exact `document_chunk` that was retrieved — preserving full traceability of every RAG answer.
- All foreign keys use `ON DELETE CASCADE`, so deleting a user removes all their data; deleting a document removes its chunks and any citations referencing those chunks.
- `source_type` on `documents` is one of `PDF`, `MARKDOWN`, `GIT_REPOSITORY`.
- `role` on `messages` is one of `USER`, `ASSISTANT`.
- `provider` on `conversations` is one of `GEMINI`, `OPENAI`, `CLAUDE`.
