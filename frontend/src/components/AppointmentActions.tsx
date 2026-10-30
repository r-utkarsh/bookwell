import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarClock, Check, LoaderCircle, X } from 'lucide-react'
import { useState } from 'react'
import { format, parseISO } from 'date-fns'
import { toast } from 'sonner'
import {
  Alert,
  Button,
  Modal,
  Spinner,
  Textarea,
} from '@/components/ui'
import { apiRequest, ApiError, getErrorMessage } from '@/lib/api'
import { cn } from '@/lib/cn'
import {
  addDaysToDate,
  formatDate,
  formatDateOnly,
  zonedToday,
} from '@/lib/format'
import type { Appointment, AvailabilityResponse } from '@/types/api'

export function AppointmentActions({
  appointment,
  onChanged,
  className,
}: {
  appointment: Appointment
  onChanged?: () => void
  className?: string
}) {
  const queryClient = useQueryClient()
  const [cancelOpen, setCancelOpen] = useState(false)
  const [rescheduleOpen, setRescheduleOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const today = zonedToday(appointment.providerTimeZone)
  const [selectedDate, setSelectedDate] = useState(today)
  const [selectedStart, setSelectedStart] = useState<string | null>(null)
  const [dateWindowStart, setDateWindowStart] = useState(today)

  const dates = Array.from({ length: 14 }, (_, index) =>
    addDaysToDate(dateWindowStart, index),
  )

  const availability = useQuery({
    queryKey: ['availability', appointment.providerId, appointment.serviceId, selectedDate],
    queryFn: () =>
      apiRequest<AvailabilityResponse>(
        `/providers/${appointment.providerId}/availability?serviceId=${appointment.serviceId}&date=${selectedDate}`,
      ),
    enabled: rescheduleOpen,
    staleTime: 0,
  })

  function refreshRelated() {
    queryClient.invalidateQueries({ queryKey: ['customer-appointments'] })
    queryClient.invalidateQueries({ queryKey: ['availability'] })
    queryClient.invalidateQueries({ queryKey: ['admin-bookings'] })
    queryClient.invalidateQueries({ queryKey: ['provider-calendar'] })
    onChanged?.()
  }

  const cancelMutation = useMutation({
    mutationFn: () =>
      apiRequest<Appointment>(`/appointments/${appointment.id}/cancel`, {
        method: 'POST',
        body: { reason: reason.trim() || null },
      }),
    onSuccess: () => {
      toast.success('Appointment cancelled. A confirmation email is on its way.')
      setCancelOpen(false)
      setReason('')
      setError(null)
      refreshRelated()
    },
    onError: (mutationError) => setError(getErrorMessage(mutationError)),
  })

  const rescheduleMutation = useMutation({
    mutationFn: () =>
      apiRequest<Appointment>(`/appointments/${appointment.id}/reschedule`, {
        method: 'POST',
        body: { startAt: selectedStart },
      }),
    onSuccess: () => {
      toast.success('Appointment moved. Check your email for the new time.')
      setRescheduleOpen(false)
      setSelectedStart(null)
      setError(null)
      refreshRelated()
    },
    onError: (mutationError) => {
      setError(getErrorMessage(mutationError))
      if (mutationError instanceof ApiError && mutationError.status === 409) {
        availability.refetch()
      }
    },
  })

  function openReschedule() {
    setError(null)
    setSelectedStart(null)
    const newToday = zonedToday(appointment.providerTimeZone)
    setSelectedDate(newToday)
    setDateWindowStart(newToday)
    setRescheduleOpen(true)
  }

  function changeDate(date: string) {
    setSelectedDate(date)
    setSelectedStart(null)
    setError(null)
  }

  if (!appointment.canCancel && !appointment.canReschedule) return null

  return (
    <>
      <div className={cn('flex flex-wrap gap-2', className)}>
        {appointment.canReschedule && (
          <Button variant="outline" size="sm" onClick={openReschedule}>
            <CalendarClock className="size-3.5" /> Reschedule
          </Button>
        )}
        {appointment.canCancel && (
          <Button
            variant="ghost"
            size="sm"
            className="text-red-700 hover:bg-red-50"
            onClick={() => {
              setError(null)
              setCancelOpen(true)
            }}
          >
            <X className="size-3.5" /> Cancel
          </Button>
        )}
      </div>

      <Modal
        open={cancelOpen}
        onOpenChange={(open) => {
          setCancelOpen(open)
          if (!open) {
            setError(null)
            setReason('')
          }
        }}
        title="Cancel this appointment?"
        description={`${appointment.serviceName} with ${appointment.providerDisplayName} on ${formatDate(
          appointment.startAt,
          appointment.providerTimeZone,
        )}.`}
        footer={
          <>
            <Button variant="ghost" onClick={() => setCancelOpen(false)} disabled={cancelMutation.isPending}>
              Keep appointment
            </Button>
            <Button
              variant="danger"
              onClick={() => cancelMutation.mutate()}
              loading={cancelMutation.isPending}
            >
              Cancel appointment
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {error && <Alert tone="error">{error}</Alert>}
          <p className="text-sm text-ink-soft">
            This slot will be released for someone else. If you are inside the provider&apos;s change window, the
            platform will let you know before anything changes.
          </p>
          <div>
            <label htmlFor="cancel-reason" className="mb-1.5 block text-xs font-bold uppercase tracking-[0.12em] text-forest-800">
              Reason <span className="font-medium normal-case tracking-normal text-ink-muted">(optional)</span>
            </label>
            <Textarea
              id="cancel-reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={500}
              rows={3}
              placeholder="Let the provider know if you’d like."
            />
          </div>
        </div>
      </Modal>

      <Modal
        open={rescheduleOpen}
        onOpenChange={(open) => {
          setRescheduleOpen(open)
          if (!open) {
            setError(null)
            setSelectedStart(null)
          }
        }}
        title="Find a new time"
        description={`${appointment.serviceName} · ${appointment.serviceDurationMinutes} minutes with ${appointment.providerDisplayName}`}
        size="lg"
        footer={
          <>
            <Button variant="ghost" onClick={() => setRescheduleOpen(false)} disabled={rescheduleMutation.isPending}>
              Keep current time
            </Button>
            <Button
              onClick={() => rescheduleMutation.mutate()}
              loading={rescheduleMutation.isPending}
              disabled={!selectedStart}
            >
              Move appointment
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {error && <Alert tone="error">{error}</Alert>}
          <div className="flex items-center justify-between">
            <p className="text-xs font-bold uppercase tracking-[0.12em] text-ink-muted">Choose a date</p>
            <div className="flex gap-1">
              <button
                type="button"
                className="rounded-full border border-line px-2.5 py-1 text-[0.65rem] font-bold text-forest-800 disabled:opacity-40"
                onClick={() => {
                  setDateWindowStart(addDaysToDate(dateWindowStart, -7))
                  setSelectedStart(null)
                }}
                disabled={dateWindowStart <= today}
              >
                Earlier
              </button>
              <button
                type="button"
                className="rounded-full border border-line px-2.5 py-1 text-[0.65rem] font-bold text-forest-800"
                onClick={() => {
                  setDateWindowStart(addDaysToDate(dateWindowStart, 7))
                  setSelectedStart(null)
                }}
              >
                Later
              </button>
            </div>
          </div>
          <div className="no-scrollbar flex gap-2 overflow-x-auto pb-1">
            {dates.map((date) => {
              const active = date === selectedDate
              return (
                <button
                  key={date}
                  type="button"
                  onClick={() => changeDate(date)}
                  aria-pressed={active}
                  className={cn(
                    'flex min-w-[4.1rem] shrink-0 flex-col items-center rounded-xl border px-2 py-2.5 transition',
                    active
                      ? 'border-forest-900 bg-forest-900 text-cream-50'
                      : 'border-line bg-white/60 text-forest-900 hover:border-forest-300',
                  )}
                >
                  <span className="text-[0.58rem] font-bold uppercase tracking-wider opacity-70">
                    {format(parseISO(date), 'EEE')}
                  </span>
                  <span className="font-display text-xl">{format(parseISO(date), 'd')}</span>
                  <span className="text-[0.58rem] uppercase opacity-70">{format(parseISO(date), 'MMM')}</span>
                </button>
              )
            })}
          </div>

          <div className="min-h-32 rounded-2xl border border-line bg-cream-50 p-4">
            {availability.isLoading ? (
              <div className="flex min-h-24 items-center justify-center gap-2 text-sm text-ink-muted">
                <Spinner /> Checking the calendar…
              </div>
            ) : availability.isError ? (
              <div className="flex min-h-24 flex-col items-center justify-center gap-2 text-center">
                <p className="text-sm text-red-700">{getErrorMessage(availability.error)}</p>
                <Button variant="outline" size="sm" onClick={() => availability.refetch()}>
                  <LoaderCircle className="size-3.5" /> Try again
                </Button>
              </div>
            ) : availability.data?.slots.length ? (
              <>
                <p className="mb-3 text-[0.62rem] font-bold uppercase tracking-[0.14em] text-ink-muted">
                  Available times · {availability.data.timeZone.replace('_', ' ')}
                </p>
                <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
                  {availability.data.slots.map((slot) => (
                    <button
                      key={slot.startAt}
                      type="button"
                      onClick={() => setSelectedStart(slot.startAt)}
                      aria-pressed={selectedStart === slot.startAt}
                      className={cn(
                        'rounded-xl border px-2 py-2.5 text-xs font-bold transition',
                        selectedStart === slot.startAt
                          ? 'border-forest-900 bg-forest-900 text-cream-50'
                          : 'border-line bg-white text-forest-800 hover:border-forest-300',
                      )}
                    >
                      {slot.label}
                    </button>
                  ))}
                </div>
              </>
            ) : (
              <p className="flex min-h-24 items-center justify-center text-center text-sm text-ink-muted">
                {availability.data?.message ?? 'No times available on this date. Try another day.'}
              </p>
            )}
          </div>
          {selectedStart && (
            <p className="flex items-center gap-2 text-xs font-semibold text-forest-700">
              <Check className="size-4" /> New time: {formatDateOnly(selectedDate)} at{' '}
              {availability.data?.slots.find((slot) => slot.startAt === selectedStart)?.label ?? 'selected time'}
            </p>
          )}
        </div>
      </Modal>
    </>
  )
}
