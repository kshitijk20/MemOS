import { apiClient } from '@/api/client'
import type { UpdateProfileRequest, UserProfileResponse } from '@/api/types'

export async function getProfile(): Promise<UserProfileResponse> {
  const response = await apiClient.get<UserProfileResponse>('/api/v1/users/me')
  return response.data
}

/** Revokes every session of the current user, on all devices. */
export async function logoutEverywhere(): Promise<void> {
  await apiClient.post('/api/v1/users/me/logout-everywhere')
}

export async function updateProfile(data: UpdateProfileRequest): Promise<UserProfileResponse> {
  const response = await apiClient.patch<UserProfileResponse>('/api/v1/users/me', data)
  return response.data
}
