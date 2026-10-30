import {
  ArrowRight,
  CalendarCheck2,
  Check,
  Clock3,
  LayoutDashboard,
  Mail,
  ShieldCheck,
  Sparkles,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { buttonClasses, Card, PageHeader } from '@/components/ui'

const features = [
  {
    icon: CalendarCheck2,
    title: 'A calendar that holds its ground',
    body: 'Overlapping appointments are rejected by the database itself — not just hidden in the UI. Your time stays protected.',
  },
  {
    icon: Clock3,
    title: 'Availability that tells the truth',
    body: 'Set weekly hours, breaks, closures, and one-off changes. Bookwell works out the rest, down to the minute.',
  },
  {
    icon: LayoutDashboard,
    title: 'The day at a glance',
    body: 'See today’s bookings, upcoming appointments, and completed visits from one calm dashboard.',
  },
  {
    icon: Mail,
    title: 'Confirmations on autopilot',
    body: 'Customers receive booking, reschedule, cancellation, and reminder emails through a durable outbox.',
  },
  {
    icon: ShieldCheck,
    title: 'Reviewed, not a free-for-all',
    body: 'Every provider application is reviewed by an administrator before the listing becomes public.',
  },
  {
    icon: Sparkles,
    title: 'Your brand, not ours',
    body: 'Your business name, description, city, hours, and service menu stay front and centre.',
  },
]

export function ForProvidersPage() {
  return (
    <div>
      <section className="mx-auto max-w-7xl px-4 pb-10 pt-14 sm:px-6 lg:px-8 lg:pt-20">
        <div className="grid gap-12 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
          <div>
            <p className="text-[0.65rem] font-bold uppercase tracking-[0.2em] text-clay-600">For independent businesses</p>
            <h1 className="mt-4 max-w-2xl text-[2.7rem] leading-[1.04] tracking-[-0.035em] sm:text-6xl">
              Your craft deserves a calmer back office.
            </h1>
            <p className="mt-6 max-w-xl text-base leading-relaxed text-ink-soft">
              Bookwell is a booking page and calendar built for solo practitioners and small studios. Less admin,
              fewer no-shows, and a schedule that respects the work you do.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link to="/register/provider" className={buttonClasses('primary', 'lg')}>
                Apply to join <ArrowRight className="size-4" />
              </Link>
              <Link to="/providers" className={buttonClasses('outline', 'lg')}>
                See the customer side
              </Link>
            </div>
            <p className="mt-4 text-xs text-ink-muted">Applications are reviewed before your listing goes live.</p>
          </div>
          <Card className="rotate-[1deg] bg-forest-900 p-7 text-cream-50 sm:p-9">
            <p className="text-[0.65rem] font-bold uppercase tracking-[0.2em] text-gold-300">A well-kept week</p>
            <div className="mt-5 space-y-3">
              {[
                ['Tuesday', '6 appointments', '10:00 – 19:00'],
                ['Wednesday', '4 appointments', '10:00 – 19:00'],
                ['Thursday', 'Full', '10:00 – 19:00'],
                ['Friday', '5 appointments', '10:00 – 19:00'],
              ].map(([day, count, hours], index) => (
                <div
                  key={day}
                  className={`flex items-center gap-4 rounded-xl border p-3.5 ${
                    index === 2 ? 'border-gold-300/30 bg-white/10' : 'border-white/10 bg-white/[0.04]'
                  }`}
                >
                  <span className="w-24 text-sm font-semibold">{day}</span>
                  <span className="text-xs text-cream-200/70">{hours}</span>
                  <span className="ml-auto rounded-full bg-white/10 px-2.5 py-1 text-[0.6rem] font-bold uppercase tracking-wider">
                    {count}
                  </span>
                </div>
              ))}
            </div>
            <div className="mt-6 flex items-start gap-3 border-t border-white/10 pt-5">
              <Check className="mt-0.5 size-4 shrink-0 text-gold-300" />
              <p className="text-xs leading-relaxed text-cream-200/70">
                Your dashboard shows the real calendar, not a demo. Hours and services are yours to shape.
              </p>
            </div>
          </Card>
        </div>
      </section>

      <section className="border-y border-line bg-white/35">
        <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
          <PageHeader
            eyebrow="What you get"
            title="The essentials, done properly."
            description="No bloated suite of tools you will never open. Just the parts of running a booking-based business that need to work."
          />
          <div className="mt-8 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {features.map((feature) => (
              <Card key={feature.title} className="p-6">
                <span className="grid size-11 place-items-center rounded-xl bg-forest-100 text-forest-700">
                  <feature.icon className="size-5" aria-hidden="true" />
                </span>
                <h3 className="mt-4 font-sans text-sm font-bold tracking-normal text-forest-900">{feature.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-ink-soft">{feature.body}</p>
              </Card>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
        <div className="grid gap-10 rounded-[2rem] border border-line bg-cream-100/60 p-6 sm:p-10 lg:grid-cols-[0.8fr_1.2fr]">
          <div>
            <p className="text-[0.65rem] font-bold uppercase tracking-[0.2em] text-clay-600">Getting started</p>
            <h2 className="mt-3 text-4xl tracking-tight">One application. Then your calendar.</h2>
            <p className="mt-4 text-sm leading-relaxed text-ink-soft">
              Create your provider account, tell us about your business, and add your services and hours. An
              administrator reviews your application before customers can find you.
            </p>
            <Link to="/register/provider" className={buttonClasses('primary', 'lg') + ' mt-7'}>
              Start your application <ArrowRight className="size-4" />
            </Link>
          </div>
          <ol className="grid gap-4 sm:grid-cols-2">
            {[
              ['01', 'Create your account', 'Choose your business name, category, city, and working time zone.'],
              ['02', 'Wait for review', 'A real administrator checks your details. You can prepare your profile meanwhile.'],
              ['03', 'Shape your menu', 'Add services with clear prices and durations, then set weekly hours and exceptions.'],
              ['04', 'Share your page', 'Once approved, customers can discover you and book real, protected slots.'],
            ].map(([step, title, body]) => (
              <li key={step} className="rounded-2xl border border-line bg-white/55 p-5">
                <span className="font-display text-xl text-clay-600">{step}</span>
                <h3 className="mt-2 font-sans text-sm font-bold tracking-normal text-forest-900">{title}</h3>
                <p className="mt-1 text-xs leading-relaxed text-ink-soft">{body}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>
    </div>
  )
}
