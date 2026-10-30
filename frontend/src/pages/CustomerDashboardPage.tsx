import { useQuery } from '@tanstack/react-query'
import {
  CalendarDays,
  CalendarPlus,
  Clock3,
  MapPin,
  NotebookPen,
  RotateCcw,
  Sparkles,
} from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { AppointmentActions } from '@/components/AppointmentActions'
import {
  Avatar,
  Badge,
  buttonClasses,
  Card,
  EmptyState,
  LoadingBlock,
  Pagination,
  StatusBadge,
} from '@/components/ui'
import { apiRequest } from '@/lib/api'
import { cn } from '@/lib/cn'
import { durationLabel, formatCurrency, formatDate, formatDateTime, formatTime } from '@/lib/format'
import type { Appointment, PageResponse } from '@/types/api'

export function CustomerDashboardPage() {
  const { user } = useAuth()
  const [scope, setScope] = useState<'upcoming' | 'history'>('upcoming')
  const [page, setPage] = useState(0)

  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['customer-appointments', scope, page],
    queryFn: () =>
      apiRequest<PageResponse<Appointment>>(
        `/appointments?scope=${scope}&page=${page}&size=8`,
      ),
  })

  function changeScope(next: 'upcoming' | 'history') {
    setScope(next)
    setPage(0)
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8 lg:py-14">
      <div className="overflow-hidden rounded-[2rem] bg-forest-900 px-6 py-8 text-cream-50 shadow-lift sm:px-9">
        <div className="flex flex-col justify-between gap-6 sm:flex-row sm:items-center">
          <div className="flex items-center gap-4">
            <Avatar name={user?.fullName ?? 'Bookwell'} size="lg" />
            <div>
              <p className="text-[0.62rem] font-bold uppercase tracking-[0.2em] text-gold-300">Your appointments</p>
              <h1 className="mt-1 font-display text-3xl text-cream-50 sm:text-4xl">
                Hello, {user?.fullName.split(' ')[0]}.
              </h1>
              <p className="mt-1 text-xs text-cream-200/70">Here is what is on your calendar.</p>
            </div>
          </div>
          <Link to="/providers" className={buttonClasses('gold', 'md')}>
            <CalendarPlus className="size-4" /> Book something new
          </Link>
        </div>
      </div>

      <div className="mt-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="inline-flex rounded-full border border-line bg-white/55 p-1" role="tablist" aria-label="Appointment scope">
          {(['upcoming', 'history'] as const).map((option) => (
            <button
              key={option}
              type="button"
              role="tab"
              aria-selected={scope === option}
              onClick={() => changeScope(option)}
              className={cn(
                'rounded-full px-4 py-2 text-xs font-bold capitalize transition',
                scope === option ? 'bg-forest-900 text-cream-50 shadow-sm' : 'text-ink-soft hover:text-forest-900',
              )}
            >
              {option === 'upcoming' ? 'Upcoming' : 'Past & cancelled'}
            </button>
          ))}
        </div>
        <p className="text-xs text-ink-muted">
          {data ? `${data.totalElements} ${scope === 'upcoming' ? 'upcoming' : 'past'} appointment${data.totalElements === 1 ? '' : 's'}` : ' '}
        </p>
      </div>

      <div className="mt-5 space-y-4">
        {isLoading ? (
          <LoadingBlock rows={3} label="Loading appointments" />
        ) : isError ? (
          <EmptyState
            title="We couldn’t load your appointments"
            description="Check your connection, then try once more."
            action={<button onClick={() => refetch()} className={buttonClasses('outline', 'md')}>Try again</button>}
          />
        ) : data?.content.length ? (
          <div className={cn('space-y-4', isFetching && 'opacity-60 transition')}>
            {data.content.map((appointment) => (
              <AppointmentRow key={appointment.id} appointment={appointment} scope={scope} />
            ))}
          </div>
        ) : (
          <EmptyState
            icon={scope === 'upcoming' ? <CalendarDays className="size-5" /> : <RotateCcw className="size-5" />}
            title={scope === 'upcoming' ? 'Nothing on the calendar yet' : 'Nothing in your history yet'}
            description={
              scope === 'upcoming'
                ? 'When you book a service, it will appear here with everything you need for the day.'
                : 'Completed and cancelled appointments will collect here.'
            }
            action={
              scope === 'upcoming' ? (
                <Link to="/providers" className={buttonClasses('primary', 'md')}>
                  <Sparkles className="size-4" /> Find a provider
                </Link>
              ) : undefined
            }
          />
        )}
      </div>

      {data && (
        <Pagination
          page={data.page}
          totalPages={data.totalPages}
          onPageChange={setPage}
          className="mt-7"
        />
      )}
    </div>
  )
}

