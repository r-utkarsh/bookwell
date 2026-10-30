import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import type { DatesSetArg, EventClickArg, EventInput } from '@fullcalendar/core'
import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { AppointmentActions } from '@/components/AppointmentActions'
import {
  Alert,
  Badge,
  Button,
  Card,
  EmptyState,
  LoadingBlock,
  Modal,
  PageHeader,
  Select,
  StatusBadge,
} from '@/components/ui'
import { apiRequest, getErrorMessage } from '@/lib/api'
import { formatDate, formatTime } from '@/lib/format'
import type {
  Appointment,
  AppointmentStatus,
  ProviderDashboardProfile,
} from '@/types/api'

const eventStyles: Record<AppointmentStatus, { backgroundColor: string; borderColor: string; textColor: string }> = {
  CONFIRMED: { backgroundColor: '#4a7861', borderColor: '#4a7861', textColor: '#fbf8f2' },
  CANCELLED: { backgroundColor: '#e6dccd', borderColor: '#d4c6b3', textColor: '#6b635b' },
  COMPLETED: { backgroundColor: '#b8cbbf', borderColor: '#93b8a4', textColor: '#223a30' },
  NO_SHOW: { backgroundColor: '#e1c48c', borderColor: '#c49a5a', textColor: '#4a3b20' },
}

function isoDate(date: Date): string {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 10)
}

