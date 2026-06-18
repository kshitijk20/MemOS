import { apiClient } from './client'
import type { DocumentChunkResponse, DocumentResponse, GitIngestRequest, PageResponse } from './types'

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

export async function deleteDocument(id: string): Promise<void> {
  await apiClient.delete(`/api/v1/documents/${id}`)
}
