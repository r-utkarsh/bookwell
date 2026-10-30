import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { ApiError } from '@/lib/api'

export function applyApiFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
): boolean {
  if (!(error instanceof ApiError) || error.fieldErrors.length === 0) return false
  for (const fieldError of error.fieldErrors) {
    setError(fieldError.field as Path<T>, {
      type: 'server',
      message: fieldError.message,
    })
  }
  return true
}
