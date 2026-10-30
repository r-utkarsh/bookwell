import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, MapPin, NotebookPen } from 'lucide-react'
import { useState } from 'react'
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
  Pagination,
  Select,
  StatusBadge,
} from '@/components/ui'
import { apiRequest, getErrorMessage } from '@/lib/api'
import { formatDate, formatDateTime, formatTime } from '@/lib/format'
import type { Appointment, AppointmentStatus, PageResponse } from '@/types/api'

export function AdminBookingsPage() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<'ALL' | AppointmentStatus>('ALL')
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const { data, isLoading, isError, error, isFetching, refetch } = useQuery({
    queryKey: ['admin-bookings', status, page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '15' })
      if (status !== 'ALL') params.set('status', status)
      return apiRequest<PageResponse<Appointment>>(`/admin/bookings?${params.toString()}`)
    },
  })

  const selected = data?.content.find((appointment) => appointment.id === selectedId) ?? null

  const completeMutation = useMutation({
    mutationFn: (id: string) => apiRequest<Appointment>(`/appointments/${id}/complete`, { method: 'POST' }),
    onSuccess: () => {
      toast.success('Booking marked completed.')
      setSelectedId(null)
      queryClient.invalidateQueries({ queryKey: ['admin-bookings'] })
      queryClient.invalidateQueries({ queryKey: ['admin-stats'] })
    },
    onError: (mutationError) => setActionError(getErrorMessage(mutationError)),
  })

  const noShowMutation = useMutation({
    mutationFn: (id: string) => apiRequest<Appointment>(`/appointments/${id}/no-show`, { method: 'POST' }),
    onSuccess: () => {
      toast.success('Booking marked as no-show.')
      setSelectedId(null)
      queryClient.invalidateQueries({ queryKey: ['admin-bookings'] })
      queryClient.invalidateQueries({ queryKey: ['admin-stats'] })
    },
    onError: (mutationError) => setActionError(getErrorMessage(mutationError)),
  })

  function refresh() {
    queryClient.invalidateQueries({ queryKey: ['admin-bookings'] })
    queryClient.invalidateQueries({ queryKey: ['admin-stats'] })
  }

  return (
    <div>
      <PageHeader
        eyebrow="Bookings"
        title="The full appointment ledger."
        description="Search the last 30 days and the next 90 days by status. Open a booking for customer details, reschedules, and cancellations."
        actions={
          <div className="w-full sm:w-44">
            <label htmlFor="admin-booking-status" className="sr-only">Filter by booking status</label>
            <Select
              id="admin-booking-status"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as 'ALL' | AppointmentStatus)
                setPage(0)
              }}
            >
              <option value="ALL">All statuses</option>
              <option value="CONFIRMED">Confirmed</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
              <option value="NO_SHOW">No-show</option>
            </Select>
          </div>
        }
      />

      <div className="mt-5 flex items-center justify-between">
        <p className="text-sm text-ink-soft">
          {data?.totalElements ?? 0} booking{data?.totalElements === 1 ? '' : 's'}
        </p>
        <Badge tone="neutral">Default: −30d to +90d</Badge>
      </div>

      {isLoading ? (
        <div className="mt-5"><LoadingBlock rows={5} label="Loading bookings" /></div>
      ) : isError ? (
        <EmptyState
          className="mt-5"
          title="Couldn’t load bookings"
          description={getErrorMessage(error)}
          action={<Button variant="outline" onClick={() => refetch()}>Try again</Button>}
        />
      ) : data?.content.length ? (
        <Card className="mt-5 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead>
                <tr className="border-b border-line bg-cream-100/60 text-[0.6rem] font-bold uppercase tracking-[0.13em] text-ink-muted">
                  <th className="px-5 py-3.5">When</th>
                  <th className="px-4 py-3.5">Service</th>
                  <th className="px-4 py-3.5">Provider</th>
                  <th className="px-4 py-3.5">Customer</th>
                  <th className="px-4 py-3.5">Status</th>
                  <th className="px-5 py-3.5 text-right">Details</th>
                </tr>
              </thead>
              <tbody className={isFetching ? 'opacity-60' : undefined}>
                {data.content.map((appointment) => (
                  <tr key={appointment.id} className="border-b border-line/70 last:border-0 hover:bg-cream-50/60">
                    <td className="px-5 py-4">
                      <p className="text-xs font-bold text-forest-900">
                        {formatDate(appointment.startAt, appointment.providerTimeZone)}
                      </p>
                      <p className="text-[0.68rem] text-ink-muted">
                        {formatTime(appointment.startAt, appointment.providerTimeZone)} · {appointment.serviceDurationMinutes} min
                      </p>
                    </td>
                    <td className="px-4 py-4 text-xs text-ink-soft">{appointment.serviceName}</td>
                    <td className="px-4 py-4">
                      <p className="text-xs font-semibold text-forest-900">{appointment.providerBusinessName}</p>
                      <p className="text-[0.68rem] text-ink-muted">{appointment.providerCity || '—'}</p>
                    </td>
                    <td className="px-4 py-4">
                      <p className="text-xs font-semibold text-forest-900">{appointment.customerName}</p>
                      <p className="text-[0.68rem] text-ink-muted">{appointment.customerEmail}</p>
                    </td>
                    <td className="px-4 py-4"><StatusBadge status={appointment.status} /></td>
                    <td className="px-5 py-4 text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => {
                          setActionError(null)
                          setSelectedId(appointment.id)
                        }}
                      >
                        Manage
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      ) : (
        <EmptyState
          className="mt-5"
          icon={<CalendarDays className="size-5" />}
          title="No bookings in this view"
          description="Try another status or page."
        />
      )}

      {data && (
        <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} className="mt-6" />
      )}

      <Modal
        open={Boolean(selected)}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedId(null)
            setActionError(null)
          }
        }}
        title={selected?.serviceName ?? 'Booking'}
        description={selected ? `${selected.providerBusinessName} · ${formatDateTime(selected.startAt, selected.providerTimeZone)}` : undefined}
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
                No-show
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
              <Badge tone="neutral">{selected.servicePrice} {selected.currency}</Badge>
              {selected.rescheduleCount > 0 && <Badge tone="blue">Rescheduled {selected.rescheduleCount}×</Badge>}
            </div>
            <div className="grid gap-4 rounded-2xl border border-line bg-white/55 p-4 sm:grid-cols-2">
              <div>
                <p className="text-[0.6rem] font-bold uppercase tracking-[0.15em] text-ink-muted">Customer</p>
                <p className="mt-1 text-sm font-bold text-forest-900">{selected.customerName}</p>
                <p className="text-xs text-ink-muted">{selected.customerEmail}</p>
                {selected.customerPhone && <p className="text-xs text-ink-muted">{selected.customerPhone}</p>}
              </div>
              <div>
                <p className="text-[0.6rem] font-bold uppercase tracking-[0.15em] text-ink-muted">Provider</p>
                <p className="mt-1 text-sm font-bold text-forest-900">{selected.providerDisplayName}</p>
                <p className="text-xs text-ink-muted">{selected.providerBusinessName}</p>
                {selected.providerCity && (
                  <p className="mt-1 flex items-center gap-1 text-xs text-ink-muted">
                    <MapPin className="size-3" /> {selected.providerCity}
                  </p>
                )}
              </div>
            </div>
            {selected.customerNotes && (
              <p className="flex gap-2 rounded-xl bg-cream-100/70 p-3 text-xs text-ink-soft">
                <NotebookPen className="mt-0.5 size-3.5 shrink-0" /> {selected.customerNotes}
              </p>
            )}
            {selected.cancellationReason && (
              <Alert tone="warning">
                Cancelled by {selected.cancelledBy?.toLowerCase() ?? 'user'}: “{selected.cancellationReason}”
              </Alert>
            )}
            {selected.status === 'CONFIRMED' && (
              <div className="border-t border-line pt-4">
                <p className="mb-3 text-[0.6rem] font-bold uppercase tracking-[0.15em] text-ink-muted">Administrative changes</p>
                <AppointmentActions appointment={selected} onChanged={refresh} />
                <p className="mt-3 text-xs text-ink-muted">
                  Administrators can change bookings even inside the customer cutoff window. Completion and no-show actions still require the relevant appointment time to have passed.
                </p>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  )
}
