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