export function ProviderCalendarPage() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<'ALL' | AppointmentStatus>('ALL')
  const [range, setRange] = useState(() => {
    const start = new Date()
    start.setDate(start.getDate() - start.getDay() + 1)
    const end = new Date(start)
    end.setDate(end.getDate() + 34)
    return { from: isoDate(start), to: isoDate(end) }
  })
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const profileQuery = useQuery({
    queryKey: ['provider-profile'],
    queryFn: () => apiRequest<ProviderDashboardProfile>('/provider/profile'),
  })
  const profile = profileQuery.data

  const calendarQuery = useQuery({
    queryKey: ['provider-calendar', range.from, range.to, status],
    queryFn: () => {
      const params = new URLSearchParams({ from: range.from, to: range.to })
      if (status !== 'ALL') params.set('status', status)
      return apiRequest<Appointment[]>(`/provider/appointments?${params.toString()}`)
    },
    enabled: Boolean(profile),
  })

  const appointments = useMemo(() => calendarQuery.data ?? [], [calendarQuery.data])
  const selected = appointments.find((appointment) => appointment.id === selectedId) ?? null

  const events: EventInput[] = useMemo(
    () =>
      appointments.map((appointment) => ({
        id: appointment.id,
        title: `${formatTime(appointment.startAt, profile?.timeZone)} · ${appointment.serviceName} — ${appointment.customerName}`,
        start: appointment.startAt,
        end: appointment.endAt,
        ...eventStyles[appointment.status],
        extendedProps: { status: appointment.status },
      })),
    [appointments, profile?.timeZone],
  )

  const completeMutation = useMutation({
    mutationFn: (id: string) =>
      apiRequest<Appointment>(`/appointments/${id}/complete`, { method: 'POST' }),
    onSuccess: () => {
      toast.success('Appointment marked as completed.')
      setSelectedId(null)
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['provider-calendar'] })
      queryClient.invalidateQueries({ queryKey: ['provider-stats'] })
    },
    onError: (error) => setActionError(getErrorMessage(error)),
  })

  const noShowMutation = useMutation({
    mutationFn: (id: string) =>
      apiRequest<Appointment>(`/appointments/${id}/no-show`, { method: 'POST' }),
    onSuccess: () => {
      toast.success('Appointment marked as a no-show.')
      setSelectedId(null)
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['provider-calendar'] })
      queryClient.invalidateQueries({ queryKey: ['provider-stats'] })
    },
    onError: (error) => setActionError(getErrorMessage(error)),
  })

  function handleDatesSet(arg: DatesSetArg) {
    const from = isoDate(arg.start)
    const end = isoDate(new Date(arg.end.getTime() - 1))
    if (from !== range.from || end !== range.to) {
      setRange({ from, to: end })
    }
  }

  function handleEventClick(arg: EventClickArg) {
    setActionError(null)
    setSelectedId(arg.event.id)
  }

  function refreshSelected() {
    queryClient.invalidateQueries({ queryKey: ['provider-calendar'] })
    queryClient.invalidateQueries({ queryKey: ['provider-stats'] })
  }

  return (
    <div>
      <PageHeader
        eyebrow="Calendar"
        title="Your working week."
        description="See confirmed, completed, cancelled, and no-show appointments. Select any appointment for customer details and actions."
        actions={
          <div className="w-full sm:w-44">
            <label htmlFor="calendar-status" className="sr-only">Filter by status</label>
            <Select id="calendar-status" value={status} onChange={(event) => setStatus(event.target.value as 'ALL' | AppointmentStatus)}>
              <option value="ALL">All statuses</option>
              <option value="CONFIRMED">Confirmed</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
              <option value="NO_SHOW">No-show</option>
            </Select>
          </div>
        }
      />

      {profileQuery.isError && (
        <Alert tone="error" className="mt-6">{getErrorMessage(profileQuery.error)}</Alert>
      )}

      <Card className="mt-6 overflow-hidden p-3 sm:p-5">
        {profileQuery.isLoading ? (
          <div className="p-4"><LoadingBlock rows={4} label="Loading calendar" /></div>
        ) : calendarQuery.isLoading ? (
          <div className="p-4"><LoadingBlock rows={4} label="Loading appointments" /></div>
        ) : profile ? (
          <FullCalendar
            plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
            initialView="timeGridWeek"
            headerToolbar={{
              left: 'prev,next today',
              center: 'title',
              right: 'timeGridDay,timeGridWeek,dayGridMonth',
            }}
            timeZone={profile.timeZone}
            nowIndicator
            height="auto"
            allDaySlot={false}
            slotMinTime="07:00:00"
            slotMaxTime="22:00:00"
            slotDuration="00:30:00"
            snapDuration="00:15:00"
            firstDay={1}
            events={events}
            datesSet={handleDatesSet}
            eventClick={handleEventClick}
            eventTimeFormat={{ hour: 'numeric', minute: '2-digit', meridiem: 'short' }}
            dayMaxEventRows={3}
            eventDidMount={(info) => {
              info.el.setAttribute('title', info.event.title)
            }}
          />
        ) : null}
        {calendarQuery.isError && (
          <div className="p-4">
            <EmptyState
              title="Couldn’t load your calendar"
              description={getErrorMessage(calendarQuery.error)}
              action={<Button size="sm" variant="outline" onClick={() => calendarQuery.refetch()}>Try again</Button>}
            />
          </div>
        )}
        {!calendarQuery.isLoading && !calendarQuery.isError && appointments.length === 0 && (
          <div className="px-4 pb-4">
            <p className="text-center text-xs text-ink-muted">
              No appointments match this calendar range and status filter.
            </p>
          </div>
        )}
      </Card>

      <p className="mt-3 text-xs text-ink-muted">
        Calendar times are displayed in {profile?.timeZone.replace('_', ' ') ?? 'your provider time zone'}.
      </p>

      <Modal
        open={Boolean(selected)}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedId(null)
            setActionError(null)
          }
        }}
        title={selected?.serviceName ?? 'Appointment'}
        description={selected ? `${formatDate(selected.startAt, selected.providerTimeZone)} · ${formatTime(selected.startAt, selected.providerTimeZone)}` : undefined}
        size="lg"
        footer={
          selected?.status === 'CONFIRMED' ? (
            <>
              <Button variant="ghost" onClick={() => setSelectedId(null)}>Close</Button>
              <Button
                variant="outline"
                onClick={() => noShowMutation.mutate(selected.id)}
                loading={noShowMutation.isPending}
              >
                Mark no-show
              </Button>
              <Button onClick={() => completeMutation.mutate(selected.id)} loading={completeMutation.isPending}>
                Mark completed
              </Button>
            </>
          ) : (
            <Button onClick={() => setSelectedId(null)}>Close</Button>
          )
        }
      >
        {selected && (
          <div className="space-y-5">
            {actionError && <Alert tone="error">{actionError}</Alert>}
            <div className="flex flex-wrap items-center gap-2">
              <StatusBadge status={selected.status} />
              <Badge tone="neutral">{selected.serviceDurationMinutes} minutes</Badge>
              <Badge tone="neutral">{selected.currency}</Badge>
            </div>
            <div className="grid gap-4 rounded-2xl border border-line bg-white/55 p-4 sm:grid-cols-2">
              <div>
                <p className="text-[0.6rem] font-bold uppercase tracking-[0.15em] text-ink-muted">Customer</p>
                <p className="mt-1 text-sm font-bold text-forest-900">{selected.customerName}</p>
                <p className="text-xs text-ink-muted">{selected.customerEmail}</p>
                {selected.customerPhone && <p className="text-xs text-ink-muted">{selected.customerPhone}</p>}
              </div>
              <div>
                <p className="text-[0.6rem] font-bold uppercase tracking-[0.15em] text-ink-muted">Time</p>
                <p className="mt-1 text-sm font-bold text-forest-900">
                  {formatTime(selected.startAt, selected.providerTimeZone)} – {formatTime(selected.endAt, selected.providerTimeZone)}
                </p>
                <p className="text-xs text-ink-muted">{formatDate(selected.startAt, selected.providerTimeZone)}</p>
                <p className="text-xs text-ink-muted">{selected.providerTimeZone.replace('_', ' ')}</p>
              </div>
            </div>
            {selected.customerNotes && (
              <div>
                <p className="text-[0.6rem] font-bold uppercase tracking-[0.15em] text-ink-muted">Customer note</p>
                <p className="mt-1 rounded-xl bg-cream-100/80 p-3 text-sm text-ink-soft">{selected.customerNotes}</p>
              </div>
            )}
            {selected.status === 'CONFIRMED' && (
              <div className="border-t border-line pt-4">
                <p className="mb-3 text-[0.6rem] font-bold uppercase tracking-[0.15em] text-ink-muted">Change appointment</p>
                <AppointmentActions appointment={selected} onChanged={refreshSelected} />
                <p className="mt-3 text-xs text-ink-muted">
                  Completion can only be recorded after the appointment has ended. No-shows can be marked once the start time has passed.
                </p>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  )
}
