import * as DialogPrimitive from '@radix-ui/react-dialog'
import {
  AlertCircle,
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Info,
  LoaderCircle,
  X,
} from 'lucide-react'
import {
  forwardRef,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react'
import { cn } from '@/lib/cn'
import type { AppointmentStatus, NotificationStatus, ProviderStatus } from '@/types/api'
import { titleCase } from '@/lib/format'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'outline' | 'gold'
type ButtonSize = 'sm' | 'md' | 'lg' | 'icon'

export function buttonClasses(variant: ButtonVariant = 'primary', size: ButtonSize = 'md'): string {
  return cn(
    'inline-flex items-center justify-center gap-2 rounded-full font-bold transition-all duration-200 disabled:pointer-events-none disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2',
    {
      'bg-forest-900 text-cream-50 shadow-sm hover:bg-forest-700 hover:shadow-md active:scale-[0.98]':
        variant === 'primary',
      'bg-clay-500 text-white shadow-sm hover:bg-clay-600 hover:shadow-md active:scale-[0.98]':
        variant === 'secondary',
      'bg-cream-100 text-forest-900 hover:bg-cream-200 active:scale-[0.98]': variant === 'ghost',
      'border border-line-strong bg-cream-50/70 text-forest-900 hover:border-forest-300 hover:bg-cream-100':
        variant === 'outline',
      'bg-red-700 text-white hover:bg-red-800': variant === 'danger',
      'bg-gold-500 text-forest-900 hover:bg-gold-300': variant === 'gold',
      'px-3.5 py-2 text-xs': size === 'sm',
      'px-5 py-2.5 text-sm': size === 'md',
      'px-7 py-3.5 text-sm': size === 'lg',
      'size-10 p-0': size === 'icon',
    },
  )
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, variant = 'primary', size = 'md', loading, children, disabled, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      className={cn(buttonClasses(variant, size), className)}
      disabled={disabled || loading}
      {...props}
    >
      {loading && <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />}
      {children}
    </button>
  )
})

interface FieldProps {
  label: string
  htmlFor: string
  error?: string
  hint?: string
  required?: boolean
  children: ReactNode
  className?: string
}

export function Field({ label, htmlFor, error, hint, required, children, className }: FieldProps) {
  return (
    <div className={cn('space-y-1.5', className)}>
      <label htmlFor={htmlFor} className="block text-xs font-bold uppercase tracking-[0.12em] text-forest-800">
        {label}
        {required && <span className="ml-1 text-clay-600">*</span>}
      </label>
      {children}
      {error ? (
        <p id={`${htmlFor}-error`} className="text-xs font-medium text-red-700" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p id={`${htmlFor}-hint`} className="text-xs text-ink-muted">
          {hint}
        </p>
      ) : null}
    </div>
  )
}

const controlClasses =
  'w-full rounded-xl border border-line-strong bg-white/70 px-4 py-3 text-sm text-ink shadow-sm outline-none transition placeholder:text-ink-muted/70 focus:border-forest-400 focus:bg-white focus:ring-4 focus:ring-forest-100 disabled:cursor-not-allowed disabled:opacity-60 aria-[invalid=true]:border-red-500 aria-[invalid=true]:ring-red-100'

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function Input({ className, ...props }, ref) {
    return <input ref={ref} className={cn(controlClasses, className)} {...props} />
  },
)

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  function Textarea({ className, ...props }, ref) {
    return <textarea ref={ref} className={cn(controlClasses, 'min-h-28 resize-y', className)} {...props} />
  },
)

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  function Select({ className, children, ...props }, ref) {
    return (
      <select ref={ref} className={cn(controlClasses, 'appearance-none pr-10', className)} {...props}>
        {children}
      </select>
    )
  },
)

export function Card({
  children,
  className,
  as: Tag = 'div',
}: {
  children: ReactNode
  className?: string
  as?: 'div' | 'section' | 'article' | 'li'
}) {
  return (
    <Tag className={cn('rounded-[1.5rem] border border-line/90 bg-white/60 shadow-soft', className)}>
      {children}
    </Tag>
  )
}

type BadgeTone = 'neutral' | 'green' | 'amber' | 'red' | 'clay' | 'blue'

