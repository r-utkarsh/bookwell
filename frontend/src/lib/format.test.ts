import { describe, expect, it } from 'vitest'
import {
  addDaysToDate,
  dayName,
  durationLabel,
  formatCurrency,
  formatDateOnly,
  formatLongDateOnly,
  initials,
  shortDayName,
  timeFromClock,
  titleCase,
  weeklyScheduleDates,
} from '@/lib/format'

describe('formatCurrency', () => {
  it('formats whole rupee amounts without decimals', () => {
    const formatted = formatCurrency(1500, 'INR')
    expect(formatted).toContain('1,500')
    expect(formatted).toContain('₹')
    expect(formatted).not.toContain('.00')
  })

  it('keeps paise when the amount is not whole', () => {
    expect(formatCurrency('250.5', 'INR')).toContain('250.50')
  })

  it('returns a dash for missing values and invalid input', () => {
    expect(formatCurrency(null)).toBe('—')
    expect(formatCurrency(undefined)).toBe('—')
    expect(formatCurrency('')).toBe('—')
    expect(formatCurrency('not-a-number')).toBe('—')
  })
})

describe('durationLabel', () => {
  it('describes short, whole-hour, and mixed durations', () => {
    expect(durationLabel(45)).toBe('45 min')
    expect(durationLabel(60)).toBe('1 hr')
    expect(durationLabel(90)).toBe('1 hr 30 min')
    expect(durationLabel(120)).toBe('2 hr')
  })
})

describe('date helpers', () => {
  it('formats date-only values without timezone drift', () => {
    expect(formatDateOnly('2027-03-01')).toBe('Mon, 1 Mar')
    expect(formatLongDateOnly('2027-03-01')).toBe('Monday, 1 March 2027')
    expect(formatDateOnly('not-a-date')).toBe('not-a-date')
  })

  it('adds calendar days across month boundaries', () => {
    expect(addDaysToDate('2027-02-28', 1)).toBe('2027-03-01')
    expect(addDaysToDate('2027-03-01', -1)).toBe('2027-02-28')
  })

  it('builds a 21-day schedule window for a time zone', () => {
    const dates = weeklyScheduleDates('Asia/Kolkata', 21)
    expect(dates).toHaveLength(21)
    expect(dates[0]).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(dates[20]).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(dates[0]).not.toBe(dates[20])
  })
})

describe('display helpers', () => {
  it('derives initials from the first two name parts', () => {
    expect(initials('Aarohi Sen')).toBe('AS')
    expect(initials('  Riya   Kapoor  ')).toBe('RK')
    expect(initials('Madonna')).toBe('M')
    expect(initials('')).toBe('')
  })

  it('converts enum-style labels to title case', () => {
    expect(titleCase('EXTRA_AVAILABILITY')).toBe('Extra Availability')
    expect(titleCase('NO_SHOW')).toBe('No Show')
  })

  it('maps day numbers and clock values', () => {
    expect(dayName(1)).toBe('Monday')
    expect(dayName(7)).toBe('Sunday')
    expect(dayName(0)).toBe('')
    expect(shortDayName(6)).toBe('Sat')
    expect(timeFromClock('09:30:00')).toBe('09:30')
    expect(timeFromClock(null)).toBe('')
  })
})
