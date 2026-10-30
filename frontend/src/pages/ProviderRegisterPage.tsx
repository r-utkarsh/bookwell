import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowRight, Building2, Check, Clock3, ShieldCheck, Sparkles } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { z } from 'zod'
import { useAuth } from '@/auth/AuthContext'
import { Alert, Button, Field, FullPageLoader, Input, Select } from '@/components/ui'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { browserTimeZone, timezoneOptions } from '@/lib/format'
import { applyApiFieldErrors } from '@/lib/forms'
import { roleHome } from '@/lib/roles'

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

const passwordField = z
  .string()
  .min(10, 'Use at least 10 characters.')
  .max(72, 'Use no more than 72 characters.')
  .regex(
    /^(?=.*\p{L})(?=.*\d)[^\p{Cc}\u2028\u2029]+$/u,
    'Include at least one letter and one number.',
  )

const schema = z.object({
  fullName: z.string().trim().min(2, 'Enter your full name.').max(120, 'Name is too long.'),
  email: z.string().trim().min(1, 'Enter your email address.').email('Enter a valid email address.'),
  phone: z.string().trim().max(30, 'Phone number is too long.').optional(),
  password: passwordField,
  confirmPassword: z.string().min(1, 'Confirm your password.'),
  businessName: z.string().trim().min(2, 'Enter your business name.').max(160, 'Business name is too long.'),
  displayName: z.string().trim().min(2, 'Enter the name customers will see.').max(140, 'Display name is too long.'),
  category: z.string().min(1, 'Choose a category.'),
  city: z.string().trim().max(100, 'City is too long.').optional(),
  timeZone: z.string().min(1, 'Choose a time zone.'),
}).refine((values) => values.password === values.confirmPassword, {
  message: 'Passwords do not match.',
  path: ['confirmPassword'],
})

type FormValues = z.infer<typeof schema>