function AppointmentRow({ appointment, scope }: { appointment: Appointment; scope: 'upcoming' | 'history' }) {
  return (
    <Card as="article" className="overflow-hidden">
      <div className="flex flex-col gap-5 p-5 sm:flex-row sm:p-6">
        <div className="flex w-full shrink-0 items-center gap-4 sm:w-40 sm:flex-col sm:items-start sm:border-r sm:border-line sm:pr-5">
          <div>
            <p className="font-display text-2xl leading-tight text-forest-900">
              {formatTime(appointment.startAt, appointment.providerTimeZone)}
            </p>
            <p className="text-xs font-bold uppercase tracking-[0.12em] text-ink-muted">
              {formatDate(appointment.startAt, appointment.providerTimeZone)}
            </p>
          </div>
          <div className="ml-auto sm:ml-0">
            <StatusBadge status={appointment.status} />
          </div>
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="font-display text-xl text-forest-900">{appointment.serviceName}</h2>
              <p className="mt-1 text-sm font-semibold text-ink-soft">
                {appointment.providerDisplayName} · {appointment.providerBusinessName}
              </p>
            </div>
            <p className="font-display text-lg text-forest-900">
              {formatCurrency(appointment.servicePrice, appointment.currency)}
            </p>
          </div>
          <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-xs text-ink-muted">
            <span className="inline-flex items-center gap-1.5">
              <Clock3 className="size-3.5" /> {durationLabel(appointment.serviceDurationMinutes)}
            </span>
            <span className="inline-flex items-center gap-1.5">
              <CalendarDays className="size-3.5" /> {formatDateTime(appointment.startAt, appointment.providerTimeZone)}
            </span>
            {appointment.providerCity && (
              <span className="inline-flex items-center gap-1.5">
                <MapPin className="size-3.5" /> {appointment.providerCity}
              </span>
            )}
            {appointment.rescheduleCount > 0 && (
              <Badge tone="blue">
                Rescheduled {appointment.rescheduleCount === 1 ? 'once' : `${appointment.rescheduleCount} times`}
              </Badge>
            )}
          </div>
          {appointment.customerNotes && (
            <p className="mt-3 flex gap-2 rounded-xl bg-cream-100/70 px-3 py-2 text-xs text-ink-soft">
              <NotebookPen className="mt-0.5 size-3.5 shrink-0 text-forest-600" />
              {appointment.customerNotes}
            </p>
          )}
          {appointment.status === 'CANCELLED' && appointment.cancellationReason && (
            <p className="mt-3 text-xs text-ink-muted">
              Cancelled by {appointment.cancelledBy?.toLowerCase() ?? 'the customer'}: “{appointment.cancellationReason}”
            </p>
          )}
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-line/80 pt-4">
            <p className="text-[0.65rem] text-ink-muted">
              {appointment.providerAddress || 'Address shared in your confirmation'}
            </p>
            {scope === 'upcoming' && <AppointmentActions appointment={appointment} />}
          </div>
        </div>
      </div>
    </Card>
  )
}
