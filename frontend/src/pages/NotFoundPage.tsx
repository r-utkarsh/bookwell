import { ArrowLeft, CalendarX2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { buttonClasses, EmptyState } from '@/components/ui'

export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-2xl px-4 py-24 sm:px-6">
      <EmptyState
        icon={<CalendarX2 className="size-5" />}
        title="This page slipped off the calendar"
        description="The link may be old, or the page may have moved. Let’s get you back to something useful."
        action={
          <Link to="/" className={buttonClasses('primary', 'md')}>
            <ArrowLeft className="size-4" /> Back home
          </Link>
        }
      />
    </div>
  )
}

export function ForbiddenPage() {
  return (
    <div className="mx-auto max-w-2xl px-4 py-24 sm:px-6">
      <EmptyState
        title="That door is for another account type"
        description="You are signed in, but this area belongs to a different role. Try your own dashboard instead."
        action={
          <Link to="/" className={buttonClasses('primary', 'md')}>
            Go to Bookwell
          </Link>
        }
      />
    </div>
  )
}
