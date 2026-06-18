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

export type DocumentSourceType = 'PDF' | 'MARKDOWN' | 'GIT_REPOSITORY'
export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface DocumentResponse {
  id: string
  title: string
  sourceType: DocumentSourceType
  sourceUri: string
  status: DocumentStatus
  errorMessage: string | null
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

export interface GitIngestRequest {
  repoUrl: string
  title: string
  branch?: string
}

// --- Conversations ---

export type AiProvider = 'GEMINI' | 'OPENAI' | 'CLAUDE'

export interface ConversationResponse {
  id: string
  title: string | null
  provider: AiProvider
  model: string
  createdAt: string
  updatedAt: string
}

export interface CreateConversationRequest {
  title?: string
  provider?: AiProvider
  model?: string
}

export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM'

export interface Citation {
  documentId: string
  documentChunkId: string
  sourceTitle: string
  snippet: string
  score: number
}

export interface MessageResponse {
  id: string
  role: MessageRole
  content: string
  citations: Citation[]
  createdAt: string
}

export interface SendMessageRequest {
  content: string
}
