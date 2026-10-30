import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ExternalLink, Search, Store } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { toast } from 'sonner'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Input,
  LoadingBlock,
  PageHeader,
  Pagination,
  Select,
  StatusBadge,
} from '@/components/ui'
import { useSearchParams } from 'react-router-dom'
import { apiRequest, getErrorMessage } from '@/lib/api'
import { formatDate } from '@/lib/format'
import type { AdminProvider, PageResponse, ProviderStatus } from '@/types/api'

export function AdminProvidersPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('q') ?? '')
  const [appliedQuery, setAppliedQuery] = useState(searchParams.get('q') ?? '')
  const page = Number(searchParams.get('page') ?? '0')
  const status = (searchParams.get('status') ?? '') as '' | ProviderStatus

  const { data, isLoading, isError, error, isFetching, refetch } = useQuery({
    queryKey: ['admin-providers', appliedQuery, status, page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '15' })
      if (appliedQuery) params.set('q', appliedQuery)
      if (status) params.set('status', status)
      return apiRequest<PageResponse<AdminProvider>>(`/admin/providers?${params.toString()}`)
    },
  })

  const statusMutation = useMutation({
    mutationFn: ({ providerId, nextStatus }: { providerId: string; nextStatus: ProviderStatus }) =>
      apiRequest<AdminProvider>(`/admin/providers/${providerId}/status`, {
        method: 'PATCH',
        body: { status: nextStatus },
      }),
    onSuccess: (updated) => {
      toast.success(`${updated.businessName} is now ${updated.status.toLowerCase()}.`)
      queryClient.invalidateQueries({ queryKey: ['admin-providers'] })
      queryClient.invalidateQueries({ queryKey: ['admin-stats'] })
      queryClient.invalidateQueries({ queryKey: ['providers'] })
    },
    onError: (mutationError) => toast.error(getErrorMessage(mutationError)),
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

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    setAppliedQuery(query.trim())
    updateParams({ q: query.trim() || null })
  }

  return (
    <div>
      <PageHeader
        eyebrow="Providers"
        title="Who is on Bookwell?"
        description="Review new applications, keep approved listings healthy, and suspend accounts when needed. Customers can only discover approved providers."
      />

      <Card className="mt-6 p-4">
        <form onSubmit={handleSearch} className="flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-ink-muted" />
            <label htmlFor="admin-provider-search" className="sr-only">Search providers</label>
            <Input
              id="admin-provider-search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search business, owner, email, or city"
              className="pl-11"
            />
          </div>
          <div className="sm:w-44">
            <label htmlFor="admin-provider-status" className="sr-only">Filter by status</label>
            <Select
              id="admin-provider-status"
              value={status}
              onChange={(event) => updateParams({ status: event.target.value || null })}
            >
              <option value="">All statuses</option>
              <option value="PENDING">Pending</option>
              <option value="APPROVED">Approved</option>
              <option value="SUSPENDED">Suspended</option>
            </Select>
          </div>
          <Button type="submit" loading={isFetching}>Search</Button>
        </form>
      </Card>

      <div className="mt-5 flex items-center justify-between">
        <p className="text-sm text-ink-soft">
          {data?.totalElements ?? 0} provider{data?.totalElements === 1 ? '' : 's'}
        </p>
        <Badge tone={status === 'PENDING' ? 'amber' : 'neutral'}>{status || 'All'}</Badge>
      </div>

      {isLoading ? (
        <div className="mt-5"><LoadingBlock rows={5} label="Loading providers" /></div>
      ) : isError ? (
        <EmptyState
          className="mt-5"
          title="Couldn’t load providers"
          description={getErrorMessage(error)}
          action={<Button variant="outline" onClick={() => refetch()}>Try again</Button>}
        />
      ) : data?.content.length ? (
        <Card className="mt-5 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[850px] text-left text-sm">
              <thead>
                <tr className="border-b border-line bg-cream-100/60 text-[0.6rem] font-bold uppercase tracking-[0.13em] text-ink-muted">
                  <th className="px-5 py-3.5">Business</th>
                  <th className="px-4 py-3.5">Owner</th>
                  <th className="px-4 py-3.5">Category</th>
                  <th className="px-4 py-3.5">Services</th>
                  <th className="px-4 py-3.5">Joined</th>
                  <th className="px-5 py-3.5">Status</th>
                </tr>
              </thead>
              <tbody className={isFetching ? 'opacity-60' : undefined}>
                {data.content.map((provider) => (
                  <tr key={provider.id} className="border-b border-line/70 last:border-0 hover:bg-cream-50/60">
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <span className="grid size-9 shrink-0 place-items-center rounded-full bg-forest-100 font-display text-sm text-forest-800">
                          {provider.displayName.slice(0, 1)}
                        </span>
                        <div className="min-w-0">
                          <p className="truncate font-bold text-forest-900">{provider.businessName}</p>
                          <p className="truncate text-xs text-ink-muted">{provider.displayName} · {provider.city || 'No city'}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-4">
                      <p className="text-xs font-semibold text-forest-900">{provider.ownerName}</p>
                      <p className="text-[0.68rem] text-ink-muted">{provider.ownerEmail}</p>
                    </td>
                    <td className="px-4 py-4 text-xs text-ink-soft">{provider.category}</td>
                    <td className="px-4 py-4 text-xs text-ink-soft">{provider.serviceCount}</td>
                    <td className="px-4 py-4 text-xs text-ink-muted">{formatDate(provider.createdAt)}</td>
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-2">
                        <StatusBadge status={provider.status} />
                        {provider.status === 'APPROVED' && (
                          <a
                            href={`/providers/${provider.id}`}
                            target="_blank"
                            rel="noreferrer"
                            className="text-ink-muted hover:text-forest-800"
                            aria-label={`Open ${provider.businessName} public page`}
                          >
                            <ExternalLink className="size-3.5" />
                          </a>
                        )}
                      </div>
                      <div className="mt-2 w-36">
                        <label htmlFor={`provider-status-${provider.id}`} className="sr-only">Change status</label>
                        <Select
                          id={`provider-status-${provider.id}`}
                          value={provider.status}
                          className="py-1.5 pl-2.5 text-xs"
                          disabled={statusMutation.isPending}
                          onChange={(event) =>
                            statusMutation.mutate({
                              providerId: provider.id,
                              nextStatus: event.target.value as ProviderStatus,
                            })
                          }
                        >
                          <option value="PENDING">Pending</option>
                          <option value="APPROVED">Approved</option>
                          <option value="SUSPENDED">Suspended</option>
                        </Select>
                      </div>
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
          icon={<Store className="size-5" />}
          title="No providers match"
          description="Try a different search or status filter."
          action={<Button variant="outline" onClick={() => { setQuery(''); setAppliedQuery(''); setSearchParams({}) }}>Clear filters</Button>}
        />
      )}

      {data && (
        <Pagination
          page={data.page}
          totalPages={data.totalPages}
          onPageChange={(next) => updateParams({ page: String(next) })}
          className="mt-6"
        />
      )}
    </div>
  )
}
