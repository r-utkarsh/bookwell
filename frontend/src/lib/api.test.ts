import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthResponse } from '@/types/api'

type ApiModule = typeof import('@/lib/api')

const user = {
  id: 'user-1',
  email: 'riya@example.com',
  fullName: 'Riya Kapoor',
  role: 'CUSTOMER' as const,
  enabled: true,
  emailVerified: true,
}

function authResponse(accessToken: string): AuthResponse {
  return {
    accessToken,
    tokenType: 'Bearer',
    expiresIn: 900,
    user,
  }
}

function json(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function authorization(call: unknown[]): string | null {
  const init = call[1] as RequestInit | undefined
  return new Headers(init?.headers).get('Authorization')
}

let api: ApiModule
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(async () => {
  vi.resetModules()
  api = await import('@/lib/api')
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  api.setSessionExpiredHandler(null)
  api.clearSession()
  vi.unstubAllGlobals()
})

describe('apiRequest', () => {
  it('sends unauthenticated public requests without a bearer token', async () => {
    fetchMock.mockResolvedValueOnce(json({ status: 'ok' }))

    await expect(api.apiRequest('/health', { auth: false })).resolves.toEqual({ status: 'ok' })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/health')
    expect(init.credentials).toBe('include')
    expect(authorization(fetchMock.mock.calls[0])).toBeNull()
  })

  it('maps API error payloads into a typed ApiError', async () => {
    fetchMock.mockResolvedValueOnce(
      json(
        {
          status: 409,
          code: 'BOOKING_CONFLICT',
          message: 'That time was just booked by someone else.',
          fieldErrors: [{ field: 'startAt', message: 'No longer available.' }],
        },
        409,
      ),
    )

    const error = await api.apiRequest('/appointments').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(api.ApiError)
    expect(error).toMatchObject({
      status: 409,
      code: 'BOOKING_CONFLICT',
      message: 'That time was just booked by someone else.',
      fieldErrors: [{ field: 'startAt', message: 'No longer available.' }],
    })
  })

  it('turns transport failures into a network ApiError', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))

    await expect(api.apiRequest('/providers')).rejects.toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR',
    })
  })
})

describe('authentication flow', () => {
  it('stores the access token after login and attaches it to later requests', async () => {
    fetchMock
      .mockResolvedValueOnce(json(authResponse('access-token-1')))
      .mockResolvedValueOnce(json({ id: 'appointment-1' }))

    const auth = await api.loginRequest('riya@example.com', 'Secret!123')
    expect(auth.accessToken).toBe('access-token-1')

    await api.apiRequest('/account/appointments')

    expect(authorization(fetchMock.mock.calls[1])).toBe('Bearer access-token-1')
  })

  it('refreshes once on a 401 and retries the original request with the new token', async () => {
    let protectedCalls = 0
    fetchMock.mockImplementation((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/account/appointments')) {
        protectedCalls += 1
        return Promise.resolve(
          protectedCalls === 1
            ? json({ status: 401, code: 'UNAUTHORIZED', message: 'Token expired.' }, 401)
            : json({ id: 'appointment-1' }),
        )
      }
      if (url.endsWith('/auth/csrf')) {
        return Promise.resolve(json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }))
      }
      if (url.endsWith('/auth/refresh')) {
        return Promise.resolve(json(authResponse('access-token-refreshed')))
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`))
    })

    await expect(api.apiRequest('/account/appointments')).resolves.toEqual({ id: 'appointment-1' })

    expect(protectedCalls).toBe(2)
    expect(authorization(fetchMock.mock.calls.at(-1)!)).toBe('Bearer access-token-refreshed')
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/auth/refresh'))).toHaveLength(1)
  })

  it('notifies the app when the refresh token is rejected', async () => {
    const expired = vi.fn()
    api.setSessionExpiredHandler(expired)

    fetchMock.mockImplementation((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/auth/csrf')) {
        return Promise.resolve(json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }))
      }
      if (url.endsWith('/auth/refresh')) {
        return Promise.resolve(
          json({ status: 401, code: 'INVALID_REFRESH_TOKEN', message: 'Session expired.' }, 401),
        )
      }
      return Promise.resolve(
        json({ status: 401, code: 'UNAUTHORIZED', message: 'Token expired.' }, 401),
      )
    })

    await expect(api.apiRequest('/account/appointments')).rejects.toMatchObject({ status: 401 })
    expect(expired).toHaveBeenCalledTimes(1)
  })

  it('sends CSRF on logout and clears the in-memory token', async () => {
    fetchMock
      .mockResolvedValueOnce(json(authResponse('access-token-1')))
      .mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'csrf-logout' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(json({ status: 'ok' }))

    await api.loginRequest('riya@example.com', 'Secret!123')
    await api.logoutRequest()

    const logoutCall = fetchMock.mock.calls[2] as [string, RequestInit]
    expect(logoutCall[0]).toBe('/api/v1/auth/logout')
    expect(new Headers(logoutCall[1].headers).get('X-XSRF-TOKEN')).toBe('csrf-logout')

    await api.apiRequest('/health', { auth: false })
    expect(authorization(fetchMock.mock.calls[3])).toBeNull()
  })
})