const badgeTones: Record<BadgeTone, string> = {
  neutral: 'bg-cream-100 text-ink-soft ring-cream-300/60',
  green: 'bg-forest-50 text-forest-700 ring-forest-200',
  amber: 'bg-gold-100 text-gold-700 ring-gold-300/60',
  red: 'bg-red-50 text-red-700 ring-red-200',
  clay: 'bg-clay-50 text-clay-700 ring-clay-200',
  blue: 'bg-blue-50 text-blue-700 ring-blue-200',
}

export function Badge({
  children,
  tone = 'neutral',
  className,
}: {
  children: ReactNode
  tone?: BadgeTone
  className?: string
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[0.63rem] font-bold uppercase tracking-[0.1em] ring-1 ring-inset',
        badgeTones[tone],
        className,
      )}
    >
      {children}
    </span>
  )
}

const statusTones: Record<AppointmentStatus | NotificationStatus | ProviderStatus, BadgeTone> = {
  CONFIRMED: 'green',
  CANCELLED: 'red',
  COMPLETED: 'blue',
  NO_SHOW: 'amber',
  PENDING: 'amber',
  SENDING: 'blue',
  SENT: 'green',
  FAILED: 'red',
  SKIPPED: 'neutral',
  APPROVED: 'green',
  SUSPENDED: 'red',
}

export function StatusBadge({ status }: { status: AppointmentStatus | NotificationStatus | ProviderStatus }) {
  return <Badge tone={statusTones[status] ?? 'neutral'}>{titleCase(status)}</Badge>
}

export function Alert({
  children,
  tone = 'info',
  className,
}: {
  children: ReactNode
  tone?: 'info' | 'error' | 'success' | 'warning'
  className?: string
}) {
  const styles = {
    info: 'border-forest-200 bg-forest-50 text-forest-800',
    error: 'border-red-200 bg-red-50 text-red-800',
    success: 'border-green-200 bg-green-50 text-green-800',
    warning: 'border-gold-300/60 bg-gold-100/70 text-gold-700',
  }
  const Icon = tone === 'error' ? AlertCircle : tone === 'success' ? CheckCircle2 : tone === 'warning' ? AlertCircle : Info
  return (
    <div className={cn('flex gap-3 rounded-xl border p-4 text-sm', styles[tone], className)} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <div>{children}</div>
    </div>
  )
}

export function Spinner({ className }: { className?: string }) {
  return <LoaderCircle className={cn('size-5 animate-spin text-forest-600', className)} aria-hidden="true" />
}

export function FullPageLoader({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 text-center">
      <Spinner className="size-7" />
      <p className="text-sm font-medium text-ink-muted">{label}…</p>
    </div>
  )
}

export function LoadingBlock({ label = 'Loading', rows = 3 }: { label?: string; rows?: number }) {
  return (
    <div className="space-y-3" aria-busy="true" aria-label={label}>
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="h-20 animate-pulse rounded-2xl bg-cream-200/70" />
      ))}
    </div>
  )
}

