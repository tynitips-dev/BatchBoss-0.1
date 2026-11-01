/** Historical/invalid values must not be guessed from bakery IDs or login timestamps. */
export function accountPlatform(value: unknown): 'android' | 'web' | 'unknown' {
  return value === 'android' || value === 'web' ? value : 'unknown';
}
