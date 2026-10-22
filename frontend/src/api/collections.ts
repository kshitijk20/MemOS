import { apiClient } from './client'
import type { CollectionResponse } from './types'

export async function listCollections(): Promise<CollectionResponse[]> {
  const response = await apiClient.get<CollectionResponse[]>('/api/v1/collections')
  return response.data
}

export async function createCollection(name: string, documentIds: string[]): Promise<CollectionResponse> {
  const response = await apiClient.post<CollectionResponse>('/api/v1/collections', { name, documentIds })
  return response.data
}

/** Omitted fields are unchanged; {@code documentIds} replaces the whole membership. */
export async function updateCollection(
  id: string,
  changes: { name?: string; documentIds?: string[] },
): Promise<CollectionResponse> {
  const response = await apiClient.patch<CollectionResponse>(`/api/v1/collections/${id}`, changes)
  return response.data
}

export async function deleteCollection(id: string): Promise<void> {
  await apiClient.delete(`/api/v1/collections/${id}`)
}
