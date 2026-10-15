const test=require('node:test');const assert=require('node:assert/strict');
const {monthlyRenewalDate}=require('../functions/lib/monthly-launch');
test('ordinary monthly anniversary',()=>assert.equal(monthlyRenewalDate('2026-10-15T10:00:00Z'),'2026-11-15T10:00:00.000Z'));
test('month end clamps to November 30',()=>assert.equal(monthlyRenewalDate('2026-10-31T10:00:00Z'),'2026-11-30T10:00:00.000Z'));
test('January 31 clamps for non leap February',()=>assert.equal(monthlyRenewalDate('2027-01-31T10:00:00Z'),'2027-02-28T10:00:00.000Z'));
test('January 31 clamps for leap February',()=>assert.equal(monthlyRenewalDate('2028-01-31T10:00:00Z'),'2028-02-29T10:00:00.000Z'));
test('December wraps into January',()=>assert.equal(monthlyRenewalDate('2026-12-31T10:00:00Z'),'2027-01-31T10:00:00.000Z'));
test('invalid paid-at rejected',()=>assert.throws(()=>monthlyRenewalDate('invalid'),/Invalid payment timestamp/));
