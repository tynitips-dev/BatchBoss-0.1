const test=require('node:test');const assert=require('node:assert/strict');const {confirmsDeferredFirstDebit:valid}=require('../functions/lib/paystack-renewal-safety');
test('accept exact renewal date',()=>assert.equal(valid('2027-10-15T09:00:00.000Z','2027-10-15T09:00:00Z'),true));
test('accept provider minute rounding',()=>assert.equal(valid('2027-10-15T09:00:59Z','2027-10-15T09:00:00Z'),true));
test('reject immediate first debit',()=>assert.equal(valid('2026-10-15T09:00:00Z','2027-10-15T09:00:00Z'),false));
test('reject missing provider date',()=>assert.equal(valid(undefined,'2027-10-15T09:00:00Z'),false));
test('reject malformed provider date',()=>assert.equal(valid('not-a-date','2027-10-15T09:00:00Z'),false));
