import type { Role, UserSummary } from '@/types/api'

export function roleHome(user: Pick<UserSummary, 'role'> | null | undefined): string {
  if (!user) return '/'
  return roleHomeForRole(user.role)
}

export function roleHomeForRole(role: Role): string {
  switch (role) {
    case 'PROVIDER':
      return '/provider'
    case 'ADMIN':
      return '/admin'
    default:
      return '/account'
  }
}
