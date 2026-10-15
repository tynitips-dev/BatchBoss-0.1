// Helpers for one-time annual launch access; standard annual subscriptions are separate.
export const ANNUAL_LAUNCH = Object.freeze({
  firstYearCents: 99000,
  recurringYearCents: 119900,
  currency: 'ZAR',
  consentVersion: 'annual-launch-r990-manual-renewal-v2',
  terms: 'Pay R990 for 12 months of Pro. This offer does not renew automatically. After access expires, you may choose a new plan at the then-current price.',
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
