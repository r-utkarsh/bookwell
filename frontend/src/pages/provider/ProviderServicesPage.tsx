import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Clock3, Plus, Scissors, Sparkles } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import {
  Alert,
  Badge,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  Field,
  Input,
  LoadingBlock,
  Modal,
  PageHeader,
  Textarea,
} from '@/components/ui'
import { apiRequest, getErrorMessage } from '@/lib/api'
import { applyApiFieldErrors } from '@/lib/forms'
import { durationLabel, formatCurrency } from '@/lib/format'
import type { ServiceOffering } from '@/types/api'

const schema = z.object({
  name: z.string().trim().min(2, 'Give the service a name.').max(140, 'Name is too long.'),
  description: z.string().trim().max(4000, 'Description is too long.').optional(),
  durationMinutes: z
    .number({ error: 'Enter a duration in minutes.' })
    .int('Use whole minutes.')
    .min(5, 'Duration must be at least 5 minutes.')
    .max(720, 'Duration cannot exceed 720 minutes.'),
  price: z
    .number({ error: 'Enter a price.' })
    .min(0, 'Price cannot be negative.')
    .max(99_999_999.99, 'Price is too large.'),
  active: z.boolean(),
  sortOrder: z
    .number({ error: 'Enter a sort order.' })
    .int('Use a whole number.')
    .min(0, 'Cannot be negative.')
    .max(1000, 'Sort order is too large.'),
})

type FormValues = z.infer<typeof schema>

const defaults: FormValues = {
  name: '',
  description: '',
  durationMinutes: 45,
  price: 0,
  active: true,
  sortOrder: 0,
}

