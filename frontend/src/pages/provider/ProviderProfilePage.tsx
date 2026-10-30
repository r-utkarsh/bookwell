import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ExternalLink, MapPin, Save, Store } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import {
  Alert,
  Button,
  Card,
  Field,
  Input,
  LoadingBlock,
  PageHeader,
  Select,
  StatusBadge,
  Textarea,
} from '@/components/ui'
import { apiRequest, getErrorMessage } from '@/lib/api'
import { applyApiFieldErrors } from '@/lib/forms'
import { timezoneOptions } from '@/lib/format'
import type { ProviderDashboardProfile } from '@/types/api'

const categories = [
  'Salon & Spa',
  'Hair Studio',
  'Beauty',
  'Wellness',
  'Massage',
  'Nails',
  'Makeup',
  'Skincare',
  'Barber',
  'Other',
]

const schema = z.object({
  displayName: z.string().trim().min(2, 'Enter your public name.').max(140, 'Name is too long.'),
  businessName: z.string().trim().min(2, 'Enter your business name.').max(160, 'Business name is too long.'),
  category: z.string().trim().min(1, 'Choose a category.').max(60, 'Category is too long.'),
  description: z.string().trim().max(4000, 'Description is too long.').optional(),
  city: z.string().trim().max(100, 'City is too long.').optional(),
  address: z.string().trim().max(240, 'Address is too long.').optional(),
  phone: z.string().trim().max(30, 'Phone number is too long.').optional(),
  timeZone: z.string().min(1, 'Choose a time zone.').max(64, 'Time zone is too long.'),
})

type FormValues = z.infer<typeof schema>

