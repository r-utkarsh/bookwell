import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft,
  ArrowRight,
  CalendarCheck2,
  CalendarDays,
  Check,
  Clock3,
  LockKeyhole,
  MapPin,
  Sparkles,
  UserRound,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { format, parseISO } from 'date-fns'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { useAuth } from '@/auth/AuthContext'
import {
  Alert,
  Badge,
  Button,
  Card,
  EmptyState,
  LoadingBlock,
  Modal,
  Textarea,
  buttonClasses,
} from '@/components/ui'
import { apiRequest, getErrorMessage } from '@/lib/api'
import { cn } from '@/lib/cn'
import {
  formatCurrency,
  formatDateOnly,
  formatDateTime,
  zonedToday,
} from '@/lib/format'
import type {
  Appointment,
  AvailabilityResponse,
  AvailabilitySlot,
  ProviderDetail,
} from '@/types/api'

export function BookPage() {
  const { providerId } = useParams<{ providerId: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [serviceId, setServiceId] = useState(searchParams.get('service') ?? '')
  const [selectedDate, setSelectedDate] = useState<string | null>(searchParams.get('date'))
  const [selectedSlot, setSelectedSlot] = useState<AvailabilitySlot | null>(null)
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [confirmed, setConfirmed] = useState<Appointment | null>(null)

  const providerQuery = useQuery({
    queryKey: ['provider', providerId],
    queryFn: () => apiRequest<ProviderDetail>(`/providers/${providerId}`),
    enabled: Boolean(providerId),
  })

  const provider = providerQuery.data
  const activeServiceId =
    provider?.services.some((service) => service.id === serviceId)
      ? serviceId
      : provider?.services[0]?.id ?? ''
  const activeService = provider?.services.find((service) => service.id === activeServiceId)
  const activeDate = selectedDate ?? zonedToday(provider?.provider.timeZone ?? 'Asia/Kolkata')
  const dates = useMemo(() => {
    if (!provider) return []
    const start = zonedToday(provider.provider.timeZone)
    return Array.from({ length: 21 }, (_, index) => {
      const date = new Date(`${start}T12:00:00`)
      date.setDate(date.getDate() + index)
      return format(date, 'yyyy-MM-dd')
    })
  }, [provider])

  const availability = useQuery({
    queryKey: ['availability', providerId, activeServiceId, activeDate],
    queryFn: () =>
      apiRequest<AvailabilityResponse>(
        `/providers/${providerId}/availability?serviceId=${activeServiceId}&date=${activeDate}`,
      ),
    enabled: Boolean(providerId && activeServiceId && activeDate),
    staleTime: 0,
  })

  const booking = useMutation({
    mutationFn: () => {
      if (!provider || !activeService || !selectedSlot) throw new Error('Choose a time first.')
      return apiRequest<Appointment>('/appointments', {
        method: 'POST',
        body: {
          providerId: provider.provider.id,
          serviceId: activeService.id,
          startAt: selectedSlot.startAt,
          notes: notes.trim() || null,
        },
      })
    },
    onSuccess: (appointment) => {
      setConfirmed(appointment)
      setSelectedSlot(null)
      setNotes('')
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['availability'] })
      queryClient.invalidateQueries({ queryKey: ['customer-appointments'] })
      toast.success('Your appointment is confirmed.')
    },
    onError: (mutationError) => {
      setError(getErrorMessage(mutationError))
      availability.refetch()
    },
  })

  function selectService(nextServiceId: string) {
    setServiceId(nextServiceId)
    setSelectedSlot(null)
    setError(null)
    const params = new URLSearchParams(searchParams)
    params.set('service', nextServiceId)
    params.set('date', activeDate)
    setSearchParams(params, { replace: true })
  }

  function selectDate(date: string) {
    setSelectedDate(date)
    setSelectedSlot(null)
    setError(null)
    const params = new URLSearchParams(searchParams)
    if (activeServiceId) params.set('service', activeServiceId)
    params.set('date', date)
    setSearchParams(params, { replace: true })
  }

  if (providerQuery.isLoading) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-12 sm:px-6 lg:px-8">
        <LoadingBlock rows={5} label="Loading booking page" />
      </div>
    )
  }

  if (providerQuery.isError || !provider) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-20 sm:px-6">
        <EmptyState
          title="We couldn’t open this booking page"
          description={getErrorMessage(providerQuery.error)}
          action={
            <Link to="/providers" className={buttonClasses('primary', 'md')}>
              Browse providers
            </Link>
          }
        />
      </div>
    )
  }

  const currentPath = `/providers/${provider.provider.id}/book?service=${activeServiceId}&date=${activeDate}`
  const loginHref = `/login?redirect=${encodeURIComponent(currentPath)}`
  const registerHref = `/register?redirect=${encodeURIComponent(currentPath)}`
  const canBook = user?.role === 'CUSTOMER'

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8 lg:py-12">
      <Link
        to={`/providers/${provider.provider.id}`}
        className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-[0.14em] text-ink-muted transition hover:text-forest-900"
      >
        <ArrowLeft className="size-3.5" /> Back to {provider.provider.displayName}
      </Link>

      <div className="mt-5">
        <p className="text-[0.65rem] font-bold uppercase tracking-[0.2em] text-clay-600">Book your visit</p>
        <h1 className="mt-2 text-4xl tracking-tight sm:text-5xl">Choose a service and a time.</h1>
        <p className="mt-2 text-sm text-ink-soft">
          With <span className="font-semibold text-forest-900">{provider.provider.displayName}</span> ·{" "}
          {provider.provider.businessName} · Times shown in {provider.provider.timeZone.replace('_', ' ')}
        </p>
      </div>

      {!user && (
        <Alert tone="info" className="mt-6">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p>
              You can browse times now. Sign in when you are ready to confirm — we&apos;ll bring you back to this page.
            </p>
            <div className="flex shrink-0 gap-2">
              <Link to={loginHref} className={buttonClasses('outline', 'sm')}>
                Sign in
              </Link>
              <Link to={registerHref} className={buttonClasses('primary', 'sm')}>
                Create account
              </Link>
            </div>
          </div>
        </Alert>
      )}
      {user && user.role !== 'CUSTOMER' && (
        <Alert tone="warning" className="mt-6">
          You are signed in as {user.role === 'PROVIDER' ? 'a provider' : 'an administrator'}. Customer accounts
          are required to create a booking. Use a customer account or the provider calendar instead.
        </Alert>
      )}

      <div className="mt-7 grid gap-7 lg:grid-cols-[1fr_360px] lg:items-start">
        <div className="space-y-7">
          <Card className="p-5 sm:p-6">
            <div className="flex items-center gap-3">
              <span className="grid size-9 place-items-center rounded-xl bg-forest-100 text-forest-700">
                <Sparkles className="size-4" />
              </span>
              <div>
                <h2 className="font-display text-xl">1. Pick a service</h2>
                <p className="text-xs text-ink-muted">Duration and price are reserved for you.</p>
              </div>
            </div>
            <div className="mt-5 grid gap-2 sm:grid-cols-2">
              {provider.services.map((service) => {
                const active = service.id === activeServiceId
                return (
                  <button
                    key={service.id}
                    type="button"
                    onClick={() => selectService(service.id)}
                    aria-pressed={active}
                    className={cn(
                      'rounded-xl border p-4 text-left transition',
                      active
                        ? 'border-forest-900 bg-forest-50 ring-1 ring-forest-900'
                        : 'border-line bg-white/55 hover:border-forest-300 hover:bg-white',
                    )}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <p className="text-sm font-bold text-forest-900">{service.name}</p>
                      {active && (
                        <span className="grid size-5 shrink-0 place-items-center rounded-full bg-forest-900 text-cream-50">
                          <Check className="size-3" />
                        </span>
                      )}
                    </div>
                    <div className="mt-2 flex items-center justify-between text-xs text-ink-muted">
                      <span className="inline-flex items-center gap-1">
                        <Clock3 className="size-3" /> {service.durationMinutes} min
                      </span>
                      <span className="font-bold text-forest-800">
                        {formatCurrency(service.price, service.currency)}
                      </span>
                    </div>
                  </button>
                )
              })}
            </div>
          </Card>

          <Card className="p-5 sm:p-6">
            <div className="flex items-center gap-3">
              <span className="grid size-9 place-items-center rounded-xl bg-clay-100 text-clay-700">
                <CalendarDays className="size-4" />
              </span>
              <div>
                <h2 className="font-display text-xl">2. Choose a date</h2>
                <p className="text-xs text-ink-muted">Showing the next three weeks.</p>
              </div>
            </div>
            <div className="no-scrollbar mt-5 flex gap-2 overflow-x-auto pb-1">
              {dates.map((date) => {
                const active = date === activeDate
                return (
                  <button
                    key={date}
                    type="button"
                    onClick={() => selectDate(date)}
                    aria-pressed={active}
                    className={cn(
                      'flex min-w-[4.30rem] shrink-0 flex-col items-center rounded-xl border px-2 py-2.5 transition',
                      active
                        ? 'border-forest-900 bg-forest-900 text-cream-50'
                        : 'border-line bg-white/55 text-forest-900 hover:border-forest-300 hover:bg-white',
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
          </Card>

          <Card className="p-5 sm:p-6">
            <div className="flex items-center gap-3">
              <span className="grid size-9 place-items-center rounded-xl bg-gold-100 text-gold-700">
                <Clock3 className="size-4" />
              </span>
              <div>
                <h2 className="font-display text-xl">3. Pick a time</h2>
                <p className="text-xs text-ink-muted">{formatDateOnly(activeDate)} · {provider.provider.timeZone.replace('_', ' ')}</p>
              </div>
            </div>

            {availability.isLoading ? (
              <div className="mt-5">
                <LoadingBlock rows={2} label="Loading available times" />
              </div>
            ) : availability.isError ? (
              <Alert tone="error" className="mt-5">
                <p>{getErrorMessage(availability.error)}</p>
                <Button variant="outline" size="sm" className="mt-3" onClick={() => availability.refetch()}>
                  Try again
                </Button>
              </Alert>
            ) : availability.data?.slots.length ? (
              <>
                <div className="mt-5 grid grid-cols-3 gap-2 sm:grid-cols-4 md:grid-cols-5">
                  {availability.data.slots.map((slot) => {
                    const active = selectedSlot?.startAt === slot.startAt
                    return (
                      <button
                        key={slot.startAt}
                        type="button"
                        onClick={() => {
                          setSelectedSlot(slot)
                          setError(null)
                        }}
                        aria-pressed={active}
                        className={cn(
                          'rounded-xl border px-2 py-3 text-xs font-bold transition',
                          active
                            ? 'border-forest-900 bg-forest-900 text-cream-50 shadow-sm'
                            : 'border-line bg-white/60 text-forest-800 hover:border-forest-300 hover:bg-white',
                        )}
                      >
                        {slot.label}
                      </button>
                    )
                  })}
                </div>
                <p className="mt-4 text-xs text-ink-muted">
                  Times are in {provider.provider.timeZone.replace('_', ' ')}. Availability refreshes as bookings
                  are made.
                </p>
              </>
            ) : (
              <EmptyState
                className="mt-5 border-0 bg-cream-100/60 py-8"
                title="No times left on this date"
                description={availability.data?.message ?? 'Try another date — most studios open new slots throughout the week.'}
              />
            )}
          </Card>
        </div>

        <aside className="lg:sticky lg:top-24">
          <Card className="overflow-hidden">
            <div className="bg-forest-900 p-6 text-cream-50">
              <p className="text-[0.62rem] font-bold uppercase tracking-[0.18em] text-cream-300/70">Your booking</p>
              <p className="mt-2 font-display text-2xl">{activeService?.name ?? 'Choose a service'}</p>
              <p className="mt-1 text-xs text-cream-200/70">
                {activeService ? `${activeService.durationMinutes} minutes` : 'Select a service above'}
              </p>
              <div className="mt-5 flex items-end justify-between border-t border-white/10 pt-4">
                <span className="text-xs text-cream-200/70">Total</span>
                <span className="font-display text-2xl">
                  {activeService ? formatCurrency(activeService.price, activeService.currency) : '—'}
                </span>
              </div>
            </div>
            <div className="space-y-4 p-5">
              <div className="flex gap-3">
                <UserRound className="mt-0.5 size-4 shrink-0 text-forest-600" />
                <div>
                  <p className="text-xs font-bold text-forest-900">{provider.provider.displayName}</p>
                  <p className="text-[0.68rem] text-ink-muted">{provider.provider.businessName}</p>
                </div>
              </div>
              <div className="flex gap-3">
                <CalendarCheck2 className="mt-0.5 size-4 shrink-0 text-forest-600" />
                <div>
                  <p className="text-xs font-bold text-forest-900">{formatDateOnly(activeDate)}</p>
                  <p className="text-[0.68rem] text-ink-muted">
                    {selectedSlot ? selectedSlot.label : 'Choose a time'} ·{" "}
                    {provider.provider.timeZone.replace('_', ' ')}
                  </p>
                </div>
              </div>
              <div className="flex gap-3">
                <MapPin className="mt-0.5 size-4 shrink-0 text-forest-600" />
                <div>
                  <p className="text-xs font-bold text-forest-900">{provider.provider.city || 'In person'}</p>
                  <p className="text-[0.68rem] text-ink-muted">
                    {provider.provider.address || 'Address shared in your confirmation'}
                  </p>
                </div>
              </div>

              <div>
                <label htmlFor="booking-notes" className="mb-1.5 block text-xs font-bold uppercase tracking-[0.12em] text-forest-800">
                  Notes <span className="font-medium normal-case tracking-normal text-ink-muted">(optional)</span>
                </label>
                <Textarea
                  id="booking-notes"
                  value={notes}
                  onChange={(event) => setNotes(event.target.value)}
                  maxLength={1000}
                  rows={3}
                  placeholder="Anything your provider should know?"
                />
              </div>

              {error && <Alert tone="error">{error}</Alert>}

              {canBook ? (
                <Button
                  size="lg"
                  className="w-full"
                  disabled={!selectedSlot || !activeService}
                  loading={booking.isPending}
                  onClick={() => booking.mutate()}
                >
                  Confirm booking <ArrowRight className="size-4" />
                </Button>
              ) : user ? (
                <Link to="/" className={buttonClasses('outline', 'lg') + ' w-full'}>
                  Switch to a customer account
                </Link>
              ) : (
                <Link to={loginHref} className={buttonClasses('primary', 'lg') + ' w-full'}>
                  Sign in to book <LockKeyhole className="size-4" />
                </Link>
              )}
              <p className="text-center text-[0.65rem] leading-relaxed text-ink-muted">
                No payment is taken now. Your appointment is held for you the moment you confirm.
              </p>
            </div>
          </Card>
          <div className="mt-3 flex items-center justify-center gap-2 text-[0.65rem] text-ink-muted">
            <LockKeyhole className="size-3" /> Protected by database-enforced availability
          </div>
        </aside>
      </div>

      <Modal
        open={Boolean(confirmed)}
        onOpenChange={(open) => {
          if (!open) {
            setConfirmed(null)
            navigate('/account')
          }
        }}
        title="You’re booked."
        description="A confirmation has been queued for your inbox."
        footer={
          <>
            <Button variant="ghost" onClick={() => navigate('/providers')}>
              Keep browsing
            </Button>
            <Button onClick={() => navigate('/account')}>View my bookings</Button>
          </>
        }
      >
        {confirmed && (
          <div className="rounded-2xl border border-line bg-cream-100/70 p-5">
            <Badge tone="green">
              <Check className="size-3" /> Confirmed
            </Badge>
            <p className="mt-3 font-display text-2xl text-forest-900">{confirmed.serviceName}</p>
            <p className="mt-1 text-sm text-ink-soft">with {confirmed.providerDisplayName}</p>
            <p className="mt-4 text-sm font-bold text-forest-900">
              {formatDateTime(confirmed.startAt, confirmed.providerTimeZone)}
            </p>
            <p className="text-xs text-ink-muted">
              {confirmed.serviceDurationMinutes} minutes ·{" "}
              {formatCurrency(confirmed.servicePrice, confirmed.currency)}
            </p>
          </div>
        )}
      </Modal>
    </div>
  )
}
