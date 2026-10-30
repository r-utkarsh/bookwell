import { useQuery } from '@tanstack/react-query'
import { Search, SlidersHorizontal, X } from 'lucide-react'
import { type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ProviderCard } from '@/components/ProviderCard'
import { Button, EmptyState, Input, LoadingBlock, Pagination, PageHeader, Select } from '@/components/ui'
import { apiRequest } from '@/lib/api'
import { cn } from '@/lib/cn'
import type { PageResponse, ProviderCard as ProviderCardType } from '@/types/api'

export function ProvidersPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const activeQuery = searchParams.get('q') ?? ''
  const activeCategory = searchParams.get('category') ?? ''
  const page = Number(searchParams.get('page') ?? '0')

  const { data: categories } = useQuery({
    queryKey: ['provider-categories'],
    queryFn: () => apiRequest<string[]>('/providers/categories'),
  })

  const { data, isLoading, isFetching, isError, refetch } = useQuery({
    queryKey: ['providers', 'search', activeQuery, activeCategory, page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '9' })
      if (activeQuery) params.set('q', activeQuery)
      if (activeCategory) params.set('category', activeCategory)
      return apiRequest<PageResponse<ProviderCardType>>(`/providers?${params.toString()}`)
    },
  })

  function updateParams(changes: Record<string, string | null>) {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    if (!('page' in changes)) next.delete('page')
    setSearchParams(next)
  }

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = String(new FormData(event.currentTarget).get('q') ?? '').trim()
    updateParams({ q: value || null })
  }

  function clearFilters() {
    setSearchParams({})
  }

  const hasFilters = Boolean(activeQuery || activeCategory)

  return (
    <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8 lg:py-16">
      <PageHeader
        eyebrow="Discover"
        title="Find someone who gets it."
        description="Browse independent salons, studios, and wellness providers. Filter by what you need, then pick a time that actually exists."
      />

      <div className="mt-8 rounded-[1.5rem] border border-line bg-white/55 p-4 shadow-soft sm:p-5">
        <form onSubmit={handleSearch} className="flex flex-col gap-3 lg:flex-row">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-ink-muted" aria-hidden="true" />
            <label htmlFor="provider-search" className="sr-only">
              Search providers
            </label>
            <Input
              id="provider-search"
              name="q"
              key={activeQuery}
              defaultValue={activeQuery}
              placeholder="Search by name, business, city, or service"
              className="pl-11"
            />
          </div>
          <div className="flex gap-2">
            <div className="relative flex-1 sm:min-w-52">
              <SlidersHorizontal className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-ink-muted" aria-hidden="true" />
              <label htmlFor="provider-category" className="sr-only">
                Filter by category
              </label>
              <Select
                id="provider-category"
                value={activeCategory}
                onChange={(event) => updateParams({ category: event.target.value || null })}
                className="pl-11"
              >
                <option value="">All categories</option>
                {categories?.map((category) => (
                  <option key={category} value={category}>
                    {category}
                  </option>
                ))}
              </Select>
            </div>
            <Button type="submit" loading={isFetching}>
              Search
            </Button>
          </div>
        </form>

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <span className="text-[0.65rem] font-bold uppercase tracking-[0.16em] text-ink-muted">Quick filters</span>
          {(categories?.length ? categories.slice(0, 4) : ['Salon & Spa', 'Beauty', 'Wellness']).map((category) => (
            <button
              key={category}
              type="button"
              onClick={() => updateParams({ category: activeCategory === category ? null : category })}
              className={cn(
                'rounded-full border px-3 py-1.5 text-xs font-semibold transition',
                activeCategory === category
                  ? 'border-forest-900 bg-forest-900 text-cream-50'
                  : 'border-line bg-cream-50 text-forest-800 hover:border-forest-300',
              )}
            >
              {category}
            </button>
          ))}
          {hasFilters && (
            <button
              type="button"
              onClick={clearFilters}
              className="ml-auto inline-flex items-center gap-1 text-xs font-semibold text-clay-700 hover:text-clay-800"
            >
              <X className="size-3.5" /> Clear all
            </button>
          )}
        </div>
      </div>

      <div className="mt-8 flex items-center justify-between gap-4">
        <p className="text-sm text-ink-soft" aria-live="polite">
          {isLoading ? 'Looking for providers…' : `${data?.totalElements ?? 0} provider${data?.totalElements === 1 ? '' : 's'} found`}
          {activeQuery && <span className="text-ink-muted"> for “{activeQuery}”</span>}
          {activeCategory && <span className="text-ink-muted"> in {activeCategory}</span>}
        </p>
        {data && data.totalPages > 1 && (
          <p className="hidden text-xs text-ink-muted sm:block">
            Page {data.page + 1} of {data.totalPages}
          </p>
        )}
      </div>

      <div className="mt-5">
        {isLoading ? (
          <LoadingBlock rows={4} label="Loading providers" />
        ) : isError ? (
          <EmptyState
            title="We couldn’t load providers"
            description="Check your connection and try again. Your search filters have been kept."
            action={
              <Button variant="outline" onClick={() => refetch()}>
                Try again
              </Button>
            }
          />
        ) : data?.content.length ? (
          <div className={cn('grid gap-6 md:grid-cols-2 lg:grid-cols-3', isFetching && 'opacity-60 transition')}>
            {data.content.map((provider) => (
              <ProviderCard key={provider.id} provider={provider} />
            ))}
          </div>
        ) : (
          <EmptyState
            title="No providers match that yet"
            description="Try a broader search, choose a different category, or clear your filters."
            action={
              <Button variant="outline" onClick={clearFilters}>
                Clear filters
              </Button>
            }
          />
        )}
      </div>

      {data && (
        <Pagination
          page={data.page}
          totalPages={data.totalPages}
          onPageChange={(next) => updateParams({ page: String(next) })}
          className="mt-8"
        />
      )}
    </div>
  )
}
