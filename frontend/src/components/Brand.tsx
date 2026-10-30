import { Link } from 'react-router-dom'
import { cn } from '@/lib/cn'

export function Brand({
  className,
  compact = false,
  light = false,
}: {
  className?: string
  compact?: boolean
  light?: boolean
}) {
  return (
    <Link
      to="/"
      className={cn('group inline-flex items-center gap-3', className)}
      aria-label="Bookwell home"
    >
      <span
        className={cn(
          'grid size-10 shrink-0 place-items-center rounded-[0.9rem] font-display text-2xl font-semibold shadow-sm transition-transform duration-300 group-hover:-rotate-3',
          light ? 'bg-cream-100 text-forest-900' : 'bg-forest-900 text-cream-100',
        )}
        aria-hidden="true"
      >
        b
      </span>
      {!compact && (
        <span className="flex flex-col leading-none">
          <span
            className={cn(
              'font-display text-[1.35rem] font-semibold tracking-tight',
              light ? 'text-cream-50' : 'text-forest-900',
            )}
          >
            Bookwell
          </span>
          <span
            className={cn(
              'mt-1 text-[0.58rem] font-bold uppercase tracking-[0.24em]',
              light ? 'text-cream-200/80' : 'text-ink-muted',
            )}
          >
            Time, well kept
          </span>
        </span>
      )}
    </Link>
  )
}
