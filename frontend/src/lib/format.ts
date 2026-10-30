import { addDays, format, parseISO } from 'date-fns'

export function formatCurrency(amount: number | string | null | undefined, currency = 'INR'): string {
  if (amount === null || amount === undefined || amount === '') return '—'
  const numeric = typeof amount === 'string' ? Number(amount) : amount
  if (Number.isNaN(numeric)) return '—'
  try {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: currency || 'INR',
      maximumFractionDigits: numeric % 1 === 0 ? 0 : 2,
    }).format(numeric)
  } catch {
    return `${currency} ${numeric.toFixed(2)}`
  }
}

export function formatDateTime(iso: string, timeZone?: string): string {
  try {
    return new Intl.DateTimeFormat('en-IN', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
      timeZone: timeZone || undefined,
    }).format(new Date(iso))
  } catch {
    return new Intl.DateTimeFormat('en-IN', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    }).format(new Date(iso))
  }
}

export function formatDate(iso: string, timeZone?: string): string {
  try {
    return new Intl.DateTimeFormat('en-IN', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: timeZone || undefined,
    }).format(new Date(iso))
  } catch {
    return new Intl.DateTimeFormat('en-IN', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    }).format(new Date(iso))
  }
}

export function formatTime(iso: string, timeZone?: string): string {
  try {
    return new Intl.DateTimeFormat('en-IN', {
      hour: 'numeric',
      minute: '2-digit',
      timeZone: timeZone || undefined,
    }).format(new Date(iso))
  } catch {
    return new Intl.DateTimeFormat('en-IN', {
      hour: 'numeric',
      minute: '2-digit',
    }).format(new Date(iso))
  }
}

export function formatDateOnly(dateOnly: string): string {
  try {
    return format(parseISO(dateOnly), 'EEE, d MMM')
  } catch {
    return dateOnly
  }
}

export function formatLongDateOnly(dateOnly: string): string {
  try {
    return format(parseISO(dateOnly), 'EEEE, d MMMM yyyy')
  } catch {
    return dateOnly
  }
}

export function zonedToday(timeZone: string): string {
  try {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).formatToParts(new Date())
    const year = parts.find((part) => part.type === 'year')?.value
    const month = parts.find((part) => part.type === 'month')?.value
    const day = parts.find((part) => part.type === 'day')?.value
    if (year && month && day) return `${year}-${month}-${day}`
  } catch {
    // Fall back to the browser's local date below.
  }
  return format(new Date(), 'yyyy-MM-dd')
}

export function addDaysToDate(dateOnly: string, days: number): string {
  return format(addDays(parseISO(dateOnly), days), 'yyyy-MM-dd')
}

export function weeklyScheduleDates(timeZone: string, count = 21): string[] {
  const start = zonedToday(timeZone)
  return Array.from({ length: count }, (_, index) => addDaysToDate(start, index))
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
}

export function titleCase(value: string): string {
  return value
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase())
}

export function durationLabel(minutes: number): string {
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours} hr` : `${hours} hr ${rest} min`
}

export function dayName(dayOfWeek: number): string {
  return ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][dayOfWeek] ?? ''
}

export function shortDayName(dayOfWeek: number): string {
  return ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][dayOfWeek] ?? ''
}

export function timeFromClock(value: string | null | undefined): string {
  if (!value) return ''
  return value.slice(0, 5)
}

export function timezoneOptions(): string[] {
  const fallback = [
    'Asia/Kolkata',
    'Asia/Dubai',
    'Asia/Singapore',
    'Europe/London',
    'Europe/Paris',
    'America/New_York',
    'America/Chicago',
    'America/Los_Angeles',
    'Australia/Sydney',
  ]
  const supported = (
    Intl as typeof Intl & { supportedValuesOf?: (key: string) => string[] }
  ).supportedValuesOf?.('timeZone')
  return supported && supported.length > 0 ? supported : fallback
}

export function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Kolkata'
  } catch {
    return 'Asia/Kolkata'
  }
}