export function ProviderServicesPage() {
  const queryClient = useQueryClient()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<ServiceOffering | null>(null)
  const [deactivating, setDeactivating] = useState<ServiceOffering | null>(null)

  const { data: services, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['provider-services'],
    queryFn: () => apiRequest<ServiceOffering[]>('/provider/services'),
  })

  const deactivateMutation = useMutation({
    mutationFn: (serviceId: string) =>
      apiRequest<void>(`/provider/services/${serviceId}`, { method: 'DELETE' }),
    onSuccess: () => {
      toast.success('Service archived. It will no longer be bookable.')
      setDeactivating(null)
      queryClient.invalidateQueries({ queryKey: ['provider-services'] })
      queryClient.invalidateQueries({ queryKey: ['provider-stats'] })
    },
    onError: (mutationError) => toast.error(getErrorMessage(mutationError)),
  })

  function openNew() {
    setEditing(null)
    setFormOpen(true)
  }

  function openEdit(service: ServiceOffering) {
    setEditing(service)
    setFormOpen(true)
  }

  const activeServices = services?.filter((service) => service.active) ?? []
  const archivedServices = services?.filter((service) => !service.active) ?? []

  return (
    <div>
      <PageHeader
        eyebrow="Services"
        title="Your service menu."
        description="Clear names, durations, and prices help customers choose with confidence. Archived services stay in your history but cannot be booked."
        actions={
          <Button onClick={openNew}>
            <Plus className="size-4" /> Add service
          </Button>
        }
      />

      {isLoading ? (
        <div className="mt-7"><LoadingBlock rows={4} label="Loading services" /></div>
      ) : isError ? (
        <EmptyState
          className="mt-7"
          title="Couldn’t load your services"
          description={getErrorMessage(error)}
          action={<Button variant="outline" onClick={() => refetch()}>Try again</Button>}
        />
      ) : services?.length === 0 ? (
        <EmptyState
          className="mt-7"
          icon={<Scissors className="size-5" />}
          title="Your menu is a blank page"
          description="Add your first service with a clear duration and price. Customers will see it on your public profile once you are approved."
          action={<Button onClick={openNew}><Plus className="size-4" /> Add your first service</Button>}
        />
      ) : (
        <div className="mt-7 space-y-8">
          <section>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-sm font-bold text-forest-900">Active menu</h2>
              <span className="text-xs text-ink-muted">{activeServices.length} live</span>
            </div>
            <div className="space-y-3">
              {activeServices.length === 0 ? (
                <Card className="p-5 text-sm text-ink-muted">
                  No active services. Add one or reactivate an archived service to start taking bookings.
                </Card>
              ) : (
                activeServices.map((service) => (
                  <ServiceRow key={service.id} service={service} onEdit={openEdit} onArchive={setDeactivating} />
                ))
              )}
            </div>
          </section>

          {archivedServices.length > 0 && (
            <section>
              <div className="mb-3 flex items-center gap-2">
                <h2 className="text-sm font-bold text-forest-900">Archived</h2>
                <span className="text-xs text-ink-muted">{archivedServices.length} hidden from customers</span>
              </div>
              <div className="space-y-3 opacity-70">
                {archivedServices.map((service) => (
                  <ServiceRow key={service.id} service={service} onEdit={openEdit} onArchive={setDeactivating} />
                ))}
              </div>
            </section>
          )}
        </div>
      )}

      <div className="mt-8 rounded-2xl border border-line bg-cream-100/60 p-5">
        <h2 className="flex items-center gap-2 font-sans text-sm font-bold text-forest-900">
          <Sparkles className="size-4 text-clay-600" /> A small pricing tip
        </h2>
        <p className="mt-1 text-xs leading-relaxed text-ink-soft">
          Show the full price, not a starting-from range. Clear pricing reduces back-and-forth and helps customers
          arrive ready to enjoy the appointment.
        </p>
      </div>

      {formOpen && (
        <ServiceFormModal
          key={editing?.id ?? 'new'}
          service={editing}
          nextSortOrder={services?.length ?? 0}
          onClose={() => setFormOpen(false)}
          onSaved={() => {
            setFormOpen(false)
            queryClient.invalidateQueries({ queryKey: ['provider-services'] })
            queryClient.invalidateQueries({ queryKey: ['provider-stats'] })
          }}
        />
      )}

      <ConfirmDialog
        open={Boolean(deactivating)}
        onOpenChange={(open) => !open && setDeactivating(null)}
        title="Archive this service?"
        description={`“${deactivating?.name ?? 'This service'}” will disappear from your public menu. Existing bookings keep their snapshot details.`}
        confirmLabel="Archive service"
        destructive
        loading={deactivateMutation.isPending}
        onConfirm={() => deactivating && deactivateMutation.mutate(deactivating.id)}
      />
    </div>
  )
}

function ServiceRow({
  service,
  onEdit,
  onArchive,
}: {
  service: ServiceOffering
  onEdit: (service: ServiceOffering) => void
  onArchive: (service: ServiceOffering) => void
}) {
  return (
    <Card className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="font-display text-xl text-forest-900">{service.name}</h3>
          {!service.active && <Badge tone="neutral">Archived</Badge>}
        </div>
        {service.description && <p className="mt-1 line-clamp-2 text-sm text-ink-soft">{service.description}</p>}
        <div className="mt-2 flex flex-wrap gap-3 text-xs text-ink-muted">
          <span className="inline-flex items-center gap-1"><Clock3 className="size-3" /> {durationLabel(service.durationMinutes)}</span>
          <span>Sort order {service.sortOrder}</span>
        </div>
      </div>
      <div className="flex items-center justify-between gap-4 border-t border-line pt-4 sm:border-l sm:border-t-0 sm:pl-5 sm:pt-0">
        <div className="text-right">
          <p className="font-display text-xl text-forest-900">{formatCurrency(service.price, service.currency)}</p>
          <p className="text-[0.6rem] uppercase tracking-wider text-ink-muted">{service.active ? 'Bookable' : 'Archived'}</p>
        </div>
        <div className="flex gap-1.5">
          <Button variant="ghost" size="sm" onClick={() => onEdit(service)}>Edit</Button>
          {service.active && (
            <Button variant="ghost" size="sm" className="text-red-700 hover:bg-red-50" onClick={() => onArchive(service)}>
              Archive
            </Button>
          )}
        </div>
      </div>
    </Card>
  )
}

