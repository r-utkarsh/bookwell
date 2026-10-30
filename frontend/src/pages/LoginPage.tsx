import { zodResolver } from '@hookform/resolvers/zod'
import { CalendarCheck2, Eye, EyeOff, LockKeyhole, Quote, ShieldCheck, Sparkles } from 'lucide-react'
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

const schema = z.object({
  email: z.string().trim().min(1, 'Enter your email address.').email('Enter a valid email address.'),
  password: z.string().min(1, 'Enter your password.'),
})

type FormValues = z.infer<typeof schema>

export function LoginPage() {
  const { user, initializing, login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [showPassword, setShowPassword] = useState(false)
  const requested = searchParams.get('redirect')
  const redirect =
    requested && requested.startsWith('/') && !requested.startsWith('//') && !requested.startsWith('/login')
      ? requested
      : null

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  })

  if (initializing) return <FullPageLoader label="Checking your session" />
  if (user) return <Navigate to={redirect ?? roleHome(user)} replace />

  async function onSubmit(values: FormValues) {
    try {
      const signedIn = await login(values.email, values.password)
      toast.success(`Welcome back, ${signedIn.fullName.split(' ')[0]}.`)
      navigate(redirect ?? roleHome(signedIn), { replace: true })
    } catch (error) {
      if (!applyApiFieldErrors(error, setError)) {
        setError('root', { message: getErrorMessage(error) })
      }
    }
  }

  return (
    <div className="mx-auto grid min-h-[calc(100vh-15rem)] max-w-6xl items-stretch gap-0 px-4 py-10 sm:px-6 lg:grid-cols-[0.95fr_1.05fr] lg:px-8">
      <div className="flex items-center justify-center py-4 lg:pr-16">
        <div className="w-full max-w-md">
          <p className="text-[0.65rem] font-bold uppercase tracking-[0.2em] text-clay-600">Welcome back</p>
          <h1 className="mt-3 text-4xl tracking-tight sm:text-5xl">Good to see you.</h1>
          <p className="mt-3 text-sm leading-relaxed text-ink-soft">
            Sign in to manage appointments, your calendar, or the platform.
          </p>

          <form onSubmit={handleSubmit(onSubmit)} className="mt-8 space-y-5" noValidate>
            {errors.root?.message && (
              <div className="rounded-xl border border-red-200 bg-red-50 p-3.5 text-sm text-red-800" role="alert">
                {errors.root.message}
              </div>
            )}
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
            <Field label="Password" htmlFor="password" error={errors.password?.message} required>
              <div className="relative">
                <Input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="Your password"
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
            <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>
              Sign in <LockKeyhole className="size-4" />
            </Button>
          </form>

          <div className="mt-6 space-y-3 text-center text-sm">
            <p className="text-ink-soft">
              New to Bookwell?{' '}
              <Link to="/register" className="font-bold text-forest-800 underline decoration-forest-300 underline-offset-4 hover:text-clay-700">
                Create a customer account
              </Link>
            </p>
            <p className="text-xs text-ink-muted">
              Have a business?{' '}
              <Link to="/register/provider" className="font-semibold text-forest-700 hover:text-clay-700">
                Apply as a provider
              </Link>
            </p>
          </div>
        </div>
      </div>

      <div className="relative hidden overflow-hidden rounded-[2rem] bg-forest-900 p-9 text-cream-50 lg:flex lg:flex-col lg:justify-between">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_80%_15%,rgba(196,154,90,0.28),transparent_38%),radial-gradient(circle_at_10%_90%,rgba(201,111,74,0.25),transparent_40%)]" aria-hidden="true" />
        <div className="relative">
          <span className="grid size-12 place-items-center rounded-2xl border border-white/15 bg-white/[0.06]">
            <CalendarCheck2 className="size-5 text-gold-300" />
          </span>
          <h2 className="mt-8 max-w-md text-4xl leading-tight tracking-tight text-cream-50">
            One quiet place for all the appointments that matter.
          </h2>
        </div>
        <div className="relative space-y-6">
          <div className="rounded-2xl border border-white/10 bg-white/[0.05] p-6">
            <Quote className="size-5 text-gold-300" />
            <p className="mt-3 font-display text-xl leading-snug text-cream-100">
              “The best booking experiences are the ones you barely notice. They just work.”
            </p>
            <p className="mt-4 text-[0.62rem] font-bold uppercase tracking-[0.18em] text-cream-300/60">
              The Bookwell principle
            </p>
          </div>
          <div className="flex flex-wrap gap-x-6 gap-y-3 text-xs text-cream-200/70">
            <span className="inline-flex items-center gap-2">
              <ShieldCheck className="size-4 text-gold-300" /> Protected sessions
            </span>
            <span className="inline-flex items-center gap-2">
              <Sparkles className="size-4 text-gold-300" /> One thoughtful place
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}
