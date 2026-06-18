# MemOS Backend

MemOS is a RAG (Retrieval-Augmented Generation) knowledge assistant backend. It lets users
upload documents (PDF, Markdown, or entire Git repositories), automatically chunks and embeds
their content into PGVector, and answers natural-language questions about that content via an
LLM — with every answer backed by citations pointing back to the source chunks.

Built with Java 21, Spring Boot 3, PostgreSQL + pgvector, Redis, and a clean hexagonal
architecture (`domain` / `application` / `infrastructure` / `web`) so new ingestion sources
(GitHub issues, Jira, Confluence, Slack, ...) and AI providers can be added as new adapters
without touching core business logic.

## Features

- **JWT authentication** — register/login/refresh/logout with BCrypt password hashing.
- **Document ingestion** — upload PDF or Markdown files, or point at a Git repository (cloned
  via JGit, walked and filtered by file extension). Ingestion runs asynchronously; poll the
  document's status until `COMPLETED`.
- **Semantic chunking** — paragraph/heading-aware recursive splitting with configurable token
  size and overlap.
- **PGVector storage & retrieval** — chunk embeddings stored in a `vector(768)` column with an
  HNSW index, queried via cosine similarity (`<=>`) scoped to the authenticated user's own
  documents.
- **Pluggable AI providers** — `ChatPort` / `EmbeddingPort` abstractions with adapters for
  **Gemini** (default, free tier), **OpenAI**, and **Claude**. Conversations can select a
  provider/model per-conversation.
- **Conversational RAG** — each question retrieves the top-K most relevant chunks, builds a
  prompt with conversation history, calls the resolved chat provider, and persists the
  assistant's reply together with `Citation` records (source document, chunk, snippet, score).
- **Caching** — Redis-backed embedding cache (1h TTL) to avoid re-spending free-tier embedding
  quota on repeated text.
- **OpenAPI/Swagger UI**, Actuator health/info endpoints, global exception handling with
  consistent error responses.

## Tech Stack

| Concern          | Choice                                                            |
|------------------|--------------------------------------------------------------------|
| Language/runtime | Java 21, Spring Boot 3.4.5                                         |
| Database         | PostgreSQL 16 + pgvector extension                                 |
| Cache            | Redis 7                                                            |
| Migrations       | Flyway                                                              |
| Security         | Spring Security + JWT (jjwt)                                       |
| AI providers     | Gemini (default, via OpenAI-compatible endpoint), OpenAI, Claude   |
| Parsing          | Apache PDFBox (PDF), CommonMark (Markdown), JGit (Git repos)       |
| Build            | Maven                                                               |
| Tests            | JUnit 5, Mockito, Spring MockMvc, Testcontainers (pgvector)        |

## Architecture

```
com.memos
├── domain/          pure Java models, repository ports, AI ports — no framework dependencies
├── application/      use-case orchestration (auth, ingestion, RAG, conversations)
├── infrastructure/    adapters: persistence (JPA + pgvector), security (JWT), AI clients
│                       (Gemini/OpenAI/Claude), document parsers, chunking, Redis cache
└── web/               REST controllers, request/response DTOs, global exception handling
```

The `application` layer depends only on `domain` ports. `infrastructure` provides the concrete
adapters. `web` is the only layer that knows about HTTP. This keeps the RAG/ingestion/auth logic
testable in isolation (see `src/test/java/com/memos/application/**` and
`src/test/java/com/memos/infrastructure/ingestion/**`) and makes it straightforward to add a new
ingestion source: implement `DocumentParserPort`, register it in
`infrastructure/ingestion/parser/DocumentParserRegistry`, and add the corresponding
`DocumentSourceType` — no changes required to `DocumentIngestionService`.

**Known limitation:** the `document_chunks.embedding` column is fixed at `vector(768)` to match
Gemini's `gemini-embedding-001` model truncated to 768 dimensions (the default, free embedding
provider). Switching
`memos.ai.embedding.provider` to OpenAI (1536 dims) would require a new Flyway migration to
alter the column and re-embedding all existing chunks — out of scope for this version.

## Getting Started

### Prerequisites

