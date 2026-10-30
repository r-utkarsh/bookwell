import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarOff, Info, Plus, Trash2 } from 'lucide-react'
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
  Select,
  Textarea,
} from '@/components/ui'
import { apiRequest, getErrorMessage } from '@/lib/api'
import { applyApiFieldErrors } from '@/lib/forms'
import { dayName, formatDateOnly, timeFromClock, zonedToday } from '@/lib/format'
import type {
  ScheduleException,
  ScheduleExceptionType,
  ScheduleOverview,
  WeeklyHour,
} from '@/types/api'

interface IntervalDraft {
  startTime: string
  endTime: string
}

type WeekDraft = Record<number, IntervalDraft[]>

const days = [1, 2, 3, 4, 5, 6, 7]

function toWeekDraft(hours: WeeklyHour[]): WeekDraft {
  const draft: WeekDraft = { 1: [], 2: [], 3: [], 4: [], 5: [], 6: [], 7: [] }
  for (const hour of hours) {
    draft[hour.dayOfWeek]?.push({
      startTime: timeFromClock(hour.startTime),
      endTime: timeFromClock(hour.endTime),
    })
  }
  for (const day of days) {
    draft[day]?.sort((a, b) => a.startTime.localeCompare(b.startTime))
  }
  return draft
}

