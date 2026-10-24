import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import test from 'node:test';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const require = createRequire(import.meta.url);
const { matchesPayfastNotification } = require(join(dirname(fileURLToPath(import.meta.url)), '../lib/payfast-core.js'));
const merchant = '10000000';
const payment = { provider: 'payfast', currency: 'ZAR', amount: 149 };
const fields = { merchant_id: merchant, amount_gross: '149.00' };
const source = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '../src/index.ts'), 'utf8');
test('valid PayFast merchant and exact amount match', () => {
  assert.equal(matchesPayfastNotification(fields, payment, merchant), true);
});
test('rejects merchant mismatch', () => {
  assert.equal(matchesPayfastNotification({ ...fields, merchant_id: '10000001' }, payment, merchant), false);
});
test('rejects wrong payment provider or currency', () => {
  assert.equal(matchesPayfastNotification(fields, { ...payment, provider: 'paystack' }, merchant), false);
  assert.equal(matchesPayfastNotification(fields, { ...payment, currency: 'USD' }, merchant), false);
});
test('rejects mismatched, missing and malformed amounts', () => {
  for (const amount_gross of ['149.01', '148.99', '', '-149.00', 'NaN', '149.001']) {
    assert.equal(matchesPayfastNotification({ ...fields, amount_gross }, payment, merchant), false);
  }
});
test('rejects invalid expected settlement amounts', () => {
  assert.equal(matchesPayfastNotification(fields, { ...payment, amount: NaN }, merchant), false);
  assert.equal(matchesPayfastNotification(fields, { ...payment, amount: 0 }, merchant), false);
});


test('legacy PayFast checkout is disabled by default while the notification handler remains available', () => {
  assert.match(source, /defineString\\(["']PAYFAST_CHECKOUT_ENABLED["'],\\s*\\{\\s*default:\\s*["']false["']\\s*\\}\\)/);
  const start = source.indexOf('export const createPayfastCheckout = onCall(');
  const end = source.indexOf('export const payfastNotify = onRequest(', start);
  assert.ok(start >= 0 && end > start, 'legacy checkout and callback exports should remain separate');
  const checkout = source.slice(start, end);
  const gate = checkout.indexOf('payfastCheckoutEnabled.value() !== "true"');
  const write = checkout.indexOf('subscriptionPayments').valueOf();
  assert.ok(gate >= 0 && gate < write, 'checkout must fail closed before writing a payment intent');
  assert.match(source.slice(end), /export const payfastNotify = onRequest\\(/);
});
