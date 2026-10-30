import { useQuery } from '@tanstack/react-query'
import {
  ArrowRight,
  BellRing,
  CalendarDays,
  CircleAlert,
  Clock3,
  Store,
  UsersRound,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import {
  Badge,
  buttonClasses,
  Card,
  EmptyState,
  LoadingBlock,
  PageHeader,
  StatusBadge,
  StatCard,
} from '@/components/ui'
import { apiRequest } from '@/lib/api'
import type {
  AdminNotification,
  AdminPlatformStats,
  AdminProvider,
  PageResponse,
} from '@/types/api'

export function AdminOverviewPage() {
  const { data: stats, isLoading } = useQuery({
    queryKey: ['admin-stats'],
    queryFn: () => apiRequest<AdminPlatformStats>('/admin/stats'),
  })

  const { data: pending } = useQuery({
    queryKey: ['admin-providers', 'PENDING', 'overview'],
    queryFn: () =>
      apiRequest<PageResponse<AdminProvider>>('/admin/providers?status=PENDING&page=0&size=4'),
  })

  const { data: failedNotifications } = useQuery({
    queryKey: ['admin-notifications', 'FAILED', 'overview'],
    queryFn: () =>
      apiRequest<PageResponse<AdminNotification>>('/admin/notifications?status=FAILED&page=0&size=4'),
  })

  if (isLoading) return <LoadingBlock rows={6} label="Loading platform overview" />

  return (
    <div>
      <PageHeader
        eyebrow="Platform administration"
        title="The whole picture."
        description="Provider approvals, bookings, customers, and the notification outbox — all in one place."
        actions={
          <Link to="/admin/providers" className={buttonClasses('primary', 'sm')}>
            <Store className="size-4" /> Review providers
          </Link>
        }
      />

      {stats && stats.pendingProviders > 0 && (
        <div className="mt-6 flex flex-col justify-between gap-4 rounded-2xl border border-gold-300/60 bg-gold-100/60 p-5 sm:flex-row sm:items-center">
          <div className="flex items-start gap-3">
            <CircleAlert className="mt-0.5 size-5 shrink-0 text-gold-700" />
            <div>
              <p className="text-sm font-bold text-gold-700">
                {stats.pendingProviders} provider application{stats.pendingProviders === 1 ? '' : 's'} waiting
              </p>
              <p className="mt-0.5 text-xs text-gold-700/80">
                These businesses are preparing their profiles but cannot be discovered by customers yet.
              </p>
            </div>
          </div>
          <Link to="/admin/providers?status=PENDING" className={buttonClasses('outline', 'sm')}>
            Review now <ArrowRight className="size-3.5" />
          </Link>
        </div>
      )}

      <div className="mt-7 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Customers" value={stats?.totalCustomers ?? 0} hint="registered accounts" icon={<UsersRound className="size-4" />} accent />
        <StatCard label="Providers" value={stats?.totalProviders ?? 0} hint={`${stats?.approvedProviders ?? 0} approved`} icon={<Store className="size-4" />} />
        <StatCard label="All bookings" value={stats?.totalBookings ?? 0} hint={`${stats?.upcomingConfirmedBookings ?? 0} upcoming`} icon={<CalendarDays className="size-4" />} />
        <StatCard label="Notifications" value={stats?.pendingNotifications ?? 0} hint="in the outbox" icon={<BellRing className="size-4" />} />
      </div>

      <div className="mt-8 grid gap-6 xl:grid-cols-2">
        <Card className="overflow-hidden">
          <div className="flex items-center justify-between gap-3 border-b border-line px-5 py-4">
            <div>
              <h2 className="font-display text-xl">Provider applications</h2>
              <p className="text-xs text-ink-muted">The newest businesses waiting for review.</p>
            </div>
            <Link to="/admin/providers" className="text-xs font-bold text-clay-700 hover:text-clay-800">View all</Link>
          </div>
          {pending?.content.length ? (
            <div className="divide-y divide-line">
              {pending.content.map((provider) => (
                <div key={provider.id} className="flex items-center gap-3 px-5 py-4">
                  <div className="grid size-10 shrink-0 place-items-center rounded-full bg-cream-100 font-display text-sm text-forest-800">
                    {provider.displayName.slice(0, 1)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-bold text-forest-900">{provider.businessName}</p>
                    <p className="truncate text-xs text-ink-muted">
                      {provider.ownerName} · {provider.category} · {provider.city || 'No city'}
                    </p>
                  </div>
                  <StatusBadge status={provider.status} />
                </div>
              ))}
            </div>
          ) : (
            <EmptyState
              className="rounded-none border-0 py-9"
              icon={<Clock3 className="size-5" />}
              title="No applications waiting"
              description="Every provider has been reviewed. Nice work."
            />
          )}
        </Card>

        <Card className="overflow-hidden">
          <div className="flex items-center justify-between gap-3 border-b border-line px-5 py-4">
            <div>
              <h2 className="font-display text-xl">Outbox attention</h2>
              <p className="text-xs text-ink-muted">Failed notifications can be retried here.</p>
            </div>
            <Link to="/admin/notifications" className="text-xs font-bold text-clay-700 hover:text-clay-800">View all</Link>
          </div>
          {failedNotifications?.content.length ? (
            <div className="divide-y divide-line">
              {failedNotifications.content.map((notification) => (
                <div key={notification.id} className="flex items-start gap-3 px-5 py-4">
                  <span className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-xl bg-red-50 text-red-700">
                    <CircleAlert className="size-4" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-bold text-forest-900">{notification.recipientEmail}</p>
                    <p className="truncate text-xs text-ink-muted">
                      {notification.type.replace(/_/g, ' ').toLowerCase()} · {notification.attempts}/{notification.maxAttempts} attempts
                    </p>
                    <p className="mt-0.5 truncate text-[0.65rem] text-red-700">{notification.lastError || 'No error detail'}</p>
                  </div>
                  <StatusBadge status={notification.status} />
                </div>
              ))}
            </div>
          ) : (
            <EmptyState
              className="rounded-none border-0 py-9"
              icon={<BellRing className="size-5" />}
              title="Outbox is looking healthy"
              description="No failed notifications right now. The worker is keeping up."
            />
          )}
        </Card>
      </div>

      <div className="mt-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {[
          { to: '/admin/providers', label: 'Providers', detail: `${stats?.pendingProviders ?? 0} pending approval`, icon: Store },
          { to: '/admin/customers', label: 'Customers', detail: `${stats?.totalCustomers ?? 0} accounts`, icon: UsersRound },
          { to: '/admin/bookings', label: 'Bookings', detail: `${stats?.upcomingConfirmedBookings ?? 0} upcoming`, icon: CalendarDays },
          { to: '/admin/notifications', label: 'Notifications', detail: `${stats?.failedNotifications ?? 0} failed`, icon: BellRing },
        ].map((item) => (
          <Link
            key={item.to}
            to={item.to}
            className="group rounded-2xl border border-line bg-white/55 p-4 transition hover:-translate-y-0.5 hover:border-forest-300 hover:shadow-soft"
          >
            <div className="flex items-center justify-between">
              <item.icon className="size-4 text-forest-600" />
              <ArrowRight className="size-3.5 text-ink-muted transition group-hover:translate-x-0.5 group-hover:text-forest-800" />
            </div>
            <p className="mt-3 text-sm font-bold text-forest-900">{item.label}</p>
            <p className="text-[0.65rem] text-ink-muted">{item.detail}</p>
          </Link>
        ))}
      </div>

      <Card className="mt-8 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="font-display text-xl">Notification delivery</h2>
            <p className="text-xs text-ink-muted">A quick view of where messages are landing.</p>
          </div>
          <Badge tone={stats?.failedNotifications ? 'red' : 'green'}>
            {stats?.failedNotifications ? `${stats.failedNotifications} need attention` : 'Healthy'}
          </Badge>
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-3">
          <div className="rounded-xl bg-forest-50 p-4">
            <p className="text-[0.6rem] font-bold uppercase tracking-[0.14em] text-forest-700">Sent</p>
            <p className="mt-1 font-display text-2xl text-forest-900">{stats?.sentNotifications ?? 0}</p>
          </div>
          <div className="rounded-xl bg-gold-100/60 p-4">
            <p className="text-[0.6rem] font-bold uppercase tracking-[0.14em] text-gold-700">Pending</p>
            <p className="mt-1 font-display text-2xl text-forest-900">{stats?.pendingNotifications ?? 0}</p>
          </div>
          <div className="rounded-xl bg-red-50 p-4">
            <p className="text-[0.6rem] font-bold uppercase tracking-[0.14em] text-red-700">Failed</p>
            <p className="mt-1 font-display text-2xl text-forest-900">{stats?.failedNotifications ?? 0}</p>
          </div>
        </div>
      </Card>
    </div>
  )
}