export function EmptyState({
  title,
  description,
  icon,
  action,
  className,
}: {
  title: string
  description?: string
  icon?: ReactNode
  action?: ReactNode
  className?: string
}) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center rounded-[1.5rem] border border-dashed border-line-strong bg-cream-50/50 px-6 py-12 text-center',
        className,
      )}
    >
      <div className="mb-4 grid size-12 place-items-center rounded-full bg-cream-100 text-forest-500">
        {icon ?? <CalendarDays className="size-5" aria-hidden="true" />}
      </div>
      <h3 className="font-display text-xl text-forest-900">{title}</h3>
      {description && <p className="mt-1 max-w-sm text-sm text-ink-muted">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
  className,
}: {
  eyebrow?: string
  title: string
  description?: string
  actions?: ReactNode
  className?: string
}) {
  return (
    <div className={cn('flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between', className)}>
      <div>
        {eyebrow && <p className="mb-2 text-[0.65rem] font-bold uppercase tracking-[0.2em] text-clay-600">{eyebrow}</p>}
        <h1 className="text-3xl tracking-tight sm:text-4xl">{title}</h1>
        {description && <p className="mt-2 max-w-2xl text-sm leading-relaxed text-ink-soft">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

export function StatCard({
  label,
  value,
  hint,
  icon,
  accent = false,
}: {
  label: string
  value: string | number
  hint?: string
  icon?: ReactNode
  accent?: boolean
}) {
  return (
    <Card className={cn('p-5', accent && 'bg-forest-900 text-cream-50')}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <p
            className={cn(
              'text-[0.63rem] font-bold uppercase tracking-[0.16em]',
              accent ? 'text-cream-200/75' : 'text-ink-muted',
            )}
          >
            {label}
          </p>
          <p className={cn('mt-2 font-display text-3xl', accent ? 'text-cream-50' : 'text-forest-900')}>{value}</p>
          {hint && <p className={cn('mt-1 text-xs', accent ? 'text-cream-200/70' : 'text-ink-muted')}>{hint}</p>}
        </div>
        {icon && (
          <span
            className={cn(
              'grid size-10 place-items-center rounded-xl',
              accent ? 'bg-white/10 text-cream-100' : 'bg-cream-100 text-forest-600',
            )}
          >
            {icon}
          </span>
        )}
      </div>
    </Card>
  )
}

const dialogSizes = {
  sm: 'max-w-md',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
}

export function Modal({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
  size = 'md',
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  children: ReactNode
  footer?: ReactNode
  size?: keyof typeof dialogSizes
}) {
  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-forest-900/40 backdrop-blur-[2px] data-[state=closed]:animate-[fadeOut_0.15s_ease] data-[state=open]:animate-[fadeIn_0.2s_ease]" />
        <DialogPrimitive.Content
          className={cn(
            'fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[calc(100%-2rem)] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-[1.6rem] border border-line bg-cream-50 p-6 shadow-lift outline-none sm:p-7',
            dialogSizes[size],
          )}
        >
          <div className="mb-5 flex items-start justify-between gap-4">
            <div>
              <DialogPrimitive.Title className="font-display text-2xl text-forest-900">
                {title}
              </DialogPrimitive.Title>
              {description && (
                <DialogPrimitive.Description className="mt-1 text-sm text-ink-soft">
                  {description}
                </DialogPrimitive.Description>
              )}
            </div>
            <DialogPrimitive.Close
              className="grid size-9 shrink-0 place-items-center rounded-full text-ink-muted transition hover:bg-cream-100 hover:text-forest-900"
              aria-label="Close dialog"
            >
              <X className="size-4" />
            </DialogPrimitive.Close>
          </div>
          {children}
          {footer && <div className="mt-6 flex flex-wrap justify-end gap-2 border-t border-line pt-5">{footer}</div>}
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = 'Confirm',
  onConfirm,
  loading,
  destructive = false,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: string
  confirmLabel?: string
  onConfirm: () => void
  loading?: boolean
  destructive?: boolean
}) {
  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      description={description}
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={loading}>
            Keep it
          </Button>
          <Button variant={destructive ? 'danger' : 'primary'} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <p className="text-sm text-ink-soft">
        This action updates your calendar immediately. You can always contact the provider if you need help.
      </p>
    </Modal>
  )
}

export function Pagination({
  page,
  totalPages,
  onPageChange,
  className,
}: {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
  className?: string
}) {
  if (totalPages <= 1) return null
  return (
    <nav className={cn('flex items-center justify-between gap-3', className)} aria-label="Pagination">
      <Button variant="outline" size="sm" onClick={() => onPageChange(page - 1)} disabled={page <= 0}>
        <ChevronLeft className="size-3.5" /> Previous
      </Button>
      <span className="text-xs font-semibold text-ink-muted">
        Page {page + 1} of {totalPages}
      </span>
      <Button
        variant="outline"
        size="sm"
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
      >
        Next <ChevronRight className="size-3.5" />
      </Button>
    </nav>
  )
}

export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' | 'lg' }) {
  const parts = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
  return (
    <span
      className={cn(
        'grid shrink-0 place-items-center rounded-full bg-forest-100 font-display font-semibold text-forest-700',
        size === 'sm' && 'size-8 text-xs',
        size === 'md' && 'size-10 text-sm',
        size === 'lg' && 'size-14 text-lg',
      )}
      aria-hidden="true"
    >
      {parts.join('')}
    </span>
  )
}
