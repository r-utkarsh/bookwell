import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BellRing, RotateCcw } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  LoadingBlock,
  PageHeader,
  Pagination,
  Select,
  StatusBadge,
} from '@/components/ui'
import { apiRequest, getErrorMessage } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import type {
  AdminNotification,
  NotificationStatus,
  NotificationType,
  PageResponse,
} from '@/types/api'

export function AdminNotificationsPage() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<'' | NotificationStatus>('')
  const [type, setType] = useState<'' | NotificationType>('')
  const [page, setPage] = useState(0)

  const { data, isLoading, isError, error, isFetching, refetch } = useQuery({
    queryKey: ['admin-notifications', status, type, page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '20' })
      if (status) params.set('status', status)
      if (type) params.set('type', type)
      return apiRequest<PageResponse<AdminNotification>>(`/admin/notifications?${params.toString()}`)
    },
  })

  const retryMutation = useMutation({
    mutationFn: (id: string) =>
      apiRequest<AdminNotification>(`/admin/notifications/${id}/retry`, { method: 'POST' }),
    onSuccess: () => {
      toast.success('Notification returned to the outbox.')
      queryClient.invalidateQueries({ queryKey: ['admin-notifications'] })
      queryClient.invalidateQueries({ queryKey: ['admin-stats'] })
    },
    onError: (mutationError) => toast.error(getErrorMessage(mutationError)),
  })

  return (
    <div>
      <PageHeader
        eyebrow="Notifications"
        title="The outbox."
        description="Every booking, cancellation, reschedule, and reminder creates a durable notification. Failed messages can be retried safely."
      />

      <Card className="mt-6 p-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_auto]">
          <div>
            <label htmlFor="notification-status" className="sr-only">Filter by status</label>
            <Select
              id="notification-status"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as '' | NotificationStatus)
                setPage(0)
              }}
            >
              <option value="">All statuses</option>
              <option value="PENDING">Pending</option>
              <option value="SENDING">Sending</option>
              <option value="SENT">Sent</option>
              <option value="FAILED">Failed</option>
              <option value="SKIPPED">Skipped</option>
            </Select>
          </div>
          <div>
            <label htmlFor="notification-type" className="sr-only">Filter by type</label>
            <Select
              id="notification-type"
              value={type}
              onChange={(event) => {
                setType(event.target.value as '' | NotificationType)
                setPage(0)
              }}
            >
              <option value="">All types</option>
              <option value="BOOKING_CONFIRMATION">Booking confirmation</option>
              <option value="CANCELLATION">Cancellation</option>
              <option value="RESCHEDULE">Reschedule</option>
              <option value="REMINDER">Reminder</option>
            </Select>
          </div>
          <Badge tone="neutral" className="self-center justify-self-start">{data?.totalElements ?? 0} messages</Badge>
        </div>
      </Card>

      {isLoading ? (
        <div className="mt-5"><LoadingBlock rows={5} label="Loading notifications" /></div>
      ) : isError ? (
        <EmptyState
          className="mt-5"
          title="Couldn’t load the outbox"
          description={getErrorMessage(error)}
          action={<Button variant="outline" onClick={() => refetch()}>Try again</Button>}
        />
      ) : data?.content.length ? (
        <Card className="mt-5 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[940px] text-left text-sm">
              <thead>
                <tr className="border-b border-line bg-cream-100/60 text-[0.6rem] font-bold uppercase tracking-[0.13em] text-ink-muted">
                  <th className="px-5 py-3.5">Recipient</th>
                  <th className="px-4 py-3.5">Type</th>
                  <th className="px-4 py-3.5">Status</th>
                  <th className="px-4 py-3.5">Attempts</th>
                  <th className="px-4 py-3.5">Scheduled / sent</th>
                  <th className="px-4 py-3.5">Delivery</th>
                  <th className="px-5 py-3.5 text-right">Action</th>
                </tr>
              </thead>
              <tbody className={isFetching ? 'opacity-60' : undefined}>
                {data.content.map((notification) => (
                  <tr key={notification.id} className="border-b border-line/70 last:border-0 align-top hover:bg-cream-50/60">
                    <td className="px-5 py-4">
                      <p className="text-xs font-semibold text-forest-900">{notification.recipientEmail}</p>
                      <p className="text-[0.65rem] text-ink-muted">Booking {notification.appointmentId.slice(0, 8)}…</p>
                    </td>
                    <td className="px-4 py-4 text-xs text-ink-soft">
                      {notification.type.replace(/_/g, ' ').toLowerCase()}
                    </td>
                    <td className="px-4 py-4"><StatusBadge status={notification.status} /></td>
                    <td className="px-4 py-4 text-xs text-ink-soft">{notification.attempts} / {notification.maxAttempts}</td>
                    <td className="px-4 py-4">
                      <p className="text-[0.68rem] text-ink-soft">{formatDateTime(notification.scheduledAt)}</p>
                      {notification.sentAt && (
                        <p className="text-[0.62rem] text-forest-600">Sent {formatDateTime(notification.sentAt)}</p>
                      )}
                    </td>
                    <td className="px-4 py-4">
                      <Badge tone={notification.deliveryMode === 'LOG' ? 'amber' : 'neutral'}>
                        {notification.deliveryMode || '—'}
                      </Badge>
                      {notification.lastError && (
                        <p className="mt-1 max-w-48 truncate text-[0.62rem] text-red-700" title={notification.lastError}>
                          {notification.lastError}
                        </p>
                      )}
                    </td>
                    <td className="px-5 py-4 text-right">
                      {(notification.status === 'FAILED' || notification.status === 'SKIPPED') && (
                        <Button
                          variant="outline"
                          size="sm"
                          loading={retryMutation.isPending}
                          onClick={() => retryMutation.mutate(notification.id)}
                        >
                          <RotateCcw className="size-3" /> Retry
                        </Button>
                      )}
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
          icon={<BellRing className="size-5" />}
          title="No notifications in this view"
          description="Try another status or type. New booking activity will appear here immediately."
        />
      )}

      {data && (
        <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} className="mt-6" />
      )}

      <p className="mt-5 text-xs text-ink-muted">
        Delivery is at-least-once. A retry can produce a duplicate email in rare failure windows; this is an accepted trade-off documented in the deployment guide.
      </p>
    </div>
  )
}
