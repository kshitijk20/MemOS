import { createContext } from 'react'
import type { UserRole } from '@/api/types'

export interface AuthUser {
  id: string
  email: string
  fullName?: string
  role: UserRole
}

export interface AuthContextValue {
  user: AuthUser | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (email: string, password: string) => Promise<void>
  /** Step 1 of sign-up: emails a verification code. */
  register: (email: string, password: string, fullName: string) => Promise<void>
  /** Step 2 of sign-up: creates the account from the code and signs in. */
  verifyRegistration: (email: string, code: string, fullName: string) => Promise<void>
  logout: () => Promise<void>
  /** Signs out on every device, then here. */
  logoutEverywhere: () => Promise<void>
  updateUser: (patch: Partial<AuthUser>) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
