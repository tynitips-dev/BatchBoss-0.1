import assert from 'node:assert/strict';
import test from 'node:test';
import { createHmac } from 'node:crypto';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const { validSignature, matchesPayment, periodEnd } =
  require(join(dirname(fileURLToPath(import.meta.url)), '../lib/paystack-core.js'));

const key = 'synthetic-webhook-secret';
const payload = Buffer.from('{"event":"charge.success","data":{"reference":"BB-test"}}');

test('Paystack webhook signature accepts exact signed bytes', () => {
  const signature = createHmac('sha512', key).update(payload).digest('hex');
  assert.equal(validSignature(payload, signature, key), true);
  assert.equal(validSignature(Buffer.from(payload.toString() + ' '), signature, key), false);
  assert.equal(validSignature(payload, 'not-a-valid-signature', key), false);
});

const expected = {
  amount: 14900, currency: 'ZAR', customer: 'CUS_TEST',
  planCode: 'PLN_TEST', domain: 'live',
};
function transaction(overrides = {}) {
  return {
    status: 'success', reference: 'BB-test',
    amount: 14900, currency: 'ZAR', domain: 'live',
    customer: { customer_code: 'CUS_TEST' }, plan: 'PLN_TEST',
    paid_at: '2026-10-22T09:00:00.000Z', ...overrides,
  };
}
test('verified payment must match every server-owned billing field', () => {
  assert.equal(matchesPayment(transaction(), expected), true);
  for (const changed of [
    { amount: 9900 }, { currency: 'USD' }, { domain: 'test' },
    { status: 'failed' }, { reference: 'reference with spaces' },
    { customer: { customer_code: 'CUS_OTHER' } }, { plan: 'PLN_OTHER' },
    { paid_at: 'invalid timestamp' },
  ]) {
    assert.equal(matchesPayment(transaction(changed), expected), false,
      'mismatch wrongly accepted: ' + JSON.stringify(changed));
  }
});
test('monthly renewal advances an exact paid period', () => {
  assert.equal(periodEnd('2026-10-22T09:00:00Z', 'monthly').toISOString(),
    '2026-11-22T09:00:00.000Z');
});
test('month end clamps to the payment policy limit', () => {
  assert.equal(periodEnd('2026-01-31T09:00:00Z', 'monthly').toISOString(),
    '2026-02-28T09:00:00.000Z');
});
test('annual expiry preserves calendar date except leap day', () => {
  assert.equal(periodEnd('2024-02-29T09:00:00Z', 'annual').toISOString(),
    '2025-02-28T09:00:00.000Z');
});
test('impossible calendar dates and malformed Paystack timestamps fail closed', () => {
  for (const paidAt of [
    '2026-02-31T09:00:00.000Z',
    '2026-04-31T09:00:00.000Z',
    '2026-10-22T25:00:00Z',
    '2026-10-22T09:61:00Z',
    '2026-10-22T09:00:00+15:00',
    '2026-10-22 09:00:00Z',
    '2026-10-22T09:00:00'
  ]) {
    assert.throws(() => periodEnd(paidAt, 'monthly'), /Invalid paid date/);
    assert.equal(matchesPayment(transaction({ paid_at: paidAt }), expected), false);
  }
  assert.equal(matchesPayment(transaction({
    paid_at: '2026-10-22T11:00:00+02:00'
  }), expected), true);
});

test('invalid billing timestamp cannot create an entitlement', () => {
  assert.throws(() => periodEnd('nonsense', 'annual'), /Invalid paid date/);
});
