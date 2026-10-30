import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  clearSession,
  loginRequest,
  logoutRequest,
  refreshSession,
  registerCustomerRequest,
  registerProviderRequest,
  setSessionExpiredHandler,
  type ApiError,
} from '@/lib/api'
import { queryClient } from '@/lib/queryClient'
import type { AuthResponse, UserSummary } from '@/types/api'

interface AuthContextValue {
  user: UserSummary | null
  initializing: boolean
  login: (email: string, password: string) => Promise<UserSummary>
  registerCustomer: (payload: {
    email: string
    password: string
    fullName: string
    phone?: string
  }) => Promise<UserSummary>
  registerProvider: (payload: {
    email: string
    password: string
    fullName: string
    phone?: string
    businessName: string
    displayName: string
    category: string
    city?: string
    timeZone?: string
  }) => Promise<UserSummary>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function applyAuth(auth: AuthResponse): UserSummary {
  return auth.user
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null)
  const [initializing, setInitializing] = useState(true)

  useEffect(() => {
    let active = true
    setSessionExpiredHandler(() => {
      setUser(null)
      queryClient.clear()
    })

    refreshSession()
      .then((auth) => {
        if (active) setUser(applyAuth(auth))
      })
      .catch(() => {
        if (active) setUser(null)
      })
      .finally(() => {
        if (active) setInitializing(false)
      })

    return () => {
      active = false
      setSessionExpiredHandler(null)
    }
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    try {
      const auth = await loginRequest(email, password)
      setUser(auth.user)
      queryClient.clear()
      return auth.user
    } catch (error) {
      throw error as ApiError
    }
  }, [])

  const registerCustomer = useCallback(
    async (payload: {
      email: string
      password: string
      fullName: string
      phone?: string
    }) => {
      const auth = await registerCustomerRequest(payload)
      setUser(auth.user)
      queryClient.clear()
      return auth.user
    },
    [],
  )

  const registerProvider = useCallback(
    async (payload: {
      email: string
      password: string
      fullName: string
      phone?: string
      businessName: string
      displayName: string
      category: string
      city?: string
      timeZone?: string
    }) => {
      const auth = await registerProviderRequest(payload)
      setUser(auth.user)
      queryClient.clear()
      return auth.user
    },
    [],
  )

  const logout = useCallback(async () => {
    try {
      await logoutRequest()
    } finally {
      clearSession()
      setUser(null)
      queryClient.clear()
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ user, initializing, login, registerCustomer, registerProvider, logout }),
    [user, initializing, login, registerCustomer, registerProvider, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
