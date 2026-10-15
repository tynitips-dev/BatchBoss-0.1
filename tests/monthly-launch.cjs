const {test}=require('node:test');
const assert=require('node:assert/strict');
const {MONTHLY_LAUNCH,monthlyChargeCents,validateMonthlyConsent}=require('../functions/lib/monthly-launch.js');
test('monthly launch uses Johannesburg calendar cutoff',()=>{
 assert.equal(monthlyChargeCents('2026-12-31T21:59:59Z'),9900);
 assert.equal(monthlyChargeCents('2026-12-31T22:00:00Z'),14900);
 assert.equal(monthlyChargeCents('2027-01-20T08:00:00Z'),14900);
 assert.equal(MONTHLY_LAUNCH.regularCents,14900);
 assert.throws(()=>monthlyChargeCents('not a date'));
});
test('monthly renewal pricing terms require versioned consent',()=>{
 assert.equal(validateMonthlyConsent({accepted:true,version:MONTHLY_LAUNCH.consentVersion}),true);
 assert.equal(validateMonthlyConsent({accepted:true,version:'outdated'}),false);
 assert.equal(validateMonthlyConsent({accepted:false,version:MONTHLY_LAUNCH.consentVersion}),false);
 assert.equal(validateMonthlyConsent(null),false);
});
