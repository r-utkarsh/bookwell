import { useQuery } from '@tanstack/react-query'
import {
  ArrowRight,
  CalendarCheck2,
  Check,
  Clock3,
  HeartHandshake,
  Search,
  Sparkles,
  Store,
} from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ProviderCard } from '@/components/ProviderCard'
import { buttonClasses, Card, EmptyState, LoadingBlock } from '@/components/ui'
import { apiRequest } from '@/lib/api'
import type { PageResponse, ProviderCard as ProviderCardType } from '@/types/api'

const promises = [
  {
    icon: CalendarCheck2,
    title: 'Book in a minute',
    body: 'Real availability, clear pricing, and instant confirmation. No phone tag required.',
  },
  {
    icon: Check,
    title: 'No double bookings',
    body: 'Every slot is protected at the database level, so your time is genuinely yours.',
  },
  {
    icon: HeartHandshake,
    title: 'Built for independents',
    body: 'Made for thoughtful studios and solo practitioners, not giant call centres.',
  },
]

export function HomePage() {
  const [query, setQuery] = useState('')
  const navigate = useNavigate()

  const { data: providers, isLoading, isError, refetch } = useQuery({
    queryKey: ['providers', 'featured'],
    queryFn: () => apiRequest<PageResponse<ProviderCardType>>('/providers?page=0&size=6'),
  })

  const { data: categories } = useQuery({
    queryKey: ['provider-categories'],
    queryFn: () => apiRequest<string[]>('/providers/categories'),
  })

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    const params = new URLSearchParams()
    if (query.trim()) params.set('q', query.trim())
    navigate(`/providers${params.size ? `?${params.toString()}` : ''}`)
  }

  return (
    <>
      <section className="relative overflow-hidden">
        <div className="mx-auto grid max-w-7xl gap-14 px-4 pb-16 pt-14 sm:px-6 lg:grid-cols-[1.05fr_0.95fr] lg:items-center lg:px-8 lg:pb-24 lg:pt-24">
          <div className="relative z-10 animate-rise">
            <p className="inline-flex items-center gap-2 rounded-full border border-clay-200 bg-clay-50/70 px-3 py-1.5 text-[0.65rem] font-bold uppercase tracking-[0.18em] text-clay-700">
              <Sparkles className="size-3.5" aria-hidden="true" />
              Appointments, without the back-and-forth
            </p>
            <h1 className="mt-6 max-w-2xl text-[2.65rem] leading-[1.05] tracking-[-0.035em] text-forest-900 sm:text-6xl lg:text-[4.2rem]">
              Your next <em className="font-normal text-clay-600">good</em> appointment starts here.
            </h1>
            <p className="mt-6 max-w-xl text-base leading-relaxed text-ink-soft sm:text-lg">
              Find independent salons, studios, and wellness practitioners who care about the details.
              See real availability, book in a few taps, and get back to your day.
            </p>

            <form onSubmit={handleSearch} className="mt-8 max-w-xl" role="search">
              <div className="flex flex-col gap-2 rounded-[1.2rem] border border-line-strong bg-white/75 p-2 shadow-soft backdrop-blur sm:flex-row">
                <div className="relative flex-1">
                  <Search className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-ink-muted" aria-hidden="true" />
                  <label htmlFor="home-search" className="sr-only">
                    Search providers or services
                  </label>
                  <input
                    id="home-search"
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Try a studio name, service, or city"
                    className="w-full rounded-xl border-0 bg-transparent py-3 pl-11 pr-4 text-sm text-ink outline-none placeholder:text-ink-muted/70"
                  />
                </div>
                <button type="submit" className={buttonClasses('primary', 'md')}>
                  Find your person <ArrowRight className="size-4" aria-hidden="true" />
                </button>
              </div>
            </form>

            {categories && categories.length > 0 && (
              <div className="mt-5 flex flex-wrap items-center gap-2">
                <span className="text-xs font-semibold text-ink-muted">Popular:</span>
                {categories.slice(0, 4).map((category) => (
                  <Link
                    key={category}
                    to={`/providers?category=${encodeURIComponent(category)}`}
                    className="rounded-full border border-line bg-cream-50/80 px-3 py-1.5 text-xs font-semibold text-forest-800 transition hover:border-forest-300 hover:bg-forest-50"
                  >
                    {category}
                  </Link>
                ))}
              </div>
            )}
          </div>

          <div className="relative mx-auto w-full max-w-md animate-rise-delayed lg:max-w-none">
            <div className="absolute -left-10 top-10 size-52 rounded-full bg-gold-300/20 blur-3xl" aria-hidden="true" />
            <div className="absolute -bottom-8 -right-8 size-64 rounded-full bg-clay-300/20 blur-3xl" aria-hidden="true" />
            <div className="relative rotate-[1.5deg] rounded-[2rem] border border-line bg-[#fdfaf5] p-5 shadow-lift sm:p-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-[0.6rem] font-bold uppercase tracking-[0.2em] text-ink-muted">A calmer calendar</p>
                  <p className="mt-1 font-display text-2xl text-forest-900">Thursday, 14 November</p>
                </div>
                <span className="grid size-11 place-items-center rounded-full bg-forest-900 text-cream-50">
                  <CalendarCheck2 className="size-5" aria-hidden="true" />
                </span>
              </div>
              <div className="mt-6 rounded-2xl bg-cream-100 p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-xs font-bold uppercase tracking-[0.12em] text-clay-700">Your appointment</p>
                    <p className="mt-2 font-display text-xl text-forest-900">Signature Haircut</p>
                    <p className="mt-1 text-xs text-ink-muted">Aarohi Studio · Indiranagar</p>
                  </div>
                  <span className="rounded-full bg-forest-100 px-2.5 py-1 text-[0.6rem] font-bold uppercase tracking-wider text-forest-700">
                    Confirmed
                  </span>
                </div>
                <div className="mt-4 flex items-center gap-3 border-t border-line/80 pt-4 text-sm">
                  <Clock3 className="size-4 text-clay-600" aria-hidden="true" />
                  <span className="font-semibold text-forest-900">11:30 AM – 12:15 PM</span>
                  <span className="ml-auto font-bold text-forest-800">₹899</span>
                </div>
              </div>
              <p className="mt-5 px-1 text-xs font-semibold uppercase tracking-[0.16em] text-ink-muted">Also today</p>
              <div className="mt-3 space-y-2">
                {[
                  ['Glow Facial', '2:30 PM', '60 min'],
                  ['Head Massage', '4:00 PM', '30 min'],
                  ['Hair Colour', '5:15 PM', '90 min'],
                ].map(([service, time, duration]) => (
                  <div key={service} className="flex items-center gap-3 rounded-xl border border-line/80 bg-white/60 px-4 py-3">
                    <span className="size-2 rounded-full bg-gold-500" aria-hidden="true" />
                    <span className="text-sm font-semibold text-forest-900">{service}</span>
                    <span className="ml-auto text-xs font-bold text-ink-soft">{time}</span>
                    <span className="text-[0.65rem] text-ink-muted">{duration}</span>
                  </div>
                ))}
              </div>
            </div>
            <div className="absolute -bottom-6 -left-4 rotate-[-4deg] rounded-2xl border border-line bg-forest-900 px-5 py-3 text-cream-50 shadow-lift sm:-left-8">
              <p className="text-[0.58rem] font-bold uppercase tracking-[0.18em] text-cream-300/70">Next available</p>
              <p className="mt-1 font-display text-lg">Tomorrow · 10:00 AM</p>
            </div>
          </div>
        </div>
      </section>

      <section className="border-y border-line bg-white/35">
        <div className="mx-auto grid max-w-7xl gap-6 px-4 py-10 sm:px-6 md:grid-cols-3 lg:px-8">
          {promises.map((promise) => (
            <div key={promise.title} className="flex gap-4">
              <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-forest-100 text-forest-700">
                <promise.icon className="size-4.5" aria-hidden="true" />
              </span>
              <div>
                <h2 className="font-sans text-sm font-bold tracking-normal text-forest-900">{promise.title}</h2>
                <p className="mt-1 text-sm leading-relaxed text-ink-muted">{promise.body}</p>
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-[0.65rem] font-bold uppercase tracking-[0.2em] text-clay-600">A few good people</p>
            <h2 className="mt-2 text-4xl tracking-tight sm:text-5xl">Studios worth making time for.</h2>
            <p className="mt-3 max-w-xl text-sm leading-relaxed text-ink-soft">
              Independent businesses with real services, real hours, and a booking experience that respects your time.
            </p>
          </div>
          <Link to="/providers" className={buttonClasses('outline', 'md')}>
            Explore all providers <ArrowRight className="size-4" />
          </Link>
        </div>

        <div className="mt-9 grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {isLoading ? (
            <div className="md:col-span-2 lg:col-span-3">
              <LoadingBlock label="Loading featured providers" rows={2} />
            </div>
          ) : isError ? (
            <div className="md:col-span-2 lg:col-span-3">
              <EmptyState
                title="We couldn’t load providers just now"
                description="Check your connection and try again, or browse the full directory."
                action={
                  <button type="button" onClick={() => refetch()} className={buttonClasses('outline', 'md')}>
                    Try again
                  </button>
                }
              />
            </div>
          ) : providers?.content.length ? (
            providers.content.map((provider) => <ProviderCard key={provider.id} provider={provider} />)
          ) : (
            <div className="md:col-span-2 lg:col-span-3">
              <EmptyState
                title="The first chairs are being prepared"
                description="No approved providers are listed yet. Check back soon, or apply to bring your own studio to Bookwell."
                action={
                  <Link to="/register/provider" className={buttonClasses('primary', 'md')}>
                    <Store className="size-4" /> List your business
                  </Link>
                }
              />
            </div>
          )}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 pb-20 sm:px-6 lg:px-8">
        <div className="overflow-hidden rounded-[2.2rem] bg-forest-900 px-6 py-12 text-cream-50 sm:px-10 lg:px-16 lg:py-16">
          <div className="grid gap-12 lg:grid-cols-[0.85fr_1.15fr] lg:items-center">
            <div>
              <p className="text-[0.65rem] font-bold uppercase tracking-[0.2em] text-gold-300">How it works</p>
              <h2 className="mt-3 max-w-md text-4xl leading-tight tracking-tight text-cream-50 sm:text-5xl">
                From “I need a reset” to booked.
              </h2>
              <p className="mt-4 max-w-sm text-sm leading-relaxed text-cream-200/75">
                Three small steps, one well-kept calendar. You will see the exact time and price before anything is
                confirmed.
              </p>
              <Link to="/providers" className={buttonClasses('gold', 'lg') + ' mt-7'}>
                Start exploring <ArrowRight className="size-4" />
              </Link>
            </div>
            <ol className="space-y-4">
              {[
                ['01', 'Choose your person', 'Browse by category, city, or the service you need. Every listing is reviewed before it goes live.'],
                ['02', 'Pick a time that works', 'Availability is calculated from their real hours, breaks, and existing bookings — not a best guess.'],
                ['03', 'Arrive, unwind, repeat', 'Instant confirmation lands in your inbox. Manage or reschedule from your account when plans change.'],
              ].map(([number, title, body]) => (
                <li key={number} className="flex gap-5 rounded-2xl border border-white/10 bg-white/[0.04] p-5">
                  <span className="font-display text-2xl text-gold-300">{number}</span>
                  <div>
                    <h3 className="font-sans text-sm font-bold tracking-normal text-cream-50">{title}</h3>
                    <p className="mt-1.5 text-sm leading-relaxed text-cream-200/70">{body}</p>
                  </div>
                </li>
              ))}
            </ol>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 pb-20 sm:px-6 lg:px-8">
        <Card className="flex flex-col items-start justify-between gap-6 p-7 sm:flex-row sm:items-center sm:p-9">
          <div className="flex items-start gap-4">
            <span className="grid size-12 shrink-0 place-items-center rounded-2xl bg-clay-100 text-clay-700">
              <Store className="size-5" aria-hidden="true" />
            </span>
            <div>
              <h2 className="font-display text-2xl">Run a studio that runs beautifully?</h2>
              <p className="mt-1 max-w-xl text-sm text-ink-soft">
                Join a booking platform that puts your time first. Applications are reviewed by a real person, and
                pricing for early providers is simple: free while you grow.
              </p>
            </div>
          </div>
          <Link to="/register/provider" className={buttonClasses('primary', 'md')}>
            List your business <ArrowRight className="size-4" />
          </Link>
        </Card>
      </section>

      <section className="mx-auto max-w-7xl px-4 pb-4 sm:px-6 lg:px-8">
        <div className="grid gap-4 rounded-[1.8rem] border border-line bg-cream-100/60 p-6 sm:grid-cols-3 sm:p-8">
          <div>
            <p className="text-[0.6rem] font-bold uppercase tracking-[0.2em] text-ink-muted">Clear pricing</p>
            <p className="mt-2 font-display text-2xl">No surprise add-ons</p>
            <p className="mt-1 text-xs text-ink-muted">Every service shows its price and duration up front.</p>
          </div>
          <div>
            <p className="text-[0.6rem] font-bold uppercase tracking-[0.2em] text-ink-muted">Local time, always</p>
            <p className="mt-2 font-display text-2xl">No time-zone maths</p>
            <p className="mt-1 text-xs text-ink-muted">Availability is shown in your provider&apos;s working time zone.</p>
          </div>
          <div>
            <p className="text-[0.6rem] font-bold uppercase tracking-[0.2em] text-ink-muted">Plans change</p>
            <p className="mt-2 font-display text-2xl">Change it online</p>
            <p className="mt-1 text-xs text-ink-muted">Reschedule or cancel from your account within the provider&apos;s window.</p>
          </div>
        </div>
      </section>
    </>
  )
}
