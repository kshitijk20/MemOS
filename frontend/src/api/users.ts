import { apiClient } from '@/api/client'
import type { UpdateProfileRequest, UserProfileResponse } from '@/api/types'

export async function getProfile(): Promise<UserProfileResponse> {
  const response = await apiClient.get<UserProfileResponse>('/api/v1/users/me')
  return response.data
}

export async function updateProfile(data: UpdateProfileRequest): Promise<UserProfileResponse> {
  const response = await apiClient.patch<UserProfileResponse>('/api/v1/users/me', data)
  return response.data
}