export function ProviderAvailabilityPage() {
  const queryClient = useQueryClient()
  const [weekDraft, setWeekDraft] = useState<WeekDraft | null>(null)
  const [exceptionOpen, setExceptionOpen] = useState(false)
  const [deleting, setDeleting] = useState<ScheduleException | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['provider-schedule'],
    queryFn: () => apiRequest<ScheduleOverview>('/provider/schedule'),
  })

  const week = weekDraft ?? (data ? toWeekDraft(data.weeklyHours) : null)

  const saveMutation = useMutation({
    mutationFn: (draft: WeekDraft) => {
      const intervals = days.flatMap((day) =>
        (draft[day] ?? []).map((slot) => ({
          dayOfWeek: day,
          startTime: slot.startTime,
          endTime: slot.endTime,
        })),
      )
      return apiRequest<WeeklyHour[]>('/provider/schedule/weekly', {
        method: 'PUT',
        body: { intervals },
      })
    },
    onSuccess: () => {
      toast.success('Weekly hours saved.')
      setSaveError(null)
      setWeekDraft(null)
      queryClient.invalidateQueries({ queryKey: ['provider-schedule'] })
    },
    onError: (mutationError) => setSaveError(getErrorMessage(mutationError)),
  })

  const deleteMutation = useMutation({
    mutationFn: (exceptionId: string) =>
      apiRequest<void>(`/provider/schedule/exceptions/${exceptionId}`, { method: 'DELETE' }),
    onSuccess: () => {
      toast.success('Exception removed.')
      setDeleting(null)
      queryClient.invalidateQueries({ queryKey: ['provider-schedule'] })
    },
    onError: (mutationError) => toast.error(getErrorMessage(mutationError)),
  })

  function updateInterval(day: number, index: number, field: keyof IntervalDraft, value: string) {
    setWeekDraft((current) => {
      if (!current) return current
      const next = { ...current, [day]: current[day].map((slot, slotIndex) => (slotIndex === index ? { ...slot, [field]: value } : slot)) }
      return next
    })
    setSaveError(null)
  }

  function addInterval(day: number) {
    setWeekDraft((current) => {
      if (!current) return current
      const existing = current[day] ?? []
      const lastEnd = existing.at(-1)?.endTime ?? '10:00'
      return {
        ...current,
        [day]: [...existing, { startTime: lastEnd, endTime: lastEnd === '19:00' ? '20:00' : '19:00' }],
      }
    })
    setSaveError(null)
  }

  function removeInterval(day: number, index: number) {
    setWeekDraft((current) => {
      if (!current) return current
      return { ...current, [day]: current[day].filter((_, slotIndex) => slotIndex !== index) }
    })
    setSaveError(null)
  }

  if (isError) {
    return (
      <EmptyState
        title="Couldn’t load your availability"
        description={getErrorMessage(error)}
        action={<Button variant="outline" onClick={() => refetch()}>Try again</Button>}
      />
    )
  }

  if (isLoading || !week) {
    return <LoadingBlock rows={5} label="Loading availability" />
  }

  return (
    <div>
      <PageHeader
        eyebrow="Availability"
        title="Set the hours you actually work."
        description="Weekly hours repeat every week. Add a break as a second interval, or use an exception for one-off closures and extra availability."
        actions={<Button onClick={() => setExceptionOpen(true)}><CalendarOff className="size-4" /> Add exception</Button>}
      />

      <Alert tone="info" className="mt-6">
        <div className="flex gap-2">
          <Info className="mt-0.5 size-4 shrink-0" />
          <p>
            Availability is calculated in <strong>{data?.timeZone.replace('_', ' ')}</strong>. Existing confirmed
            appointments must continue to fit inside your weekly hours. If they would fall outside, Bookwell will
            ask you to reschedule them first.
          </p>
        </div>
      </Alert>

      <Card className="mt-6 overflow-hidden">
        <div className="flex flex-col gap-2 border-b border-line px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="font-display text-xl">Weekly hours</h2>
            <p className="text-xs text-ink-muted">Add multiple intervals for a lunch break or split shift.</p>
          </div>
          <Badge tone="neutral">52 weeks a year</Badge>
        </div>
        <div className="divide-y divide-line">
          {days.map((day) => (
            <div key={day} className="grid gap-3 px-5 py-4 sm:grid-cols-[120px_1fr] sm:items-start">
              <div className="flex items-center justify-between sm:block">
                <p className="text-sm font-bold text-forest-900">{dayName(day)}</p>
                <button
                  type="button"
                  onClick={() => addInterval(day)}
                  className="text-xs font-bold text-clay-700 hover:text-clay-800 sm:mt-1"
                >
                  + Add hours
                </button>
              </div>
              <div className="space-y-2">
                {(week[day] ?? []).length === 0 ? (
                  <p className="rounded-xl border border-dashed border-line-strong px-3 py-3 text-xs text-ink-muted">
                    Closed all day
                  </p>
                ) : (
                  week[day].map((slot, index) => (
                    <div key={index} className="flex flex-wrap items-center gap-2 rounded-xl border border-line bg-cream-50 p-2">
                      <label className="sr-only" htmlFor={`day-${day}-start-${index}`}>Start time</label>
                      <Input
                        id={`day-${day}-start-${index}`}
                        type="time"
                        value={slot.startTime}
                        onChange={(event) => updateInterval(day, index, 'startTime', event.target.value)}
                        className="w-auto flex-1 py-2 text-xs sm:max-w-36"
                      />
                      <span className="text-xs text-ink-muted">to</span>
                      <label className="sr-only" htmlFor={`day-${day}-end-${index}`}>End time</label>
                      <Input
                        id={`day-${day}-end-${index}`}
                        type="time"
                        value={slot.endTime}
                        onChange={(event) => updateInterval(day, index, 'endTime', event.target.value)}
                        className="w-auto flex-1 py-2 text-xs sm:max-w-36"
                      />
                      <button
                        type="button"
                        onClick={() => removeInterval(day, index)}
                        className="ml-auto grid size-8 place-items-center rounded-full text-ink-muted hover:bg-red-50 hover:text-red-700"
                        aria-label={`Remove ${dayName(day)} hours`}
                      >
                        <Trash2 className="size-3.5" />
                      </button>
                    </div>
                  ))
                )}
              </div>
            </div>
          ))}
        </div>
        <div className="flex flex-col gap-3 border-t border-line bg-cream-100/50 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-xs text-ink-muted">Changes apply to all future availability.</p>
          <Button onClick={() => week && saveMutation.mutate(week)} loading={saveMutation.isPending}>
            Save weekly hours
          </Button>
        </div>
      </Card>
      {saveError && <Alert tone="error" className="mt-3">{saveError}</Alert>}

      <section className="mt-9">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 className="font-display text-2xl">Exceptions</h2>
            <p className="mt-1 text-xs text-ink-muted">One-off closures, blocked periods, and extra availability.</p>
          </div>
          <Button variant="outline" size="sm" onClick={() => setExceptionOpen(true)}>
            <Plus className="size-3.5" /> Add exception
          </Button>
        </div>

        <div className="mt-4 space-y-3">
          {data?.upcomingExceptions.length ? (
            data.upcomingExceptions.map((exception) => (
              <Card key={exception.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
                <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-cream-100 text-forest-700">
                  <CalendarOff className="size-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm font-bold text-forest-900">{formatDateOnly(exception.date)}</p>
                    <ExceptionBadge type={exception.type} />
                  </div>
                  <p className="mt-1 text-xs text-ink-muted">
                    {exception.type === 'CLOSED'
                      ? 'Closed all day'
                      : `${timeFromClock(exception.startTime)} – ${timeFromClock(exception.endTime)}`}
                    {exception.reason ? ` · ${exception.reason}` : ''}
                  </p>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-red-700 hover:bg-red-50"
                  onClick={() => setDeleting(exception)}
                >
                  <Trash2 className="size-3.5" /> Remove
                </Button>
              </Card>
            ))
          ) : (
            <EmptyState
              icon={<CalendarOff className="size-5" />}
              title="No upcoming exceptions"
              description="Your weekly hours apply as normal. Add a closure when you need a day off."
            />
          )}
        </div>
      </section>

      {exceptionOpen && (
        <ExceptionModal
          timeZone={data?.timeZone ?? 'Asia/Kolkata'}
          onClose={() => setExceptionOpen(false)}
          onSaved={() => {
            setExceptionOpen(false)
            queryClient.invalidateQueries({ queryKey: ['provider-schedule'] })
          }}
        />
      )}

      <ConfirmDialog
        open={Boolean(deleting)}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Remove this exception?"
        description={`${deleting ? formatDateOnly(deleting.date) : 'This date'} will return to your normal weekly hours.`}
        confirmLabel="Remove exception"
        loading={deleteMutation.isPending}
        onConfirm={() => deleting && deleteMutation.mutate(deleting.id)}
      />
    </div>
  )
}