export function ProviderProfilePage() {
  const queryClient = useQueryClient()
  const [serverError, setServerError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data: profile, isLoading, isError, error } = useQuery({
    queryKey: ['provider-profile'],
    queryFn: () => apiRequest<ProviderDashboardProfile>('/provider/profile'),
  })

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      displayName: '',
      businessName: '',
      category: '',
      description: '',
      city: '',
      address: '',
      phone: '',
      timeZone: 'Asia/Kolkata',
    },
  })

  useEffect(() => {
    if (profile) {
      reset({
        displayName: profile.displayName,
        businessName: profile.businessName,
        category: profile.category,
        description: profile.description ?? '',
        city: profile.city ?? '',
        address: profile.address ?? '',
        phone: profile.phone ?? '',
        timeZone: profile.timeZone,
      })
    }
  }, [profile, reset])

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      apiRequest<ProviderDashboardProfile>('/provider/profile', {
        method: 'PUT',
        body: values,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['provider-profile'], updated)
      queryClient.invalidateQueries({ queryKey: ['provider', updated.id] })
      queryClient.invalidateQueries({ queryKey: ['providers'] })
      queryClient.invalidateQueries({ queryKey: ['provider-categories'] })
      toast.success('Profile updated.')
      setServerError(null)
      setSaved(true)
      reset({
        displayName: updated.displayName,
        businessName: updated.businessName,
        category: updated.category,
        description: updated.description ?? '',
        city: updated.city ?? '',
        address: updated.address ?? '',
        phone: updated.phone ?? '',
        timeZone: updated.timeZone,
      })
    },
    onError: (mutationError) => {
      if (!applyApiFieldErrors(mutationError, setError)) {
        setServerError(getErrorMessage(mutationError))
      }
    },
  })

  function onSubmit(values: FormValues) {
    setServerError(null)
    setSaved(false)
    mutation.mutate(values)
  }

  if (isLoading) return <LoadingBlock rows={5} label="Loading your profile" />

  if (isError || !profile) {
    return (
      <Alert tone="error">
        {getErrorMessage(error)}
      </Alert>
    )
  }

  return (
    <div>
      <PageHeader
        eyebrow="Profile"
        title="The face of your business."
        description="This is how customers meet you in search and on your public page. Keep it warm, specific, and true."
        actions={
          profile.status === 'APPROVED' ? (
            <a
              href={`/providers/${profile.id}`}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-2 rounded-full border border-line-strong bg-cream-50/70 px-4 py-2 text-xs font-bold text-forest-900 hover:bg-cream-100"
            >
              <ExternalLink className="size-3.5" /> View public page
            </a>
          ) : undefined
        }
      />

      <div className="mt-7 grid gap-6 lg:grid-cols-[1fr_300px] lg:items-start">
        <Card className="p-5 sm:p-7">
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
            {serverError && <Alert tone="error">{serverError}</Alert>}
            {saved && <Alert tone="success">Your profile changes are live.</Alert>}
            <div className="grid gap-5 sm:grid-cols-2">
              <Field label="Public display name" htmlFor="profile-display-name" error={errors.displayName?.message} required>
                <Input id="profile-display-name" aria-invalid={Boolean(errors.displayName)} {...register('displayName')} />
              </Field>
              <Field label="Business name" htmlFor="profile-business-name" error={errors.businessName?.message} required>
                <Input id="profile-business-name" aria-invalid={Boolean(errors.businessName)} {...register('businessName')} />
              </Field>
              <Field label="Category" htmlFor="profile-category" error={errors.category?.message} required>
                <Select id="profile-category" aria-invalid={Boolean(errors.category)} {...register('category')}>
                  {categories.map((category) => (
                    <option key={category} value={category}>{category}</option>
                  ))}
                </Select>
              </Field>
              <Field label="City" htmlFor="profile-city" error={errors.city?.message}>
                <Input id="profile-city" aria-invalid={Boolean(errors.city)} {...register('city')} />
              </Field>
              <Field label="Address" htmlFor="profile-address" error={errors.address?.message} className="sm:col-span-2">
                <Input id="profile-address" aria-invalid={Boolean(errors.address)} {...register('address')} />
              </Field>
              <Field label="Phone" htmlFor="profile-phone" error={errors.phone?.message}>
                <Input id="profile-phone" type="tel" aria-invalid={Boolean(errors.phone)} {...register('phone')} />
              </Field>
              <Field
                label="Working time zone"
                htmlFor="profile-time-zone"
                error={errors.timeZone?.message}
                hint="Cannot change while future appointments exist."
              >
                <Select id="profile-time-zone" aria-invalid={Boolean(errors.timeZone)} {...register('timeZone')}>
                  {timezoneOptions().map((zone) => (
                    <option key={zone} value={zone}>{zone.replace(/_/g, ' ')}</option>
                  ))}
                </Select>
              </Field>
            </div>
            <Field
              label="About your business"
              htmlFor="profile-description"
              error={errors.description?.message}
              hint="A few sentences about your space, your approach, and what makes an appointment feel considered."
            >
              <Textarea id="profile-description" rows={6} aria-invalid={Boolean(errors.description)} {...register('description')} />
            </Field>
            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-5">
              <p className="text-xs text-ink-muted">Changes are saved to your public listing immediately.</p>
              <Button type="submit" loading={isSubmitting || mutation.isPending} disabled={!isDirty && !saved}>
                <Save className="size-4" /> Save profile
              </Button>
            </div>
          </form>
        </Card>

        <aside className="space-y-5">
          <Card className="p-5">
            <span className="grid size-11 place-items-center rounded-xl bg-forest-100 text-forest-700">
              <Store className="size-5" />
            </span>
            <h2 className="mt-3 font-display text-xl">Listing status</h2>
            <div className="mt-3 space-y-3 text-xs">
              <div className="flex items-center justify-between">
                <span className="text-ink-muted">Status</span>
                <StatusBadge status={profile.status} />
              </div>
              <div className="flex items-center justify-between">
                <span className="text-ink-muted">Owner</span>
                <span className="font-semibold text-forest-900">{profile.ownerName}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-ink-muted">Email</span>
                <span className="max-w-40 truncate font-semibold text-forest-900">{profile.ownerEmail}</span>
              </div>
            </div>
            {profile.status === 'PENDING' && (
              <Alert tone="warning" className="mt-4 text-xs">
                An administrator is reviewing your application. Your public page is not visible yet.
              </Alert>
            )}
            {profile.status === 'SUSPENDED' && (
              <Alert tone="error" className="mt-4 text-xs">
                Your listing is suspended. Contact the platform administrator before accepting new bookings.
              </Alert>
            )}
          </Card>

          <Card className="p-5">
            <h2 className="font-display text-xl">Location preview</h2>
            <div className="mt-3 flex gap-2 text-sm text-ink-soft">
              <MapPin className="mt-0.5 size-4 shrink-0 text-clay-600" />
              <p>{[profile.address, profile.city].filter(Boolean).join(', ') || 'Add an address so customers know where to find you.'}</p>
            </div>
            <div className="mt-4 border-t border-line pt-4">
              <p className="text-[0.6rem] font-bold uppercase tracking-[0.15em] text-ink-muted">Time zone</p>
              <p className="mt-1 text-sm font-semibold text-forest-900">{profile.timeZone.replace('_', ' ')}</p>
            </div>
          </Card>
        </aside>
      </div>
    </div>
  )
}
