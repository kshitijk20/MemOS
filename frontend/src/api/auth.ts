import { apiClient } from './client'
import type { AuthResponse, LoginRequest, RefreshRequest, RegisterRequest, UserResponse } from './types'

export async function register(payload: RegisterRequest): Promise<UserResponse> {
  const response = await apiClient.post<UserResponse>('/api/v1/auth/register', payload)
  return response.data
}

export async function login(payload: LoginRequest): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>('/api/v1/auth/login', payload)
  return response.data
}

export async function refresh(payload: RefreshRequest): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>('/api/v1/auth/refresh', payload)
  return response.data
}

export async function logout(payload: RefreshRequest): Promise<void> {
  await apiClient.post('/api/v1/auth/logout', payload)
}
