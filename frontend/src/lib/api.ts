import type {
  ApiErrorPayload,
  AuthResponse,
  CsrfResponse,
} from '@/types/api'

const configuredBase = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''
const API_ORIGIN = configuredBase.replace(/\/+$/, '')
const API_ROOT = `${API_ORIGIN}/api/v1`

let accessToken: string | null = null
let csrfToken: CsrfResponse | null = null
let refreshPromise: Promise<AuthResponse> | null = null
let sessionExpiredHandler: (() => void) | null = null

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: { field: string; message: string }[]

  constructor(
    message: string,
    status = 0,
    code = 'NETWORK_ERROR',
    fieldErrors: { field: string; message: string }[] = [],
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  auth?: boolean
}

function normalizeNetworkError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (error instanceof DOMException && error.name === 'AbortError') {
    return new ApiError('The request was cancelled.', 0, 'ABORTED')
  }
  return new ApiError(
    'We could not reach Bookwell. Check your connection and try again.',
    0,
    'NETWORK_ERROR',
  )
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T
  const text = await response.text()
  let payload: unknown = null
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch {
      payload = null
    }
  }
  if (!response.ok) {
    const error = payload as ApiErrorPayload | null
    throw new ApiError(
      error?.message ?? `Request failed with status ${response.status}.`,
      response.status,
      error?.code ?? 'REQUEST_FAILED',
      error?.fieldErrors ?? [],
    )
  }
  return payload as T
}

async function fetchCsrf(force = false): Promise<CsrfResponse> {
  if (csrfToken && !force) return csrfToken
  try {
    const response = await fetch(`${API_ROOT}/auth/csrf`, {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    csrfToken = await parseResponse<CsrfResponse>(response)
    return csrfToken
  } catch (error) {
    throw normalizeNetworkError(error)
  }
}

async function rawRefresh(): Promise<AuthResponse> {
  const csrf = await fetchCsrf()
  const response = await fetch(`${API_ROOT}/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      [csrf.headerName]: csrf.token,
    },
  })
  if (response.status === 403) {
    csrfToken = null
    const retriedCsrf = await fetchCsrf(true)
    const retried = await fetch(`${API_ROOT}/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        [retriedCsrf.headerName]: retriedCsrf.token,
      },
    })
    const auth = await parseResponse<AuthResponse>(retried)
    accessToken = auth.accessToken
    return auth
  }
  const auth = await parseResponse<AuthResponse>(response)
  accessToken = auth.accessToken
  return auth
}

export function refreshSession(): Promise<AuthResponse> {
  if (!refreshPromise) {
    refreshPromise = rawRefresh()
      .catch((error: unknown) => {
        accessToken = null
        throw normalizeNetworkError(error)
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {},
  canRefresh = true,
): Promise<T> {
  const { body, auth = true, headers: providedHeaders, ...init } = options
  const headers = new Headers(providedHeaders)
  headers.set('Accept', 'application/json')
  if (body !== undefined) headers.set('Content-Type', 'application/json')
  if (auth && accessToken) headers.set('Authorization', `Bearer ${accessToken}`)

  try {
    const response = await fetch(`${API_ROOT}${path}`, {
      ...init,
      credentials: 'include',
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })

    if (response.status === 401 && auth && canRefresh) {
      try {
        await refreshSession()
      } catch (refreshError) {
        sessionExpiredHandler?.()
        throw normalizeNetworkError(refreshError)
      }
      return apiRequest<T>(path, options, false)
    }

    return parseResponse<T>(response)
  } catch (error) {
    throw normalizeNetworkError(error)
  }
}

async function cookieBackedRequest(path: string, canRetry = true): Promise<void> {
  try {
    const csrf = await fetchCsrf()
    const response = await fetch(`${API_ROOT}${path}`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        [csrf.headerName]: csrf.token,
      },
    })
    if (response.status === 403 && canRetry) {
      csrfToken = null
      return cookieBackedRequest(path, false)
    }
    await parseResponse<void>(response)
  } catch (error) {
    throw normalizeNetworkError(error)
  }
}

export async function loginRequest(email: string, password: string): Promise<AuthResponse> {
  const auth = await apiRequest<AuthResponse>(
    '/auth/login',
    { method: 'POST', auth: false, body: { email, password } },
    false,
  )
  accessToken = auth.accessToken
  return auth
}

export async function registerCustomerRequest(payload: {
  email: string
  password: string
  fullName: string
  phone?: string
}): Promise<AuthResponse> {
  const auth = await apiRequest<AuthResponse>(
    '/auth/register',
    { method: 'POST', auth: false, body: payload },
    false,
  )
  accessToken = auth.accessToken
  return auth
}

export async function registerProviderRequest(payload: {
  email: string
  password: string
  fullName: string
  phone?: string
  businessName: string
  displayName: string
  category: string
  city?: string
  timeZone?: string
}): Promise<AuthResponse> {
  const auth = await apiRequest<AuthResponse>(
    '/auth/register/provider',
    { method: 'POST', auth: false, body: payload },
    false,
  )
  accessToken = auth.accessToken
  return auth
}

export async function logoutRequest(): Promise<void> {
  try {
    await cookieBackedRequest('/auth/logout')
  } finally {
    accessToken = null
  }
}

export async function verifyEmailRequest(token: string): Promise<void> {
  await apiRequest<void>('/auth/verify-email', {
    method: 'POST',
    auth: false,
    body: { token },
  })
}

export async function resendVerificationRequest(): Promise<void> {
  await apiRequest<void>('/auth/verify-email/resend', {
    method: 'POST',
    auth: true,
  })
}

export function clearSession(): void {
  accessToken = null
}

export function setSessionExpiredHandler(handler: (() => void) | null): void {
  sessionExpiredHandler = handler
}

export function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return 'Something went wrong. Please try again.'
}

export function getFieldErrors(error: unknown): { field: string; message: string }[] {
  return error instanceof ApiError ? error.fieldErrors : []
}

export { API_ROOT }
