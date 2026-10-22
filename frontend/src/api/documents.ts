import { apiClient } from './client'
import type { DocumentChunkResponse, DocumentChunkSourceResponse, DocumentResponse, GitIngestRequest, PageResponse, UrlIngestRequest } from './types'

export async function listDocuments(page: number, size: number): Promise<PageResponse<DocumentResponse>> {
  const response = await apiClient.get<PageResponse<DocumentResponse>>('/api/v1/documents', {
    params: { page, size },
  })
  return response.data
}

export async function getDocument(id: string): Promise<DocumentResponse> {
  const response = await apiClient.get<DocumentResponse>(`/api/v1/documents/${id}`)
  return response.data
}

export async function uploadDocument(file: File, title: string): Promise<DocumentResponse> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('title', title)
  const response = await apiClient.post<DocumentResponse>('/api/v1/documents/upload', formData)
  return response.data
}

export async function ingestGitRepository(payload: GitIngestRequest): Promise<DocumentResponse> {
  const response = await apiClient.post<DocumentResponse>('/api/v1/documents/git', payload)
  return response.data
}

export async function ingestUrl(payload: UrlIngestRequest): Promise<DocumentResponse> {
  const response = await apiClient.post<DocumentResponse>('/api/v1/documents/url', payload)
  return response.data
}

export async function getDocumentChunks(
  id: string,
  page: number,
  size: number,
): Promise<PageResponse<DocumentChunkResponse>> {
  const response = await apiClient.get<PageResponse<DocumentChunkResponse>>(`/api/v1/documents/${id}/chunks`, {
    params: { page, size },
  })
  return response.data
}

export async function getDocumentChunk(
  documentId: string,
  chunkId: string,
): Promise<DocumentChunkSourceResponse> {
  const response = await apiClient.get<DocumentChunkSourceResponse>(
    `/api/v1/documents/${documentId}/chunks/${chunkId}`,
  )
  return response.data
}

export async function retryDocument(id: string): Promise<DocumentResponse> {
  const response = await apiClient.post<DocumentResponse>(`/api/v1/documents/${id}/retry`)
  return response.data
}

export async function reingestDocument(id: string, file?: File): Promise<DocumentResponse> {
  if (file) {
    const formData = new FormData()
    formData.append('file', file)
    const response = await apiClient.post<DocumentResponse>(`/api/v1/documents/${id}/reingest`, formData)
    return response.data
  }
  const response = await apiClient.post<DocumentResponse>(`/api/v1/documents/${id}/reingest`)
  return response.data
}

export async function deleteDocument(id: string): Promise<void> {
  await apiClient.delete(`/api/v1/documents/${id}`)
}

export interface IngestionProgress {
  phase: 'PARSING' | 'CHUNKING' | 'EMBEDDING' | 'SAVING'
  processed: number
  total: number
  percentComplete: number
}

export async function fetchDocumentProgress(id: string): Promise<IngestionProgress> {
  const response = await apiClient.get<IngestionProgress>(`/api/v1/documents/${id}/progress`)
  return response.data
}
