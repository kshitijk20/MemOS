import { useEffect, useState, type ReactNode } from 'react'
import { jwtDecode } from 'jwt-decode'
import * as authApi from '@/api/auth'
import {
  getRefreshToken,
  setAccessToken,
  setAuthFailureHandler,
  setRefreshToken,
} from '@/api/client'
import type { AuthResponse, UserRole } from '@/api/types'
import { AuthContext, type AuthUser } from '@/lib/auth-context-base'

interface JwtPayload {
  sub: string
  userId: string
  role: UserRole
  exp: number
}

function userFromTokens(tokens: AuthResponse, fullName?: string): AuthUser {
  const payload = jwtDecode<JwtPayload>(tokens.accessToken)
  return {
    id: payload.userId,
    email: payload.sub,
    role: payload.role,
    fullName,
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [isLoading, setIsLoading] = useState(() => getRefreshToken() !== null)

  useEffect(() => {
    setAuthFailureHandler(() => {
      setUser(null)
    })

    const existingRefreshToken = getRefreshToken()
    if (!existingRefreshToken) {
      return
    }

    authApi
      .refresh({ refreshToken: existingRefreshToken })
      .then((tokens) => {
        setAccessToken(tokens.accessToken)
        setRefreshToken(tokens.refreshToken)
        setUser(userFromTokens(tokens))
      })
      .catch(() => {
        setAccessToken(null)
        setRefreshToken(null)
        setUser(null)
      })
      .finally(() => setIsLoading(false))
  }, [])

  async function login(email: string, password: string) {
    const tokens = await authApi.login({ email, password })
    setAccessToken(tokens.accessToken)
    setRefreshToken(tokens.refreshToken)
    setUser(userFromTokens(tokens))
  }

  async function register(email: string, password: string, fullName: string) {
    await authApi.register({ email, password, fullName })
    const tokens = await authApi.login({ email, password })
    setAccessToken(tokens.accessToken)
    setRefreshToken(tokens.refreshToken)
    setUser(userFromTokens(tokens, fullName))
  }

  async function logout() {
    const refreshToken = getRefreshToken()
    if (refreshToken) {
      try {
        await authApi.logout({ refreshToken })
      } catch {
        // best-effort
      }
    }
    setAccessToken(null)
    setRefreshToken(null)
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: user !== null, isLoading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}
