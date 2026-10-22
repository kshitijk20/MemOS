import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiErrorResponse, AuthResponse } from './types'

const REFRESH_TOKEN_KEY = 'memos.refreshToken'

let accessToken: string | null = null
let authFailureHandler: (() => void) | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setRefreshToken(token: string | null): void {
  if (token) {
    localStorage.setItem(REFRESH_TOKEN_KEY, token)
  } else {
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  }
}

export function setAuthFailureHandler(handler: () => void): void {
  authFailureHandler = handler
}

/**
 * Backend origin. Empty in production builds, where the app and the API share one domain behind
 * a reverse proxy, so requests go to relative /api/... paths.
 */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? ''

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
})

apiClient.interceptors.request.use((config) => {
  if (accessToken && !config.url?.startsWith('/api/v1/auth/')) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return config
})

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

let refreshPromise: Promise<AuthResponse> | null = null

async function refreshTokens(): Promise<AuthResponse> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error('No refresh token available')
  }
  const response = await axios.post<AuthResponse>(
    `${API_BASE_URL}/api/v1/auth/refresh`,
    { refreshToken },
  )
  return response.data
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetryableRequestConfig | undefined

    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !originalRequest.url?.startsWith('/api/v1/auth/')
    ) {
      originalRequest._retry = true
      try {
        refreshPromise ??= refreshTokens()
        const tokens = await refreshPromise
        refreshPromise = null
        setAccessToken(tokens.accessToken)
        setRefreshToken(tokens.refreshToken)
        originalRequest.headers.set('Authorization', `Bearer ${tokens.accessToken}`)
        return apiClient(originalRequest)
      } catch (refreshError) {
        refreshPromise = null
        setAccessToken(null)
        setRefreshToken(null)
        authFailureHandler?.()
        return Promise.reject(refreshError)
      }
    }

    return Promise.reject(error)
  },
)

export function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as ApiErrorResponse | undefined
    if (data?.message) {
      return data.message
    }
    if (error.message) {
      return error.message
    }
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'An unexpected error occurred'
}
