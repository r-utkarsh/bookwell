import { CheckCircle2, XCircle, Loader2, ArrowRight } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui'
import { getErrorMessage, verifyEmailRequest } from '@/lib/api'

export function VerifyEmailPage() {
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [errorMessage, setErrorMessage] = useState<string>('')

  useEffect(() => {
    const hash = window.location.hash
    const match = hash.match(/token=([^&]+)/)
    const token = match ? decodeURIComponent(match[1]) : null

    if (window.history.replaceState) {
      window.history.replaceState(null, '', window.location.pathname)
    }

    if (!token) {
      setStatus('error')
      setErrorMessage('No verification token found in link. Please request a new verification email.')
      return
    }

    verifyEmailRequest(token)
      .then(() => {
        setStatus('success')
      })
      .catch((err) => {
        setStatus('error')
        setErrorMessage(getErrorMessage(err))
      })
  }, [])

  return (
    <div className="flex min-h-[60vh] items-center justify-center px-4 py-12">
      <div className="w-full max-w-md rounded-2xl border border-stone-200 bg-white p-8 text-center shadow-sm">
        {status === 'loading' && (
          <div className="flex flex-col items-center gap-4 py-8">
            <Loader2 className="h-10 w-10 animate-spin text-stone-600" />
            <h1 className="text-xl font-semibold text-stone-900">Verifying your email...</h1>
            <p className="text-sm text-stone-500">Please wait while we confirm your account.</p>
          </div>
        )}

        {status === 'success' && (
          <div className="flex flex-col items-center gap-4 py-4">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
              <CheckCircle2 className="h-7 w-7" />
            </div>
            <h1 className="text-2xl font-bold text-stone-900">Email Verified!</h1>
            <p className="text-sm text-stone-600">
              Your email address has been verified successfully. You can now book appointments and use all features.
            </p>
            <div className="mt-4">
              <Link to="/">
                <Button className="gap-2">
                  Explore Providers <ArrowRight className="h-4 w-4" />
                </Button>
              </Link>
            </div>
          </div>
        )}

        {status === 'error' && (
          <div className="flex flex-col items-center gap-4 py-4">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600">
              <XCircle className="h-7 w-7" />
            </div>
            <h1 className="text-2xl font-bold text-stone-900">Verification Failed</h1>
            <p className="text-sm text-stone-600">{errorMessage}</p>
            <div className="mt-4 flex gap-3">
              <Link to="/login">
                <Button variant="outline">Sign In</Button>
              </Link>
              <Link to="/">
                <Button>Go to Home</Button>
              </Link>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