export function ProviderRegisterPage() {
  const { user, initializing, registerProvider } = useAuth()
  const navigate = useNavigate()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      fullName: '',
      email: '',
      phone: '',
      password: '',
      confirmPassword: '',
      businessName: '',
      displayName: '',
      category: '',
      city: '',
      timeZone: browserTimeZone(),
    },
  })
  const [serverFields, setServerFields] = useState<string[]>([])

  if (initializing) return <FullPageLoader label="Checking your session" />
  if (user) return <Navigate to={roleHome(user)} replace />

  async function onSubmit(values: FormValues) {
    setServerFields([])
    try {
      await registerProvider({
        fullName: values.fullName,
        email: values.email,
        phone: values.phone || undefined,
        password: values.password,
        businessName: values.businessName,
        displayName: values.displayName,
        category: values.category,
        city: values.city || undefined,
        timeZone: values.timeZone,
      })
      toast.success('Your provider application is in. An administrator will review it shortly.')
      navigate('/provider', { replace: true })
    } catch (error) {
      if (!applyApiFieldErrors(error, setError)) {
        setError('root', { message: getErrorMessage(error) })
      } else {
        setServerFields(getFieldErrors(error).map((field) => field.field))
      }
    }
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-12 sm:px-6 lg:px-8 lg:py-16">
      <div className="grid gap-12 lg:grid-cols-[1fr_340px] lg:items-start">
        <div>
          <p className="text-[0.65rem] font-bold uppercase tracking-[0.2em] text-clay-600">For providers</p>
          <h1 className="mt-3 max-w-2xl text-4xl leading-tight tracking-tight sm:text-5xl">
            Bring your business to a calmer calendar.
          </h1>
          <p className="mt-4 max-w-2xl text-sm leading-relaxed text-ink-soft">
            Create your provider account below. Once you submit, you can set up services, hours, and your profile
            while an administrator reviews your application. Your public listing goes live after approval.
          </p>

          <form onSubmit={handleSubmit(onSubmit)} className="mt-8 space-y-8" noValidate>
            <section className="rounded-[1.5rem] border border-line bg-white/55 p-5 shadow-soft sm:p-7">
              <div className="mb-5 flex items-center gap-3">
                <span className="grid size-9 place-items-center rounded-xl bg-forest-100 text-forest-700">
                  <Building2 className="size-4" />
                </span>
                <div>
                  <h2 className="font-display text-xl">Your business</h2>
                  <p className="text-xs text-ink-muted">This is what customers will eventually see.</p>
                </div>
              </div>
              <div className="grid gap-5 sm:grid-cols-2">
                <Field label="Business name" htmlFor="businessName" error={errors.businessName?.message} required>
                  <Input id="businessName" placeholder="Aarohi Studio" aria-invalid={Boolean(errors.businessName)} {...register('businessName')} />
                </Field>
                <Field label="Public display name" htmlFor="displayName" error={errors.displayName?.message} required>
                  <Input id="displayName" placeholder="Aarohi Mehta" aria-invalid={Boolean(errors.displayName)} {...register('displayName')} />
                </Field>
                <Field label="Category" htmlFor="category" error={errors.category?.message} required>
                  <Select id="category" aria-invalid={Boolean(errors.category)} {...register('category')}>
                    <option value="">Choose a category</option>
                    {categories.map((category) => (
                      <option key={category} value={category}>
                        {category}
                      </option>
                    ))}
                  </Select>
                </Field>
                <Field label="City" htmlFor="city" error={errors.city?.message}>
                  <Input id="city" placeholder="Bengaluru" aria-invalid={Boolean(errors.city)} {...register('city')} />
                </Field>
                <Field
                  label="Working time zone"
                  htmlFor="timeZone"
                  error={errors.timeZone?.message}
                  hint="Appointments and availability will use this time zone."
                  required
                  className="sm:col-span-2"
                >
                  <Select id="timeZone" aria-invalid={Boolean(errors.timeZone)} {...register('timeZone')}>
                    {timezoneOptions().map((zone) => (
                      <option key={zone} value={zone}>
                        {zone.replace(/_/g, ' ')}
                      </option>
                    ))}
                  </Select>
                </Field>
              </div>
            </section>

            <section className="rounded-[1.5rem] border border-line bg-white/55 p-5 shadow-soft sm:p-7">
              <div className="mb-5 flex items-center gap-3">
                <span className="grid size-9 place-items-center rounded-xl bg-clay-100 text-clay-700">
                  <ShieldCheck className="size-4" />
                </span>
                <div>
                  <h2 className="font-display text-xl">Your account</h2>
                  <p className="text-xs text-ink-muted">Use this login to manage your business.</p>
                </div>
              </div>
              <div className="grid gap-5 sm:grid-cols-2">
                <Field label="Your name" htmlFor="fullName" error={errors.fullName?.message} required>
                  <Input id="fullName" autoComplete="name" placeholder="Aarohi Mehta" aria-invalid={Boolean(errors.fullName)} {...register('fullName')} />
                </Field>
                <Field label="Phone" htmlFor="phone" error={errors.phone?.message}>
                  <Input id="phone" type="tel" autoComplete="tel" placeholder="+91 98765 43210" aria-invalid={Boolean(errors.phone)} {...register('phone')} />
                </Field>
                <Field label="Email address" htmlFor="email" error={errors.email?.message} required className="sm:col-span-2">
                  <Input id="email" type="email" autoComplete="email" placeholder="studio@example.com" aria-invalid={Boolean(errors.email)} {...register('email')} />
                </Field>
                <Field
                  label="Password"
                  htmlFor="password"
                  error={errors.password?.message}
                  hint="10–72 characters, with a letter and a number."
                  required
                >
                  <Input id="password" type="password" autoComplete="new-password" aria-invalid={Boolean(errors.password)} {...register('password')} />
                </Field>
                <Field label="Confirm password" htmlFor="confirmPassword" error={errors.confirmPassword?.message} required>
                  <Input id="confirmPassword" type="password" autoComplete="new-password" aria-invalid={Boolean(errors.confirmPassword)} {...register('confirmPassword')} />
                </Field>
              </div>
            </section>

            {errors.root?.message && (
              <Alert tone="error">
                {errors.root.message}
                {serverFields.length > 0 && (
                  <span className="mt-1 block text-xs">Please check: {serverFields.join(', ')}.</span>
                )}
              </Alert>
            )}

            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-xs leading-relaxed text-ink-muted">
                By applying, you confirm you are authorised to list this business.
              </p>
              <Button type="submit" size="lg" loading={isSubmitting}>
                Submit application <ArrowRight className="size-4" />
              </Button>
            </div>
          </form>

          <p className="mt-6 text-center text-sm text-ink-soft sm:text-left">
            Already a provider?{' '}
            <Link to="/login" className="font-bold text-forest-800 underline decoration-forest-300 underline-offset-4 hover:text-clay-700">
              Sign in
            </Link>
          </p>
        </div>

        <aside className="space-y-5 lg:sticky lg:top-24">
          <div className="rounded-[1.8rem] bg-forest-900 p-7 text-cream-50">
            <span className="grid size-11 place-items-center rounded-2xl border border-white/15 bg-white/[0.06]">
              <Sparkles className="size-5 text-gold-300" />
            </span>
            <h2 className="mt-5 font-display text-2xl text-cream-50">What happens next?</h2>
            <ol className="mt-5 space-y-4">
              {[
                ['Apply', 'Tell us about your business and create your login.'],
                ['Prepare', 'Add services, weekly hours, and exceptions at your own pace.'],
                ['Review', 'An administrator checks your application.'],
                ['Go live', 'Once approved, customers can discover and book you.'],
              ].map(([title, body], index) => (
                <li key={title} className="flex gap-3">
                  <span className="grid size-7 shrink-0 place-items-center rounded-full bg-white/10 font-display text-sm text-gold-300">
                    {index + 1}
                  </span>
                  <div>
                    <p className="text-sm font-bold">{title}</p>
                    <p className="mt-0.5 text-xs leading-relaxed text-cream-200/65">{body}</p>
                  </div>
                </li>
              ))}
            </ol>
          </div>
          <div className="rounded-[1.5rem] border border-line bg-cream-100/70 p-6">
            <h3 className="flex items-center gap-2 font-sans text-sm font-bold tracking-normal text-forest-900">
              <Clock3 className="size-4" /> Built around your time
            </h3>
            <ul className="mt-4 space-y-3 text-xs leading-relaxed text-ink-soft">
              {[
                'Weekly hours and breaks, with one-off closures.',
                'Database-protected bookings and an atomic reschedule flow.',
                'Email confirmations and 24-hour reminders.',
              ].map((item) => (
                <li key={item} className="flex gap-2">
                  <Check className="mt-0.5 size-3.5 shrink-0 text-forest-600" /> {item}
                </li>
              ))}
            </ul>
          </div>
        </aside>
      </div>
    </div>
  )
}
