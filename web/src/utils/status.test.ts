import { describe, expect, it } from 'vitest'
import { formatTime, statusText, statusType } from './status'

describe('status utils', () => {
  it('maps labels and types', () => {
    expect(statusText.DELIVERED).toBe('已签收')
    expect(statusType('DELIVERY_FAILED')).toBe('danger')
  })

  it('formats empty time', () => {
    expect(formatTime()).toBe('-')
  })
})
