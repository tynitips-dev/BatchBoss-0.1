// Keep settlement validation independent from HTTP handlers for isolated tests.
export function matchesPayfastNotification(
  fields: Record<string, string>,
  payment: Record<string, unknown>,
  merchantId: string
): boolean {
  if (!merchantId || fields.merchant_id !== merchantId) return false;
  if (payment.provider !== "payfast" || payment.currency !== "ZAR") return false;
  const expected = payment.amount;
  if (typeof expected !== "number" || !Number.isFinite(expected) || expected <= 0) return false;
  const gross = fields.amount_gross;
  if (typeof gross !== "string" || !/^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/.test(gross)) return false;
  const actual = Number(gross);
  return Number.isFinite(actual) && Math.abs(actual - expected) < 0.005;
}
