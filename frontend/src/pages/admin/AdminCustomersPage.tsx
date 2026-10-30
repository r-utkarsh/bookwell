import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Search, UserRoundCheck, UserRoundX } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { toast } from 'sonner'
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  Input,
  LoadingBlock,
  PageHeader,
  Pagination,
  Select,
} from '@/components/ui'
import { apiRequest, getErrorMessage } from '@/lib/api'
import { formatDate } from '@/lib/format'
import type { AdminCustomer, PageResponse } from '@/types/api'

export function AdminCustomersPage() {
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const [appliedQuery, setAppliedQuery] = useState('')
  const [enabledFilter, setEnabledFilter] = useState('')
  const [page, setPage] = useState(0)
  const [target, setTarget] = useState<{ customer: AdminCustomer; enable: boolean } | null>(null)

  const { data, isLoading, isError, error, isFetching, refetch } = useQuery({
    queryKey: ['admin-customers', appliedQuery, enabledFilter, page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '15' })
      if (appliedQuery) params.set('q', appliedQuery)
      if (enabledFilter) params.set('enabled', enabledFilter)
      return apiRequest<PageResponse<AdminCustomer>>(`/admin/customers?${params.toString()}`)
    },
  })

  const mutation = useMutation({
    mutationFn: ({ userId, enabled }: { userId: string; enabled: boolean }) =>
      apiRequest<AdminCustomer>(`/admin/customers/${userId}/status`, {
        method: 'PATCH',
        body: { enabled },
      }),
    onSuccess: (updated) => {
      toast.success(`${updated.fullName} is now ${updated.enabled ? 'enabled' : 'disabled'}.`)
      setTarget(null)
      queryClient.invalidateQueries({ queryKey: ['admin-customers'] })
      queryClient.invalidateQueries({ queryKey: ['admin-stats'] })
    },
    onError: (mutationError) => toast.error(getErrorMessage(mutationError)),
  })

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    setAppliedQuery(query.trim())
    setPage(0)
  }

  return (
    <div>
      <PageHeader
        eyebrow="Customers"
        title="The people booking."
        description="Search customer accounts and manage access. Disabling a customer revokes their refresh sessions; any access token expires shortly after."
      />

      <Card className="mt-6 p-4">
        <form onSubmit={handleSearch} className="flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-ink-muted" />
            <label htmlFor="admin-customer-search" className="sr-only">Search customers</label>
            <Input
              id="admin-customer-search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search by name, email, or phone"
              className="pl-11"
            />
          </div>
          <div className="sm:w-44">
            <label htmlFor="admin-customer-enabled" className="sr-only">Filter by access</label>
            <Select
              id="admin-customer-enabled"
              value={enabledFilter}
              onChange={(event) => {
                setEnabledFilter(event.target.value)
                setPage(0)
              }}
            >
              <option value="">All customers</option>
              <option value="true">Enabled</option>
              <option value="false">Disabled</option>
            </Select>
          </div>
          <Button type="submit" loading={isFetching}>Search</Button>
        </form>
      </Card>

      <div className="mt-5 flex items-center justify-between">
        <p className="text-sm text-ink-soft">
          {data?.totalElements ?? 0} customer{data?.totalElements === 1 ? '' : 's'}
        </p>
        <Badge tone="neutral">Customer accounts</Badge>
      </div>

      {isLoading ? (
        <div className="mt-5"><LoadingBlock rows={5} label="Loading customers" /></div>
      ) : isError ? (
        <EmptyState
          className="mt-5"
          title="Couldn’t load customers"
          description={getErrorMessage(error)}
          action={<Button variant="outline" onClick={() => refetch()}>Try again</Button>}
        />
      ) : data?.content.length ? (
        <Card className="mt-5 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead>
                <tr className="border-b border-line bg-cream-100/60 text-[0.6rem] font-bold uppercase tracking-[0.13em] text-ink-muted">
                  <th className="px-5 py-3.5">Customer</th>
                  <th className="px-4 py-3.5">Phone</th>
                  <th className="px-4 py-3.5">Joined</th>
                  <th className="px-4 py-3.5">Access</th>
                  <th className="px-5 py-3.5 text-right">Action</th>
                </tr>
              </thead>
              <tbody className={isFetching ? 'opacity-60' : undefined}>
                {data.content.map((customer) => (
                  <tr key={customer.id} className="border-b border-line/70 last:border-0 hover:bg-cream-50/60">
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <span className="grid size-9 place-items-center rounded-full bg-cream-100 font-display text-sm text-forest-800">
                          {customer.fullName.slice(0, 1)}
                        </span>
                        <div>
                          <p className="font-bold text-forest-900">{customer.fullName}</p>
                          <p className="text-xs text-ink-muted">{customer.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-4 text-xs text-ink-soft">{customer.phone || '—'}</td>
                    <td className="px-4 py-4 text-xs text-ink-muted">{formatDate(customer.createdAt)}</td>
                    <td className="px-4 py-4">
                      <Badge tone={customer.enabled ? 'green' : 'red'}>
                        {customer.enabled ? 'Enabled' : 'Disabled'}
                      </Badge>
                    </td>
                    <td className="px-5 py-4 text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        className={customer.enabled ? 'text-red-700 hover:bg-red-50' : 'text-forest-700'}
                        onClick={() => setTarget({ customer, enable: !customer.enabled })}
                      >
                        {customer.enabled ? <UserRoundX className="size-3.5" /> : <UserRoundCheck className="size-3.5" />}
                        {customer.enabled ? 'Disable' : 'Enable'}
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
          title="No customers found"
          description="Try broadening your search or clearing the access filter."
          action={<Button variant="outline" onClick={() => { setQuery(''); setAppliedQuery(''); setEnabledFilter(''); setPage(0) }}>Clear filters</Button>}
        />
      )}

      {data && (
        <Pagination
          page={data.page}
          totalPages={data.totalPages}
          onPageChange={setPage}
          className="mt-6"
        />
      )}

      <ConfirmDialog
        open={Boolean(target)}
        onOpenChange={(open) => !open && setTarget(null)}
        title={target?.enable ? 'Enable this customer?' : 'Disable this customer?'}
        description={
          target?.enable
            ? `${target.customer.fullName} will be able to sign in and book again.`
            : `${target?.customer.fullName ?? 'This customer'} will be signed out and unable to book. Existing appointments are not cancelled automatically.`
        }
        confirmLabel={target?.enable ? 'Enable customer' : 'Disable customer'}
        destructive={!target?.enable}
        loading={mutation.isPending}
        onConfirm={() =>
          target &&
          mutation.mutate({ userId: target.customer.id, enabled: target.enable })
        }
      />
    </div>
  )
}
