import { describe, expect, it } from 'vitest'
import { roleHome, roleHomeForRole } from '@/lib/roles'

describe('roleHome', () => {
  it('routes each role to its own home', () => {
    expect(roleHomeForRole('CUSTOMER')).toBe('/account')
    expect(roleHomeForRole('PROVIDER')).toBe('/provider')
    expect(roleHomeForRole('ADMIN')).toBe('/admin')
  })

  it('falls back to the public home when there is no user', () => {
    expect(roleHome(null)).toBe('/')
    expect(roleHome(undefined)).toBe('/')
    expect(roleHome({ role: 'PROVIDER' })).toBe('/provider')
  })
})