function ExceptionBadge({ type }: { type: ScheduleExceptionType }) {
  if (type === 'CLOSED') return <Badge tone="red">Closed</Badge>
  if (type === 'BLOCKED') return <Badge tone="amber">Blocked</Badge>
  return <Badge tone="green">Extra hours</Badge>
}

const exceptionSchema = z
  .object({
    date: z.string().min(1, 'Choose a date.'),
    type: z.enum(['CLOSED', 'BLOCKED', 'EXTRA_AVAILABILITY']),
    startTime: z.string().optional(),
    endTime: z.string().optional(),
    reason: z.string().trim().max(240, 'Reason is too long.').optional(),
  })
  .superRefine((values, context) => {
    if (values.type !== 'CLOSED') {
      if (!values.startTime) {
        context.addIssue({ code: 'custom', path: ['startTime'], message: 'Start time is required.' })
      }
      if (!values.endTime) {
        context.addIssue({ code: 'custom', path: ['endTime'], message: 'End time is required.' })
      }
      if (values.startTime && values.endTime && values.startTime >= values.endTime) {
        context.addIssue({ code: 'custom', path: ['endTime'], message: 'End time must be after start time.' })
      }
    }
  })

type ExceptionValues = z.infer<typeof exceptionSchema>

function ExceptionModal({
  timeZone,
  onClose,
  onSaved,
}: {
  timeZone: string
  onClose: () => void
  onSaved: () => void
}) {
  const today = zonedToday(timeZone)
  const [serverError, setServerError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ExceptionValues>({
    resolver: zodResolver(exceptionSchema),
    defaultValues: {
      date: today,
      type: 'CLOSED',
      startTime: '10:00',
      endTime: '14:00',
      reason: '',
    },
  })

  const mutation = useMutation({
    mutationFn: (values: ExceptionValues) =>
      apiRequest<ScheduleException>('/provider/schedule/exceptions', {
        method: 'POST',
        body: {
          date: values.date,
          type: values.type,
          startTime: values.type === 'CLOSED' ? null : values.startTime,
          endTime: values.type === 'CLOSED' ? null : values.endTime,
          reason: values.reason || null,
        },
      }),
    onSuccess: () => {
      toast.success('Exception added to your schedule.')
      onSaved()
    },
    onError: (error) => {
      if (!applyApiFieldErrors(error, setError)) setServerError(getErrorMessage(error))
    },
  })

  const type = watch('type')

  return (
    <Modal
      open
      onOpenChange={(open) => !open && onClose()}
      title="Add a schedule exception"
      description="Use this for one-off changes. Weekly hours stay untouched."
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={isSubmitting || mutation.isPending}>Cancel</Button>
          <Button
            onClick={handleSubmit((values) => {
              setServerError(null)
              mutation.mutate(values)
            })}
            loading={isSubmitting || mutation.isPending}
          >
            Add exception
          </Button>
        </>
      }
    >
      <form
        className="space-y-4"
        noValidate
        onSubmit={handleSubmit((values) => {
          setServerError(null)
          mutation.mutate(values)
        })}
      >
        {serverError && <Alert tone="error">{serverError}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Date" htmlFor="exception-date" error={errors.date?.message} required>
            <Input
              id="exception-date"
              type="date"
              min={today}
              aria-invalid={Boolean(errors.date)}
              {...register('date')}
            />
          </Field>
          <Field label="Type" htmlFor="exception-type" error={errors.type?.message} required>
            <Select id="exception-type" {...register('type')}>
              <option value="CLOSED">Closed all day</option>
              <option value="BLOCKED">Block a period</option>
              <option value="EXTRA_AVAILABILITY">Extra availability</option>
            </Select>
          </Field>
        </div>
        {type !== 'CLOSED' && (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Start time" htmlFor="exception-start" error={errors.startTime?.message} required>
              <Input id="exception-start" type="time" aria-invalid={Boolean(errors.startTime)} {...register('startTime')} />
            </Field>
            <Field label="End time" htmlFor="exception-end" error={errors.endTime?.message} required>
              <Input id="exception-end" type="time" aria-invalid={Boolean(errors.endTime)} {...register('endTime')} />
            </Field>
          </div>
        )}
        <Field label="Reason" htmlFor="exception-reason" error={errors.reason?.message} hint="Shown on your internal calendar only.">
          <Textarea id="exception-reason" rows={2} placeholder="Studio maintenance, personal leave, training…" {...register('reason')} />
        </Field>
        {type === 'BLOCKED' && (
          <Alert tone="warning">
            Existing confirmed appointments cannot overlap a blocked period. Reschedule or cancel them first.
          </Alert>
        )}
        {type === 'CLOSED' && (
          <Alert tone="info">
            Full-day closures cannot be combined with another exception on the same date.
          </Alert>
        )}
      </form>
    </Modal>
  )
}
