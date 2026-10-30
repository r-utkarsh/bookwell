import {
  BellRing,
  CalendarDays,
  CalendarRange,
  ChartNoAxesColumn,
  Clock3,
  LayoutDashboard,
  LogOut,
  Scissors,
  Store,
  UserRound,
  UsersRound,
} from 'lucide-react'
import { Suspense } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { Brand } from '@/components/Brand'
import { Avatar, Badge, buttonClasses, FullPageLoader, StatusBadge } from '@/components/ui'
import { cn } from '@/lib/cn'
import type { Role } from '@/types/api'

interface NavItem {
  to: string
  label: string
  end?: boolean
  icon: typeof LayoutDashboard
}

const providerNav: NavItem[] = [
  { to: '/provider', label: 'Overview', end: true, icon: LayoutDashboard },
  { to: '/provider/calendar', label: 'Calendar', icon: CalendarDays },
  { to: '/provider/services', label: 'Services', icon: Scissors },
  { to: '/provider/availability', label: 'Availability', icon: Clock3 },
  { to: '/provider/profile', label: 'Profile', icon: UserRound },
]

const adminNav: NavItem[] = [
  { to: '/admin', label: 'Overview', end: true, icon: ChartNoAxesColumn },
  { to: '/admin/providers', label: 'Providers', icon: Store },
  { to: '/admin/customers', label: 'Customers', icon: UsersRound },
  { to: '/admin/bookings', label: 'Bookings', icon: CalendarRange },
  { to: '/admin/notifications', label: 'Notifications', icon: BellRing },
]

export function AppShell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const role: Role = user?.role ?? 'CUSTOMER'
  const items = role === 'ADMIN' ? adminNav : providerNav

  async function handleLogout() {
    await logout()
    navigate('/')
  }

  return (
    <div className="min-h-screen lg:flex">
      <aside className="sticky top-0 z-30 hidden h-screen w-72 shrink-0 border-r border-line bg-cream-100/70 lg:flex lg:flex-col">
        <div className="p-7">
          <Brand />
        </div>
        <div className="mx-5 rounded-2xl border border-line bg-white/55 p-4">
          <p className="text-[0.6rem] font-bold uppercase tracking-[0.18em] text-ink-muted">
            {role === 'ADMIN' ? 'Platform admin' : 'Provider studio'}
          </p>
          <p className="mt-2 truncate text-sm font-bold text-forest-900">{user?.fullName}</p>
          <p className="truncate text-xs text-ink-muted">{user?.email}</p>
          {user?.providerStatus && (
            <div className="mt-3">
              <StatusBadge status={user.providerStatus} />
            </div>
          )}
        </div>
        <nav className="mt-6 flex-1 space-y-1 px-4" aria-label={`${role.toLowerCase()} navigation`}>
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-xl px-3.5 py-3 text-sm font-semibold transition',
                  isActive
                    ? 'bg-forest-900 text-cream-50 shadow-sm'
                    : 'text-ink-soft hover:bg-cream-200/70 hover:text-forest-900',
                )
              }
            >
              <item.icon className="size-4.5 shrink-0" aria-hidden="true" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="p-4">
          <button
            type="button"
            onClick={handleLogout}
            className="flex w-full items-center gap-3 rounded-xl px-3.5 py-3 text-sm font-semibold text-ink-muted transition hover:bg-cream-200/70 hover:text-forest-900"
          >
            <LogOut className="size-4" aria-hidden="true" />
            Sign out
          </button>
          <NavLink to="/" className="mt-1 block rounded-xl px-3.5 py-2 text-xs font-semibold text-ink-muted hover:text-forest-900">
            ← Back to public site
          </NavLink>
        </div>
      </aside>

      <div className="min-w-0 flex-1">
        <header className="sticky top-0 z-30 border-b border-line bg-cream-50/90 backdrop-blur-xl lg:hidden">
          <div className="flex h-16 items-center justify-between px-4">
            <Brand compact />
            <div className="flex items-center gap-2">
              {user?.providerStatus && <StatusBadge status={user.providerStatus} />}
              <NavLink to="/" className={buttonClasses('ghost', 'sm')}>
                Public site
              </NavLink>
              <button
                type="button"
                onClick={handleLogout}
                className="grid size-9 place-items-center rounded-full border border-line bg-white/60 text-ink-soft"
                aria-label="Sign out"
              >
                <LogOut className="size-4" />
              </button>
            </div>
          </div>
          <nav className="no-scrollbar flex gap-1 overflow-x-auto px-3 pb-3" aria-label="Dashboard navigation">
            {items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  cn(
                    'flex shrink-0 items-center gap-2 rounded-full px-3.5 py-2 text-xs font-bold transition',
                    isActive ? 'bg-forest-900 text-cream-50' : 'bg-white/50 text-ink-soft',
                  )
                }
              >
                <item.icon className="size-3.5" aria-hidden="true" />
                {item.label}
              </NavLink>
            ))}
          </nav>
        </header>

        <div className="hidden border-b border-line bg-cream-100/45 px-8 py-3 lg:block">
          <div className="mx-auto flex max-w-6xl items-center justify-between text-xs text-ink-muted">
            <p>
              Signed in as <span className="font-semibold text-forest-800">{user?.email}</span>
            </p>
            <div className="flex items-center gap-3">
              <Badge tone="green">{role === 'ADMIN' ? 'Administrator' : 'Provider account'}</Badge>
              <Avatar name={user?.fullName ?? 'Bookwell'} size="sm" />
            </div>
          </div>
        </div>

        <main id="main-content" className="mx-auto max-w-6xl px-4 py-7 sm:px-6 lg:px-8 lg:py-10">
          <Suspense fallback={<FullPageLoader label="Opening your studio" />}>
            <Outlet />
          </Suspense>
        </main>
      </div>
    </div>
  )
}
