import { useQuery } from '@tanstack/react-query'
import {
  ArrowLeft,
  CalendarDays,
  Clock3,
  MapPin,
  Phone,
  ShieldCheck,
  Sparkles,
} from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import {
  Alert,
  Badge,
  buttonClasses,
  Card,
  EmptyState,
  LoadingBlock,
  PageHeader,
} from '@/components/ui'
import { apiRequest, getErrorMessage } from '@/lib/api'
import {
  dayName,
  durationLabel,
  formatCurrency,
  timeFromClock,
} from '@/lib/format'
import type { ProviderDetail } from '@/types/api'

export function ProviderDetailPage() {
  const { providerId } = useParams<{ providerId: string }>()
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['provider', providerId],
    queryFn: () => apiRequest<ProviderDetail>(`/providers/${providerId}`),
    enabled: Boolean(providerId),
    retry: false,
  })

  if (isLoading) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-12 sm:px-6 lg:px-8">
        <LoadingBlock rows={5} label="Loading provider" />
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-20 sm:px-6">
        <EmptyState
          title="That provider isn’t available"
          description={getErrorMessage(error)}
          action={
            <Link to="/providers" className={buttonClasses('primary', 'md')}>
              Browse providers
            </Link>
          }
        />
      </div>
    )
  }

  const { provider, services, weeklyHours } = data
  const groupedHours = Array.from({ length: 7 }, (_, index) => index + 1)
    .map((day) => ({
      day,
      slots: weeklyHours.filter((hour) => hour.dayOfWeek === day),
    }))
    .filter((entry) => entry.slots.length > 0)

  return (
    <div>
      <div className="mx-auto max-w-7xl px-4 pt-6 sm:px-6 lg:px-8">
        <Link
          to="/providers"
          className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-[0.14em] text-ink-muted transition hover:text-forest-900"
        >
          <ArrowLeft className="size-3.5" /> All providers
        </Link>
      </div>

      <section className="mx-auto mt-5 max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="relative overflow-hidden rounded-[2rem] bg-forest-900 px-6 py-9 text-cream-50 shadow-lift sm:px-10 sm:py-12">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_85%_20%,rgba(196,154,90,0.3),transparent_38%),radial-gradient(circle_at_75%_95%,rgba(201,111,74,0.28),transparent_35%)]" aria-hidden="true" />
          <div className="relative flex flex-col gap-8 md:flex-row md:items-end md:justify-between">
            <div className="max-w-2xl">
              <div className="flex flex-wrap items-center gap-2">
                <Badge tone="amber">{provider.category}</Badge>
                {provider.featured && (
                  <Badge tone="clay">
                    <Sparkles className="size-3" /> Featured
                  </Badge>
                )}
                <Badge tone="green">
                  <ShieldCheck className="size-3" /> Reviewed by Bookwell
                </Badge>
              </div>
              <h1 className="mt-5 text-4xl tracking-tight text-cream-50 sm:text-5xl">{provider.displayName}</h1>
              <p className="mt-2 font-sans text-sm font-semibold tracking-normal text-gold-300">{provider.businessName}</p>
              <p className="mt-4 max-w-xl text-sm leading-relaxed text-cream-200/80">
                {provider.description || 'A thoughtful independent provider offering considered, one-to-one appointments.'}
              </p>
              <div className="mt-6 flex flex-wrap gap-x-6 gap-y-3 text-sm text-cream-200/80">
                <span className="inline-flex items-center gap-2">
                  <MapPin className="size-4 text-gold-300" />
                  {[provider.address, provider.city].filter(Boolean).join(', ') || 'Location shared after booking'}
                </span>
                <span className="inline-flex items-center gap-2">
                  <Clock3 className="size-4 text-gold-300" />
                  Times in {provider.timeZone.replace('_', ' ')}
                </span>
                {provider.phone && (
                  <a href={`tel:${provider.phone}`} className="inline-flex items-center gap-2 hover:text-white">
                    <Phone className="size-4 text-gold-300" /> {provider.phone}
                  </a>
                )}
              </div>
            </div>
            <div className="shrink-0 rounded-2xl border border-white/10 bg-white/[0.06] p-5 backdrop-blur">
              <p className="text-[0.6rem] font-bold uppercase tracking-[0.18em] text-cream-300/70">Services from</p>
              <p className="mt-1 font-display text-3xl">
                {formatCurrency(
                  services.reduce<number | null>((lowest, service) => {
                    const price = Number(service.price)
                    return lowest === null || price < lowest ? price : lowest
                  }, null),
                  provider.currency,
                )}
              </p>
              <p className="mt-1 text-xs text-cream-200/70">
                {services.length} {services.length === 1 ? 'service' : 'services'} available
              </p>
              <a href="#services" className={buttonClasses('gold', 'md') + ' mt-4 w-full'}>
                See services
              </a>
            </div>
          </div>
        </div>
      </section>

      <div className="mx-auto grid max-w-7xl gap-10 px-4 py-12 sm:px-6 lg:grid-cols-[1fr_330px] lg:px-8">
        <section id="services" className="min-w-0 scroll-mt-24">
          <PageHeader
            eyebrow="Choose your service"
            title="What would you like to book?"
            description="Each service includes its full duration and price. Availability updates in real time."
          />
          <div className="mt-6 space-y-3">
            {services.length === 0 ? (
              <EmptyState
                title="No services listed yet"
                description="This provider is still preparing their service menu. Please check back shortly."
              />
            ) : (
              services.map((service) => (
                <Card key={service.id} as="article" className="flex flex-col gap-4 p-5 transition hover:border-forest-300 sm:flex-row sm:items-center sm:p-6">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="font-display text-xl text-forest-900">{service.name}</h3>
                      <Badge tone="neutral">
                        <Clock3 className="size-3" /> {durationLabel(service.durationMinutes)}
                      </Badge>
                    </div>
                    {service.description && (
                      <p className="mt-2 text-sm leading-relaxed text-ink-soft">{service.description}</p>
                    )}
                  </div>
                  <div className="flex shrink-0 items-center justify-between gap-5 border-t border-line pt-4 sm:flex-col sm:items-end sm:border-t-0 sm:pt-0">
                    <div className="text-right">
                      <p className="font-display text-xl text-forest-900">{formatCurrency(service.price, service.currency)}</p>
                      <p className="text-[0.65rem] uppercase tracking-[0.12em] text-ink-muted">per appointment</p>
                    </div>
                    <Link
                      to={`/providers/${provider.id}/book?service=${service.id}`}
                      className={buttonClasses('primary', 'sm')}
                    >
                      Book this <CalendarDays className="size-3.5" />
                    </Link>
                  </div>
                </Card>
              ))
            )}
          </div>

          <Alert className="mt-8" tone="info">
            <strong className="font-bold">Plans can change.</strong> You can reschedule or cancel online from your
            account until the provider&apos;s change window closes. The exact window is shown with your confirmation.
          </Alert>
        </section>

        <aside className="space-y-5">
          <Card className="p-6">
            <h2 className="font-display text-xl">Opening hours</h2>
            <p className="mt-1 text-xs text-ink-muted">{provider.timeZone.replace('_', ' ')}</p>
            <div className="mt-4 space-y-3">
              {groupedHours.length === 0 ? (
                <p className="text-sm text-ink-muted">Hours are being finalised. Contact the provider for details.</p>
              ) : (
                groupedHours.map(({ day, slots }) => (
                  <div key={day} className="flex items-start justify-between gap-3 border-b border-line/70 pb-3 last:border-0 last:pb-0">
                    <span className="text-sm font-semibold text-forest-800">{dayName(day)}</span>
                    <div className="text-right text-xs font-medium text-ink-soft">
                      {slots.map((slot, index) => (
                        <p key={slot.id ?? `${slot.startTime}-${index}`}>
                          {timeFromClock(slot.startTime)} – {timeFromClock(slot.endTime)}
                        </p>
                      ))}
                    </div>
                  </div>
                ))
              )}
            </div>
          </Card>

          <Card className="bg-cream-100/70 p-6">
            <h2 className="font-display text-xl">Before you book</h2>
            <ul className="mt-4 space-y-3 text-sm text-ink-soft">
              <li className="flex gap-2.5">
                <ShieldCheck className="mt-0.5 size-4 shrink-0 text-forest-600" />
                Your slot is held only for you. Double bookings are blocked by the platform.
              </li>
              <li className="flex gap-2.5">
                <Clock3 className="mt-0.5 size-4 shrink-0 text-forest-600" />
                Please arrive a few minutes early. The full duration is reserved for your service.
              </li>
              <li className="flex gap-2.5">
                <MapPin className="mt-0.5 size-4 shrink-0 text-forest-600" />
                {provider.address || 'The provider will share exact arrival details with your confirmation.'}
              </li>
            </ul>
          </Card>
        </aside>
      </div>
    </div>
  )
}
