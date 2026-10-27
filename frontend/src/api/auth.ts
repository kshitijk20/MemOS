import { apiClient } from './client'
import type { AuthResponse, LoginRequest, RefreshRequest, RegisterRequest, RegistrationPolicy, VerifySignupRequest } from './types'

/** Emails a verification code. Always resolves the same way whether or not the account exists. */
export async function register(payload: RegisterRequest): Promise<void> {
  await apiClient.post('/api/v1/auth/register', payload)
}

/** Creates the account from the emailed code; returns tokens, so the user is signed in. */
export async function verifyRegistration(payload: VerifySignupRequest): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>('/api/v1/auth/register/verify', payload)
  return response.data
}

export async function getRegistrationPolicy(): Promise<RegistrationPolicy> {
  const response = await apiClient.get<RegistrationPolicy>('/api/v1/auth/registration')
  return response.data
}

/** Always resolves the same way whether or not the account exists. */
export async function requestPasswordReset(email: string): Promise<void> {
  await apiClient.post('/api/v1/auth/password-reset/request', { email })
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  await apiClient.post('/api/v1/auth/password-reset/confirm', { token, newPassword })
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
