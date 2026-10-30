import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { FullPageLoader } from '@/components/ui'
import type { Role } from '@/types/api'

export function RequireAuth({
  children,
  roles,
}: {
  children: ReactNode
  roles?: Role[]
}) {
  const { user, initializing } = useAuth()
  const location = useLocation()

  if (initializing) return <FullPageLoader label="Opening your account" />

  if (!user) {
    const redirect = `${location.pathname}${location.search}`
    return <Navigate to={`/login?redirect=${encodeURIComponent(redirect)}`} replace />
  }

  if (roles && !roles.includes(user.role)) {
    return <Navigate to="/forbidden" replace />
  }

  return <>{children}</>
}
