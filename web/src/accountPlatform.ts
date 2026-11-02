/** Registration route only: never infer an installation from device/user-agent details. */
export function signupPlatform(search: string): 'android' | 'web' {
  return new URLSearchParams(search).get('from') === 'android' ? 'android' : 'web'
}
