// Pure helpers for the proposed annual introductory checkout.
// No payment action should call these until the renewal flow is implemented.
export const ANNUAL_LAUNCH = Object.freeze({
  firstYearCents: 99000,
  recurringYearCents: 149000,
  currency: 'ZAR',
  consentVersion: 'annual-launch-r990-then-r1490-v1',
  terms: 'Pay R990 for your first 12 months. Your subscription will automatically renew at R1,490 every 12 months thereafter unless cancelled before renewal.',
});

export function annualRenewalDate(paidAt: string): string {
  const payment = new Date(paidAt);
  if (!Number.isFinite(payment.getTime())) throw new Error('Invalid payment timestamp');
  const result = new Date(payment.getTime());
  const originalDay = result.getUTCDate();
  result.setUTCDate(1);
  result.setUTCFullYear(result.getUTCFullYear() + 1);
  const lastDay = new Date(Date.UTC(result.getUTCFullYear(), result.getUTCMonth() + 1, 0)).getUTCDate();
  result.setUTCDate(Math.min(originalDay, lastDay));
  return result.toISOString();
}

export function validateAnnualLaunchConsent(data: unknown): boolean {
  if (!data || typeof data !== 'object') return false;
  const consent = data as {accepted?: unknown; version?: unknown};
  return consent.accepted === true && consent.version === ANNUAL_LAUNCH.consentVersion;
}
