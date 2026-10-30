import { useQuery } from '@tanstack/react-query'
import {
  ArrowRight,
  CalendarCheck2,
  CalendarDays,
  CheckCircle2,
  Clock3,
  ExternalLink,
  Scissors,
  Store,
  UsersRound,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import {
  Alert,
  Badge,
  buttonClasses,
  Card,
  EmptyState,
  LoadingBlock,
  PageHeader,
  StatusBadge,
  StatCard,
} from '@/components/ui'
import { useAuth } from '@/auth/AuthContext'
import { apiRequest } from '@/lib/api'
import { addDaysToDate, formatCurrency, formatTime, zonedToday } from '@/lib/format'
import type {
  Appointment,
  ProviderDashboardProfile,
  ProviderDashboardStats,
} from '@/types/api'

export function ProviderOverviewPage() {
  const { user } = useAuth()
  const profileQuery = useQuery({
    queryKey: ['provider-profile'],
    queryFn: () => apiRequest<ProviderDashboardProfile>('/provider/profile'),
  })
  const profile = profileQuery.data

  const { data: stats } = useQuery({
    queryKey: ['provider-stats'],
    queryFn: () => apiRequest<ProviderDashboardStats>('/provider/stats'),
  })

  const today = profile ? zonedToday(profile.timeZone) : null
  const { data: appointments, isLoading } = useQuery({
    queryKey: ['provider-calendar', today, today ? addDaysToDate(today, 7) : null],
    queryFn: () =>
      apiRequest<Appointment[]>(
        `/provider/appointments?from=${today}&to=${addDaysToDate(today ?? '', 7)}`,
      ),
    enabled: Boolean(today),
  })

  if (profileQuery.isLoading) return <LoadingBlock rows={5} label="Loading your studio" />

  if (!profile) {
    return (
      <EmptyState
        title="We couldn’t find your provider profile"
        description="Your account is active, but the business profile is missing. Please contact support so we can reconnect it."
      />
    )
  }

  const isApproved = profile.status === 'APPROVED'
  const todayAppointments = (appointments ?? []).filter(
    (appointment) =>
      appointment.status === 'CONFIRMED' &&
      today &&
      zonedDate(appointment.startAt, profile.timeZone) === today,
  )
  const nextAppointments = (appointments ?? [])
    .filter((appointment) => appointment.status === 'CONFIRMED')
    .sort((a, b) => a.startAt.localeCompare(b.startAt))
    .slice(0, 6)

  return (
    <div>
      <PageHeader
        eyebrow="Provider studio"
        title={`Good to see you, ${user?.fullName.split(' ')[0] ?? 'there'}.`}
        description="A clear view of your business, your day, and the next few appointments."
        actions={
          <>
            <Link to="/provider/calendar" className={buttonClasses('outline', 'sm')}>
              <CalendarDays className="size-4" /> Open calendar
            </Link>
            {isApproved && (
              <Link to={`/providers/${profile.id}`} className={buttonClasses('primary', 'sm')}>
                <ExternalLink className="size-4" /> View public page
              </Link>
            )}
          </>
        }
      />

      {profile.status !== 'APPROVED' && (
        <Alert tone={profile.status === 'PENDING' ? 'warning' : 'error'} className="mt-6">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={profile.status} />
            <strong className="font-bold">
              {profile.status === 'PENDING'
                ? 'Your application is awaiting administrator approval.'
                : 'Your listing is currently suspended.'}
            </strong>
          </div>
          <p className="mt-1 text-xs leading-relaxed">
            {profile.status === 'PENDING'
              ? 'You can prepare services, hours, and your profile now. Customers will be able to book once an administrator approves you.'
              : 'Your calendar is still accessible, but customers cannot make new bookings. Contact the platform administrator for help.'}
          </p>
        </Alert>
      )}

      <div className="mt-7 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Today"
          value={stats?.todaysAppointments ?? '—'}
          hint="confirmed appointments"
          icon={<CalendarCheck2 className="size-4" />}
          accent
        />
        <StatCard
          label="Upcoming"
          value={stats?.upcomingAppointments ?? '—'}
          hint="future bookings"
          icon={<Clock3 className="size-4" />}
        />
        <StatCard
          label="Active services"
          value={stats?.activeServices ?? '—'}
          hint="on your public menu"
          icon={<Scissors className="size-4" />}
        />
        <StatCard
          label="Completed"
          value={stats?.completedAppointments ?? '—'}
          hint="all-time visits"
          icon={<CheckCircle2 className="size-4" />}
        />
      </div>

      <div className="mt-8 grid gap-6 xl:grid-cols-[1.35fr_0.65fr]">
        <Card className="overflow-hidden">
          <div className="flex items-center justify-between gap-3 border-b border-line px-5 py-4">
            <div>
              <h2 className="font-display text-xl">Today’s calendar</h2>
              <p className="text-xs text-ink-muted">
                {today} · {profile.timeZone.replace('_', ' ')}
              </p>
            </div>
            <Badge tone={todayAppointments.length ? 'green' : 'neutral'}>
              {todayAppointments.length} booked
            </Badge>
          </div>
          {isLoading ? (
            <div className="p-5">
              <LoadingBlock rows={2} label="Loading today's appointments" />
            </div>
          ) : todayAppointments.length ? (
            <div className="divide-y divide-line">
              {todayAppointments.map((appointment) => (
                <div key={appointment.id} className="flex items-start gap-4 px-5 py-4">
                  <div className="w-16 shrink-0">
                    <p className="font-display text-lg text-forest-900">{formatTime(appointment.startAt, profile.timeZone)}</p>
                    <p className="text-[0.58rem] font-bold uppercase tracking-wider text-ink-muted">
                      {appointment.serviceDurationMinutes} min
                    </p>
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-bold text-forest-900">{appointment.serviceName}</p>
                    <p className="mt-0.5 truncate text-xs text-ink-muted">
                      {appointment.customerName} · {formatCurrency(appointment.servicePrice, appointment.currency)}
                    </p>
                    {appointment.customerNotes && (
                      <p className="mt-1 line-clamp-1 text-[0.68rem] text-clay-700">Note: {appointment.customerNotes}</p>
                    )}
                  </div>
                  <StatusBadge status={appointment.status} />
                </div>
              ))}
            </div>
          ) : (
            <EmptyState
              className="rounded-none border-0"
              title="A clear day"
              description="No appointments booked today. Use the space well."
              action={
                <Link to="/provider/availability" className={buttonClasses('outline', 'sm')}>
                  Review availability
                </Link>
              }
            />
          )}
        </Card>

        <div className="space-y-5">
          <Card className="p-5">
            <h2 className="font-display text-xl">Studio at a glance</h2>
            <div className="mt-4 space-y-4">
              <div className="flex gap-3">
                <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-forest-100 text-forest-700">
                  <Store className="size-4" />
                </span>
                <div className="min-w-0">
                  <p className="truncate text-sm font-bold text-forest-900">{profile.businessName}</p>
                  <p className="text-xs text-ink-muted">{profile.category}</p>
                </div>
              </div>
              <div className="flex gap-3">
                <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-cream-100 text-forest-700">
                  <UsersRound className="size-4" />
                </span>
                <div className="min-w-0">
                  <p className="truncate text-sm font-bold text-forest-900">{profile.ownerName}</p>
                  <p className="truncate text-xs text-ink-muted">{profile.ownerEmail}</p>
                </div>
              </div>
              <div className="border-t border-line pt-4">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-ink-muted">Listing status</span>
                  <StatusBadge status={profile.status} />
                </div>
                <div className="mt-2 flex items-center justify-between text-xs">
                  <span className="text-ink-muted">Time zone</span>
                  <span className="font-semibold text-forest-800">{profile.timeZone.replace('_', ' ')}</span>
                </div>
              </div>
            </div>
            <Link to="/provider/profile" className={buttonClasses('ghost', 'sm') + ' mt-4 w-full'}>
              Edit profile <ArrowRight className="size-3.5" />
            </Link>
          </Card>

          <Card className="p-5">
            <div className="flex items-center justify-between">
              <h2 className="font-display text-xl">Coming up</h2>
              <Link to="/provider/calendar" className="text-xs font-bold text-clay-700 hover:text-clay-800">
                Calendar
              </Link>
            </div>
            {nextAppointments.length ? (
              <div className="mt-3 space-y-2">
                {nextAppointments.slice(0, 4).map((appointment) => (
                  <div key={appointment.id} className="rounded-xl border border-line/80 bg-white/50 px-3 py-2.5">
                    <p className="truncate text-xs font-bold text-forest-900">{appointment.serviceName}</p>
                    <p className="mt-0.5 truncate text-[0.65rem] text-ink-muted">
                      {zonedDate(appointment.startAt, profile.timeZone)} · {formatTime(appointment.startAt, profile.timeZone)} · {appointment.customerName}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <p className="mt-4 text-xs text-ink-muted">No future appointments yet. Your public page is the best place to start.</p>
            )}
          </Card>
        </div>
      </div>
    </div>
  )
}

function zonedDate(iso: string, timeZone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(iso))
  const year = parts.find((part) => part.type === 'year')?.value
  const month = parts.find((part) => part.type === 'month')?.value
  const day = parts.find((part) => part.type === 'day')?.value
  return `${year}-${month}-${day}`
}
