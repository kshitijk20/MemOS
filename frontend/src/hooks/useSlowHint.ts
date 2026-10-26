import { useEffect, useState } from 'react'

/**
 * True once `active` has stayed true for `delayMs`. Used to explain long waits, e.g. a free-tier
 * backend waking up from sleep, instead of leaving a spinner that looks frozen.
 */
export function useSlowHint(active: boolean, delayMs = 5000): boolean {
  const [slow, setSlow] = useState(false)

  useEffect(() => {
    if (!active) return
    const timer = setTimeout(() => setSlow(true), delayMs)
    return () => {
      clearTimeout(timer)
      setSlow(false)
    }
  }, [active, delayMs])

  return active && slow
}
