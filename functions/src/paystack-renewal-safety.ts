/**
 * Paystack can acknowledge a subscription without proving when the first debit
 * will occur. A successful API response is not sufficient to promise a delayed
 * introductory renewal. Allow up to one minute of provider timestamp rounding.
 */
export function confirmsDeferredFirstDebit(providerNextPayment:unknown, promisedRenewal:string):boolean {
 if(typeof providerNextPayment!=='string'||typeof promisedRenewal!=='string')return false;
 const actual=Date.parse(providerNextPayment), expected=Date.parse(promisedRenewal);
 return Number.isFinite(actual)&&Number.isFinite(expected)&&Math.abs(actual-expected)<=60_000;
}
