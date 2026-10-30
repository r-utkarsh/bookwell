import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowRight, Check, Eye, EyeOff, ShieldCheck, Sparkles } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { z } from 'zod'
import { useAuth } from '@/auth/AuthContext'
import { Button, Field, FullPageLoader, Input } from '@/components/ui'
import { getErrorMessage } from '@/lib/api'
import { applyApiFieldErrors } from '@/lib/forms'
import { roleHome } from '@/lib/roles'

const passwordField = z
  .string()
  .min(10, 'Use at least 10 characters.')
  .max(72, 'Use no more than 72 characters.')
  .regex(
    /^(?=.*\p{L})(?=.*\d)[^\p{Cc}\u2028\u2029]+$/u,
    'Include at least one letter and one number.',
  )

const schema = z
  .object({
    fullName: z.string().trim().min(2, 'Enter your full name.').max(120, 'Name is too long.'),
    email: z.string().trim().min(1, 'Enter your email address.').email('Enter a valid email address.'),
    phone: z.string().trim().max(30, 'Phone number is too long.').optional(),
    password: passwordField,
    confirmPassword: z.string().min(1, 'Confirm your password.'),
  })
  .refine((values) => values.password === values.confirmPassword, {
    message: 'Passwords do not match.',
    path: ['confirmPassword'],
  })

type FormValues = z.infer<typeof schema>

export function RegisterPage() {
  const { user, initializing, registerCustomer } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [showPassword, setShowPassword] = useState(false)
  const requested = searchParams.get('redirect')
  const redirect = requested && requested.startsWith('/') && !requested.startsWith('//') ? requested : null

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: '', email: '', phone: '', password: '', confirmPassword: '' },
  })

  if (initializing) return <FullPageLoader label="Checking your session" />
  if (user) return <Navigate to={redirect ?? roleHome(user)} replace />

  async function onSubmit(values: FormValues) {
    try {
      const created = await registerCustomer({
        fullName: values.fullName,
        email: values.email,
        phone: values.phone || undefined,
        password: values.password,
      })
      toast.success(`Your account is ready, ${created.fullName.split(' ')[0]}.`)
      navigate(redirect ?? '/account', { replace: true })
    } catch (error) {
      if (!applyApiFieldErrors(error, setError)) {
        setError('root', { message: getErrorMessage(error) })
      }
    }
  }

  return (
    <div className="mx-auto grid max-w-6xl gap-10 px-4 py-12 sm:px-6 lg:grid-cols-[1fr_0.75fr] lg:items-start lg:px-8 lg:py-16">
      <div className="mx-auto w-full max-w-xl">
        <p className="text-[0.65rem] font-bold uppercase tracking-[0.2em] text-clay-600">Create an account</p>
        <h1 className="mt-3 text-4xl tracking-tight sm:text-5xl">Make time for yourself.</h1>
        <p className="mt-3 text-sm leading-relaxed text-ink-soft">
          One account for booking, rescheduling, and keeping track of the appointments you have coming up.
        </p>

        <form onSubmit={handleSubmit(onSubmit)} className="mt-8 space-y-5" noValidate>
          {errors.root?.message && (
            <div className="rounded-xl border border-red-200 bg-red-50 p-3.5 text-sm text-red-800" role="alert">
              {errors.root.message}
            </div>
          )}
          <Field label="Full name" htmlFor="fullName" error={errors.fullName?.message} required>
            <Input
              id="fullName"
              autoComplete="name"
              placeholder="Riya Sharma"
              aria-invalid={Boolean(errors.fullName)}
              {...register('fullName')}
            />
          </Field>
          <div className="grid gap-5 sm:grid-cols-2">
            <Field label="Email address" htmlFor="email" error={errors.email?.message} required>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                aria-invalid={Boolean(errors.email)}
                {...register('email')}
              />
            </Field>
            <Field label="Phone" htmlFor="phone" error={errors.phone?.message} hint="Optional, for your provider.">
              <Input
                id="phone"
                type="tel"
                autoComplete="tel"
                placeholder="+91 98765 43210"
                aria-invalid={Boolean(errors.phone)}
                {...register('phone')}
              />
            </Field>
          </div>
          <Field
            label="Password"
            htmlFor="password"
            error={errors.password?.message}
            hint="10–72 characters, with a letter and a number."
            required
          >
            <div className="relative">
              <Input
                id="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="Choose a password"
                className="pr-12"
                aria-invalid={Boolean(errors.password)}
                {...register('password')}
              />
              <button
                type="button"
                onClick={() => setShowPassword((visible) => !visible)}
                className="absolute right-1 top-1/2 grid size-9 -translate-y-1/2 place-items-center rounded-full text-ink-muted hover:bg-cream-100 hover:text-forest-900"
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
              </button>
            </div>
          </Field>
          <Field
            label="Confirm password"
            htmlFor="confirmPassword"
            error={errors.confirmPassword?.message}
            required
          >
            <Input
              id="confirmPassword"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="Repeat your password"
              aria-invalid={Boolean(errors.confirmPassword)}
              {...register('confirmPassword')}
            />
          </Field>
          <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>
            Create my account <ArrowRight className="size-4" />
          </Button>
          <p className="text-center text-xs leading-relaxed text-ink-muted">
            By creating an account, you agree to use Bookwell respectfully and keep your appointment details accurate.
          </p>
        </form>

        <p className="mt-6 text-center text-sm text-ink-soft">
          Already have an account?{' '}
          <Link to="/login" className="font-bold text-forest-800 underline decoration-forest-300 underline-offset-4 hover:text-clay-700">
            Sign in
          </Link>
        </p>
      </div>

      <aside className="rounded-[1.8rem] border border-line bg-cream-100/60 p-7 lg:sticky lg:top-24">
        <span className="grid size-11 place-items-center rounded-2xl bg-forest-900 text-cream-50">
          <Sparkles className="size-5" />
        </span>
        <h2 className="mt-5 font-display text-2xl">A few good reasons</h2>
        <ul className="mt-5 space-y-4 text-sm text-ink-soft">
          {[
            'Real availability, direct from your provider’s calendar.',
            'Instant confirmation, plus a reminder before your visit.',
            'Change or cancel online when plans shift.',
            'One clear place for every appointment.',
          ].map((benefit) => (
            <li key={benefit} className="flex gap-2.5">
              <Check className="mt-0.5 size-4 shrink-0 text-forest-600" />
              {benefit}
            </li>
          ))}
        </ul>
        <div className="mt-7 flex items-start gap-3 border-t border-line pt-5">
          <ShieldCheck className="mt-0.5 size-4 shrink-0 text-forest-600" />
          <p className="text-xs leading-relaxed text-ink-muted">
            Your password is stored securely, sessions are protected, and booking endpoints are rate limited.
          </p>
        </div>
        <p className="mt-5 text-xs text-ink-muted">
          Own a studio?{' '}
          <Link to="/register/provider" className="font-semibold text-forest-700 hover:text-clay-700">
            Apply for a provider account
          </Link>
        </p>
      </aside>
    </div>
  )
}
