import { apiClient } from './client'

export interface StatsResponse {
  totalDocuments: number
  readyDocuments: number
  processingDocuments: number
  failedDocuments: number
  totalConversations: number
  helpfulAnswers: number
  unhelpfulAnswers: number
}

export async function fetchStats(): Promise<StatsResponse> {
  const res = await apiClient.get<StatsResponse>('/api/v1/stats')
  return res.data
}
