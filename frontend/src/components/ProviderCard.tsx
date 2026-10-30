import { ArrowUpRight, Clock3, MapPin, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Badge, Card } from '@/components/ui'
import { formatCurrency } from '@/lib/format'
import type { ProviderCard as ProviderCardType } from '@/types/api'

function monogram(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')
}

export function ProviderCard({ provider }: { provider: ProviderCardType }) {
  return (
    <Card as="article" className="group overflow-hidden transition duration-300 hover:-translate-y-1 hover:shadow-lift">
      <Link to={`/providers/${provider.id}`} className="block focus-visible:outline-none">
        <div className="relative h-40 overflow-hidden bg-forest-800">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_20%,rgba(196,154,90,0.3),transparent_45%),radial-gradient(circle_at_80%_70%,rgba(201,111,74,0.3),transparent_42%)]" />
          <div className="absolute -bottom-9 -right-3 size-36 rounded-full border border-cream-100/15" />
          <div className="absolute -bottom-2 right-6 size-24 rounded-full border border-cream-100/10" />
          <span className="absolute bottom-5 left-6 font-display text-5xl font-medium text-cream-100/90">
            {monogram(provider.displayName)}
          </span>
          <div className="absolute right-4 top-4 flex gap-2">
            {provider.featured && (
              <Badge tone="amber">
                <Sparkles className="size-3" aria-hidden="true" /> Featured
              </Badge>
            )}
          </div>
          <span className="absolute left-4 top-4 rounded-full bg-forest-900/55 px-2.5 py-1 text-[0.6rem] font-bold uppercase tracking-[0.14em] text-cream-100 backdrop-blur-sm">
            {provider.category}
          </span>
        </div>
        <div className="p-5">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h3 className="truncate font-display text-[1.35rem] text-forest-900">{provider.displayName}</h3>
              <p className="mt-0.5 truncate text-xs font-semibold text-ink-muted">{provider.businessName}</p>
            </div>
            <span className="grid size-8 shrink-0 place-items-center rounded-full bg-cream-100 text-forest-700 transition group-hover:bg-clay-500 group-hover:text-white">
              <ArrowUpRight className="size-4" aria-hidden="true" />
            </span>
          </div>
          <p className="mt-3 line-clamp-2 min-h-10 text-sm leading-relaxed text-ink-soft">
            {provider.description || 'A thoughtful independent service provider, ready to welcome you.'}
          </p>
          <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-line/80 pt-4 text-xs text-ink-muted">
            <span className="inline-flex items-center gap-1.5">
              <MapPin className="size-3.5" aria-hidden="true" />
              {provider.city || 'Online'}
            </span>
            <span className="inline-flex items-center gap-1.5">
              <Clock3 className="size-3.5" aria-hidden="true" />
              {provider.serviceCount} {provider.serviceCount === 1 ? 'service' : 'services'}
            </span>
            <span className="ml-auto font-bold text-forest-800">
              {provider.startingPrice != null
                ? `From ${formatCurrency(provider.startingPrice, provider.currency)}`
                : 'View services'}
            </span>
          </div>
        </div>
      </Link>
    </Card>
  )
}