function ServiceFormModal({
  service,
  nextSortOrder,
  onClose,
  onSaved,
}: {
  service: ServiceOffering | null
  nextSortOrder: number
  onClose: () => void
  onSaved: () => void
}) {
  const [serverError, setServerError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: service
      ? {
          name: service.name,
          description: service.description ?? '',
          durationMinutes: service.durationMinutes,
          price: Number(service.price),
          active: service.active,
          sortOrder: service.sortOrder,
        }
      : { ...defaults, sortOrder: nextSortOrder },
  })

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      service
        ? apiRequest<ServiceOffering>(`/provider/services/${service.id}`, {
            method: 'PUT',
            body: values,
          })
        : apiRequest<ServiceOffering>('/provider/services', {
            method: 'POST',
            body: values,
          }),
    onSuccess: () => {
      toast.success(service ? 'Service updated.' : 'Service added to your menu.')
      onSaved()
    },
    onError: (error) => {
      if (!applyApiFieldErrors(error, setError)) {
        setServerError(getErrorMessage(error))
      }
    },
  })

  function onSubmit(values: FormValues) {
    setServerError(null)
    mutation.mutate(values)
  }

  return (
    <Modal
      open
      onOpenChange={(open) => !open && onClose()}
      title={service ? 'Edit service' : 'Add a service'}
      description="Customers see this name, description, duration, and price before they book."
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={isSubmitting || mutation.isPending}>Cancel</Button>
          <Button onClick={handleSubmit(onSubmit)} loading={isSubmitting || mutation.isPending}>
            {service ? 'Save changes' : 'Add service'}
          </Button>
        </>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {serverError && <Alert tone="error">{serverError}</Alert>}
        <Field label="Service name" htmlFor="service-name" error={errors.name?.message} required>
          <Input id="service-name" placeholder="Signature Haircut" aria-invalid={Boolean(errors.name)} {...register('name')} />
        </Field>
        <Field label="Description" htmlFor="service-description" error={errors.description?.message}>
          <Textarea
            id="service-description"
            rows={3}
            placeholder="What is included, and what should the customer expect?"
            aria-invalid={Boolean(errors.description)}
            {...register('description')}
          />
        </Field>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Duration (min)" htmlFor="service-duration" error={errors.durationMinutes?.message} required>
            <Input
              id="service-duration"
              type="number"
              min={5}
              max={720}
              step={5}
              aria-invalid={Boolean(errors.durationMinutes)}
              {...register('durationMinutes', { valueAsNumber: true })}
            />
          </Field>
          <Field label="Price" htmlFor="service-price" error={errors.price?.message} hint="In your listing currency." required>
            <Input
              id="service-price"
              type="number"
              min={0}
              step="0.01"
              aria-invalid={Boolean(errors.price)}
              {...register('price', { valueAsNumber: true })}
            />
          </Field>
          <Field label="Sort order" htmlFor="service-order" error={errors.sortOrder?.message}>
            <Input
              id="service-order"
              type="number"
              min={0}
              max={1000}
              aria-invalid={Boolean(errors.sortOrder)}
              {...register('sortOrder', { valueAsNumber: true })}
            />
          </Field>
        </div>
        <label htmlFor="service-active" className="flex cursor-pointer items-center gap-3 rounded-xl border border-line bg-white/60 p-4">
          <input
            id="service-active"
            type="checkbox"
            className="size-4 rounded border-line-strong accent-forest-900"
            {...register('active')}
          />
          <span>
            <span className="block text-sm font-bold text-forest-900">Bookable now</span>
            <span className="block text-xs text-ink-muted">Uncheck to keep this service on file without offering it.</span>
          </span>
        </label>
      </form>
    </Modal>
  )
}
