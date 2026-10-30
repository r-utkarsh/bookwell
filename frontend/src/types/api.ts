export type Role = 'CUSTOMER' | 'PROVIDER' | 'ADMIN'
export type ProviderStatus = 'PENDING' | 'APPROVED' | 'SUSPENDED'
export type AppointmentStatus = 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW'
export type AppointmentActor = 'CUSTOMER' | 'PROVIDER' | 'ADMIN' | 'SYSTEM'
export type ScheduleExceptionType = 'CLOSED' | 'BLOCKED' | 'EXTRA_AVAILABILITY'
export type NotificationStatus = 'PENDING' | 'SENDING' | 'SENT' | 'FAILED' | 'SKIPPED'
export type NotificationType =
  | 'BOOKING_CONFIRMATION'
  | 'CANCELLATION'
  | 'RESCHEDULE'
  | 'REMINDER'

export interface UserSummary {
  id: string
  email: string
  fullName: string
  role: Role
  phone?: string | null
  enabled: boolean
  emailVerified: boolean
  providerId?: string | null
  providerStatus?: ProviderStatus | null
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: UserSummary
}

export interface CsrfResponse {
  headerName: string
  token: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface ApiErrorPayload {
  timestamp?: string
  status: number
  code: string
  message: string
  path?: string
  fieldErrors?: { field: string; message: string }[] | null
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  fullName: string
  phone?: string
}

export interface ProviderRegisterRequest extends RegisterRequest {
  businessName: string
  displayName: string
  category: string
  city?: string
  timeZone?: string
}

export interface ProviderCard {
  id: string
  displayName: string
  businessName: string
  category: string
  description?: string | null
  city?: string | null
  address?: string | null
  phone?: string | null
  timeZone: string
  currency: string
  featured: boolean
  status: ProviderStatus
  serviceCount: number
  startingPrice?: number | null
}

export interface ServiceOffering {
  id: string
  name: string
  description?: string | null
  durationMinutes: number
  price: number
  currency: string
  active: boolean
  sortOrder: number
}

export interface WeeklyHour {
  id: string
  dayOfWeek: number
  startTime: string
  endTime: string
}

export interface ProviderDetail {
  provider: ProviderCard
  services: ServiceOffering[]
  weeklyHours: WeeklyHour[]
}

export interface ProviderDashboardProfile {
  id: string
  userId: string
  ownerName: string
  ownerEmail: string
  displayName: string
  businessName: string
  category: string
  description?: string | null
  city?: string | null
  address?: string | null
  phone?: string | null
  timeZone: string
  currency: string
  status: ProviderStatus
  featured: boolean
  createdAt: string
}

export interface ProviderDashboardStats {
  upcomingAppointments: number
  todaysAppointments: number
  activeServices: number
  completedAppointments: number
}

export interface AvailabilitySlot {
  startAt: string
  endAt: string
  label: string
}

export interface AvailabilityResponse {
  providerId: string
  serviceId: string
  serviceName: string
  date: string
  timeZone: string
  durationMinutes: number
  price: number
  currency: string
  slots: AvailabilitySlot[]
  message?: string | null
}

export interface Appointment {
  id: string
  status: AppointmentStatus
  startAt: string
  endAt: string
  createdAt: string
  providerId: string
  providerDisplayName: string
  providerBusinessName: string
  providerTimeZone: string
  providerCity?: string | null
  providerAddress?: string | null
  serviceId: string
  serviceName: string
  serviceDurationMinutes: number
  servicePrice: number
  currency: string
  customerId: string
  customerName: string
  customerEmail: string
  customerPhone?: string | null
  customerNotes?: string | null
  cancellationReason?: string | null
  cancelledAt?: string | null
  cancelledBy?: AppointmentActor | null
  rescheduledFromAt?: string | null
  rescheduleCount: number
  canCancel: boolean
  canReschedule: boolean
}

export interface ScheduleOverview {
  weeklyHours: WeeklyHour[]
  upcomingExceptions: ScheduleException[]
  timeZone: string
}

export interface ScheduleException {
  id: string
  date: string
  type: ScheduleExceptionType
  startTime?: string | null
  endTime?: string | null
  reason?: string | null
}

export interface AdminPlatformStats {
  totalCustomers: number
  totalProviders: number
  pendingProviders: number
  approvedProviders: number
  suspendedProviders: number
  totalBookings: number
  upcomingConfirmedBookings: number
  pendingNotifications: number
  failedNotifications: number
  sentNotifications: number
}

export interface AdminProvider {
  id: string
  ownerId: string
  ownerName: string
  ownerEmail: string
  displayName: string
  businessName: string
  category: string
  city?: string | null
  status: ProviderStatus
  featured: boolean
  serviceCount: number
  createdAt: string
}

export interface AdminCustomer {
  id: string
  email: string
  fullName: string
  phone?: string | null
  enabled: boolean
  createdAt: string
}

export interface AdminNotification {
  id: string
  appointmentId: string
  type: NotificationType
  recipientEmail: string
  status: NotificationStatus
  attempts: number
  maxAttempts: number
  scheduledAt: string
  sentAt?: string | null
  lastError?: string | null
  deliveryMode?: string | null
  createdAt: string
}
