// Introductory monthly offer calculation. The provider billing transition is NOT enabled.
export const MONTHLY_LAUNCH = Object.freeze({
  introductoryCents: 9900,
  regularCents: 14900,
  offerEndsLocal: '2026-12-31',
  timezone: 'Africa/Johannesburg',
  currency: 'ZAR',
  consentVersion: 'monthly-r99-through-2026-then-r149-v1',
  terms: 'R99 per month for eligible charges through 31 December 2026. From the first renewal on or after 1 January 2027 the monthly price is R149 until cancelled.',
});

// The price depends on the payment date, never a browser-submitted price.
export function monthlyChargeCents(at: string | Date): number {
  const date = new Date(at);
  if (!Number.isFinite(date.getTime())) throw new Error('Invalid billing timestamp');
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: MONTHLY_LAUNCH.timezone,
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(date);
  const part = (type: string) => parts.find(x => x.type === type)?.value ?? '';
  const localDate = part('year')+'-'+part('month')+'-'+part('day');
  return localDate <= MONTHLY_LAUNCH.offerEndsLocal ?
    MONTHLY_LAUNCH.introductoryCents : MONTHLY_LAUNCH.regularCents;
}

export function validateMonthlyConsent(data: unknown): boolean {
  if (!data || typeof data !== 'object') return false;
  const consent = data as {accepted?: unknown;version?: unknown};
  return consent.accepted===true && consent.version===MONTHLY_LAUNCH.consentVersion;
}

/**
 * First regular-price monthly debit after an independently verified one-off
 * introductory charge. Always derive this from the confirmed paid-at timestamp,
 * never from the checkout initiation date.
 */
export function monthlyRenewalDate(paidAt:string):string {
 const payment=new Date(paidAt);
 if(!Number.isFinite(payment.getTime()))throw new Error('Invalid payment timestamp');
 const day=payment.getUTCDate();
 const next=new Date(payment.getTime());
 next.setUTCDate(1);
 next.setUTCMonth(next.getUTCMonth()+1);
 const lastDay=new Date(Date.UTC(next.getUTCFullYear(),next.getUTCMonth()+1,0)).getUTCDate();
 next.setUTCDate(Math.min(day,lastDay));
 return next.toISOString();
}
