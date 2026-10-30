import { CalendarCheck, Menu, Search, Sparkles, X } from 'lucide-react'
import { Suspense, useEffect, useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { toast } from 'sonner'
import { useAuth } from '@/auth/AuthContext'
import { Brand } from '@/components/Brand'
import { Avatar, buttonClasses, FullPageLoader } from '@/components/ui'
import { getErrorMessage, resendVerificationRequest } from '@/lib/api'
import { cn } from '@/lib/cn'
import { roleHome } from '@/lib/roles'
import type { Role } from '@/types/api'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'rounded-full px-3 py-2 text-sm font-semibold transition',
    isActive ? 'bg-forest-50 text-forest-800' : 'text-ink-soft hover:bg-cream-100 hover:text-forest-800',
  )

const currentYear = new Date().getFullYear()

export function PublicLayout() {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)

  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : ''
    return () => {
      document.body.style.overflow = ''
    }
  }, [open])

  const dashboardLabel: Record<Role, string> = {
    CUSTOMER: 'My bookings',
    PROVIDER: 'Provider studio',
    ADMIN: 'Admin',
  }

  return (
    <div className="flex min-h-screen flex-col">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[100] focus:rounded-full focus:bg-forest-900 focus:px-5 focus:py-3 focus:text-sm focus:font-bold focus:text-white"
      >
        Skip to content
      </a>
      <header className="sticky top-0 z-40 border-b border-line/70 bg-cream-50/85 backdrop-blur-xl">
        <div className="mx-auto flex h-[4.6rem] max-w-7xl items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
          <Brand />
          <nav className="hidden items-center gap-1 md:flex" aria-label="Main navigation">
            <NavLink to="/providers" className={navLinkClass}>
              Discover providers
            </NavLink>
            <NavLink to="/for-providers" className={navLinkClass}>
              For providers
            </NavLink>
            {user && (
              <NavLink to={roleHome(user)} className={navLinkClass}>
                {dashboardLabel[user.role]}
              </NavLink>
            )}
          </nav>
          <div className="hidden items-center gap-2 md:flex">
            {user ? (
              <Link
                to={roleHome(user)}
                className="flex items-center gap-2 rounded-full border border-line-strong bg-white/50 py-1.5 pl-1.5 pr-4 text-sm font-semibold text-forest-900 transition hover:border-forest-300 hover:bg-white"
              >
                <Avatar name={user.fullName} size="sm" />
                <span className="max-w-32 truncate">{user.fullName.split(' ')[0]}</span>
              </Link>
            ) : (
              <>
                <Link to="/login" className={buttonClasses('ghost', 'md')}>
                  Sign in
                </Link>
                <Link to="/register" className={buttonClasses('primary', 'md')}>
                  Book an appointment
                </Link>
              </>
            )}
          </div>
          <button
            type="button"
            className="grid size-10 place-items-center rounded-full border border-line bg-white/60 text-forest-900 md:hidden"
            onClick={() => setOpen((value) => !value)}
            aria-label={open ? 'Close menu' : 'Open menu'}
            aria-expanded={open}
          >
            {open ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
        </div>
        {open && (
          <div className="border-t border-line bg-cream-50 px-4 pb-6 pt-4 md:hidden">
            <nav className="flex flex-col gap-1" aria-label="Mobile navigation">
              <NavLink to="/providers" className={navLinkClass} onClick={() => setOpen(false)}>
                Discover providers
              </NavLink>
              <NavLink to="/for-providers" className={navLinkClass} onClick={() => setOpen(false)}>
                For providers
              </NavLink>
              {user && (
                <NavLink to={roleHome(user)} className={navLinkClass} onClick={() => setOpen(false)}>
                  {dashboardLabel[user.role]}
                </NavLink>
              )}
            </nav>
            <div className="mt-5 flex flex-col gap-2 border-t border-line pt-5">
              {user ? (
                <Link to={roleHome(user)} className={buttonClasses('primary', 'md')} onClick={() => setOpen(false)}>
                  <Avatar name={user.fullName} size="sm" />
                  {user.fullName}
                </Link>
              ) : (
                <>
                  <Link to="/login" className={buttonClasses('outline', 'md')} onClick={() => setOpen(false)}>
                    Sign in
                  </Link>
                  <Link to="/register" className={buttonClasses('primary', 'md')} onClick={() => setOpen(false)}>
                    Create a customer account
                  </Link>
                  <Link
                    to="/register/provider"
                    className={buttonClasses('ghost', 'md')}
                    onClick={() => setOpen(false)}
                  >
                    <Sparkles className="size-4" /> List your business
                  </Link>
                </>
              )}
            </div>
          </div>
        )}
      </header>

      {user && user.emailVerified === false && (
        <div className="border-b border-amber-300 bg-amber-50 px-4 py-2.5 text-center text-sm text-amber-900">
          <span>Your email address is not verified yet. Please verify your email before booking appointments. </span>
          <button
            type="button"
            className="font-semibold underline hover:text-amber-950"
            onClick={async () => {
              try {
                await resendVerificationRequest()
                toast.success('Verification email sent! Please check your inbox.')
              } catch (err) {
                toast.error(getErrorMessage(err))
              }
            }}
          >
            Resend verification email
          </button>
        </div>
      )}

      <main id="main-content" className="flex-1">
        <Suspense fallback={<FullPageLoader label="Opening the page" />}>
          <Outlet />
        </Suspense>
      </main>

      <footer className="mt-20 border-t border-line bg-forest-900 text-cream-100">
        <div className="mx-auto grid max-w-7xl gap-10 px-4 py-14 sm:px-6 md:grid-cols-[1.5fr_1fr_1fr] lg:px-8">
          <div>
            <Brand light />
            <p className="mt-5 max-w-sm text-sm leading-relaxed text-cream-200/75">
              A calmer way to book the people who make you feel like yourself. No phone tag, no double bookings,
              no guesswork.
            </p>
          </div>
          <div>
            <p className="text-[0.65rem] font-bold uppercase tracking-[0.2em] text-cream-300/70">For customers</p>
            <div className="mt-4 flex flex-col gap-2.5 text-sm text-cream-200/80">
              <Link className="hover:text-white" to="/providers">
                <Search className="mr-2 inline size-3.5" />
                Find a provider
              </Link>
              <Link className="hover:text-white" to="/account">
                <CalendarCheck className="mr-2 inline size-3.5" />
                Manage a booking
              </Link>
              <Link className="hover:text-white" to="/register">
                Create an account
              </Link>
            </div>
          </div>
          <div>
            <p className="text-[0.65rem] font-bold uppercase tracking-[0.2em] text-cream-300/70">For businesses</p>
            <div className="mt-4 flex flex-col gap-2.5 text-sm text-cream-200/80">
              <Link className="hover:text-white" to="/for-providers">
                Why Bookwell
              </Link>
              <Link className="hover:text-white" to="/register/provider">
                List your studio
              </Link>
              <Link className="hover:text-white" to="/login">
                Provider sign in
              </Link>
            </div>
          </div>
        </div>
        <div className="border-t border-white/10">
          <div className="mx-auto flex max-w-7xl flex-col gap-2 px-4 py-5 text-xs text-cream-300/60 sm:flex-row sm:items-center sm:justify-between sm:px-6 lg:px-8">
            <p>© {currentYear} Bookwell. Thoughtfully built for independent businesses.</p>
            <p>Times shown in the provider&apos;s local time zone.</p>
          </div>
        </div>
      </footer>
    </div>
  )
}