- Java 21
- Maven (or use the included setup — any standard `mvn`)
- Docker + Docker Compose (for Postgres/Redis, or the full containerized stack)
- A free [Gemini API key](https://aistudio.google.com/app/apikey) (Google AI Studio) — no
  paid subscription needed for the default configuration

### 1. Configure environment

```bash
cp .env.example .env
```

Edit `.env` and set at minimum:

- `GEMINI_API_KEY` — your free key from Google AI Studio
- `JWT_SECRET` — generate with `openssl rand -base64 64`

`OPENAI_API_KEY` / `ANTHROPIC_API_KEY` are optional; only required if you set
`MEMOS_DEFAULT_AI_PROVIDER` (or a per-conversation `provider`) to `OPENAI` or `CLAUDE`, and you
must supply your own paid keys for those.

### 2. Run dependencies

```bash
docker compose up -d postgres redis
```

Flyway migrations run automatically on application startup.

### 3. Run the application

```bash
export $(grep -v '^#' .env | xargs)   # or source .env in your shell
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`. Swagger UI is at
`http://localhost:8080/swagger-ui.html`, Actuator health at
`http://localhost:8080/actuator/health`.

### Full containerized stack

```bash
docker compose up --build
```

This builds the app image (multi-stage Dockerfile: Maven build → `eclipse-temurin:21-jre`
runtime) and starts Postgres, Redis, and the app together.

## API Overview

All endpoints are under `/api/v1` and require a `Bearer <accessToken>` JWT except the
`/auth/**` endpoints.

| Method | Path                                  | Description                                  |
|--------|---------------------------------------|-----------------------------------------------|
| POST   | `/auth/register`                      | Create an account                              |
| POST   | `/auth/login`                         | Get an access/refresh token pair               |
| POST   | `/auth/refresh`                       | Exchange a refresh token for a new pair        |
| POST   | `/auth/logout`                        | Revoke a refresh token                         |
| POST   | `/documents/upload`                   | Upload a PDF/Markdown file (multipart)         |
| POST   | `/documents/git`                      | Ingest a Git repository by URL                 |
| GET    | `/documents`                          | List your documents (paginated)                |
| GET    | `/documents/{id}`                     | Get document detail/status                     |
| GET    | `/documents/{id}/chunks`              | Inspect a document's chunks (paginated)        |
| DELETE | `/documents/{id}`                     | Delete a document and its chunks               |
| POST   | `/conversations`                      | Create a conversation (optional provider/model)|
| GET    | `/conversations`                      | List your conversations (paginated)            |
| GET    | `/conversations/{id}`                 | Get conversation detail                        |
| DELETE | `/conversations/{id}`                 | Delete a conversation                          |
| GET    | `/conversations/{id}/messages`        | List messages (paginated)                      |
| POST   | `/conversations/{id}/messages`        | Ask a question — runs the RAG pipeline         |

### Example flow

```bash
# Register
curl -X POST localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"super-secret","fullName":"Alice"}'

# Login
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"super-secret"}' | jq -r .accessToken)

# Upload a markdown doc
curl -X POST localhost:8080/api/v1/documents/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F file=@notes.md -F title="My Notes"

# Poll status until COMPLETED, then create a conversation and ask a question
CONV_ID=$(curl -s -X POST localhost:8080/api/v1/conversations \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Notes Q&A"}' | jq -r .id)

curl -X POST localhost:8080/api/v1/conversations/$CONV_ID/messages \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"content":"What are my notes about?"}'
```

The response includes the assistant's reply plus a `citations[]` array referencing the
document/chunk(s) used to ground the answer.

## Configuration Reference

Key settings (see `application.yml` for the full list and defaults):

| Property                              | Purpose                                          |
|----------------------------------------|--------------------------------------------------|
| `memos.ai.default-provider`            | `GEMINI` (default) \| `OPENAI` \| `CLAUDE`        |
| `memos.ai.providers.*`                 | Per-provider API key, base URL, model, temperature|
| `memos.rag.top-k`                      | Number of chunks retrieved per question (default 5)|
| `memos.rag.history-window`             | Conversation messages included in the prompt     |
| `memos.ingestion.chunk-size-tokens`    | Target chunk size (default 500)                   |
| `memos.ingestion.chunk-overlap-tokens` | Overlap between chunks (default 50)               |
| `memos.ingestion.git.allowed-extensions`| File extensions ingested from Git repos          |
| `memos.jwt.*`                          | JWT secret and access/refresh token TTLs          |

## Testing

```bash
./mvnw test                 # unit + web-layer tests (Mockito, MockMvc)
./mvnw verify                # also runs Testcontainers integration tests (requires Docker)
```

- Unit tests cover the chunking adapter, ingestion service, auth service, and the RAG query
  service (all with mocked ports — no Spring context).
- Web-layer tests (`@WebMvcTest` + MockMvc + spring-security-test) cover auth, document, and
  conversation controllers.
- `DocumentChunkRepositoryAdapterIT` spins up `pgvector/pgvector:pg16` via Testcontainers and
  verifies cosine-similarity ordering, owner scoping, and status filtering for the retrieval
  query — this test requires Docker to be available.

## Extending MemOS

To add a new ingestion source (e.g. Confluence):

1. Add a value to `DocumentSourceType`.
2. Implement `DocumentParserPort` (return one `ParsedDocument` per logical unit, e.g. per page).
3. Register the parser in `DocumentParserRegistry`.
4. Add a controller endpoint (or scheduled job) that calls `DocumentIngestionService.submit(...)`
   followed by `.ingest(...)`.

To add a new AI provider, implement `ChatPort` (and `EmbeddingPort` if it should also serve
embeddings), register it in `ChatPortResolver`, and add its config block under
`memos.ai.providers`.
