export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
}

export interface PageResponse<T> {
  items: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

// --- Auth ---

export type UserRole = 'USER' | 'ADMIN'

export interface RegisterRequest {
  email: string
  password: string
  fullName: string
}

/** Second sign-up step: the code emailed after {@link RegisterRequest}. */
export interface VerifySignupRequest {
  email: string
  code: string
}

/** Whether this server accepts new accounts. */
export interface RegistrationPolicy {
  open: boolean
}

export interface UserResponse {
  id: string
  email: string
  fullName: string
  role: UserRole
}

export interface LoginRequest {
  email: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

export interface RefreshRequest {
  refreshToken: string
}

// --- Documents ---

export type DocumentSourceType = 'PDF' | 'MARKDOWN' | 'GIT_REPOSITORY' | 'WEB_PAGE'
export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface DocumentResponse {
  id: string
  title: string
  sourceType: DocumentSourceType
  sourceUri: string
  status: DocumentStatus
  errorMessage: string | null
  version: number
  metadata: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface DocumentChunkResponse {
  id: string
  chunkIndex: number
  content: string
  tokenCount: number
  metadata: Record<string, unknown>
  createdAt: string
}

export interface DocumentChunkSourceResponse {
  id: string
  documentId: string
  chunkIndex: number
  content: string
  tokenCount: number
  metadata: Record<string, unknown>
  createdAt: string
  documentTitle: string
  sourceUri: string
  sourceType: DocumentSourceType
  previousContent: string | null
  nextContent: string | null
}

export interface UserProfileResponse {
  id: string
  email: string
  fullName: string
  role: UserRole
}

export interface UpdateProfileRequest {
  fullName: string
  email: string
  currentPassword?: string
  newPassword?: string
}

export interface GitIngestRequest {
  repoUrl: string
  title: string
  branch?: string
}

export interface UrlIngestRequest {
  title: string
  url: string
}

// --- Conversations ---

export type AiProvider = 'GEMINI' | 'OPENAI' | 'CLAUDE'

export interface ConversationResponse {
  id: string
  title: string | null
  provider: AiProvider
  model: string
  documentScope: string[]
  collectionId: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateConversationRequest {
  title?: string
  provider?: AiProvider
  model?: string
  documentIds?: string[]
  collectionId?: string
}

/** What a chat searches: everything, one collection, or specific documents. */
export type ConversationScope =
  | { kind: 'all' }
  | { kind: 'collection'; collectionId: string }
  | { kind: 'documents'; documentIds: string[] }

export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM'

export interface Citation {
  documentId: string
  documentChunkId: string
  sourceTitle: string
  snippet: string
  score: number
  /** The [n] the answer uses for this source; null on answers from before numbering was stored. */
  sourceNumber: number | null
}

export type MessageFeedback = 'UP' | 'DOWN'

export interface MessageResponse {
  id: string
  role: MessageRole
  content: string
  citations: Citation[]
  createdAt: string
  /** The assistant could not answer from the documents. */
  unanswered: boolean
  /** Standalone query searched when a follow-up question was rewritten. */
  searchQuery: string | null
  feedback: MessageFeedback | null
  feedbackComment: string | null
}

export interface FeedbackEntryResponse {
  messageId: string
  conversationId: string
  conversationTitle: string
  question: string | null
  answer: string
  feedback: MessageFeedback
  comment: string | null
  feedbackAt: string
}

// --- Collections ---

export interface CollectionResponse {
  id: string
  name: string
  documentIds: string[]
  createdAt: string
  updatedAt: string
}

export interface SendMessageRequest {
  content: string
}

// --- Streaming ---

export type StreamEvent =
  | { type: 'token'; content: string }
  | { type: 'done'; message: MessageResponse; conversationTitle: string | null }
  | { type: 'error'; message: string }
