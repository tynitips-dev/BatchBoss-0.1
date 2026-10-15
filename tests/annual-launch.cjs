const {test}=require('node:test');
const assert=require('node:assert/strict');
const {ANNUAL_LAUNCH,annualRenewalDate,validateAnnualLaunchConsent}=require('../functions/lib/annual-launch.js');

test('launch amounts and consent version are fixed on the server',()=>{
 assert.equal(ANNUAL_LAUNCH.firstYearCents,99000);
 assert.equal(ANNUAL_LAUNCH.recurringYearCents,149000);
 assert.equal(ANNUAL_LAUNCH.currency,'ZAR');
 assert.equal(validateAnnualLaunchConsent({accepted:true,version:ANNUAL_LAUNCH.consentVersion}),true);
 for(const consent of [null,{},false,{accepted:false,version:ANNUAL_LAUNCH.consentVersion},{accepted:true,version:'old-terms'}])
  assert.equal(validateAnnualLaunchConsent(consent),false);
});
test('renewal anniversary preserves clock and handles February leap dates',()=>{
 assert.equal(annualRenewalDate('2024-02-29T11:30:00Z'),'2025-02-28T11:30:00.000Z');
 assert.equal(annualRenewalDate('2026-10-14T09:00:00Z'),'2027-10-14T09:00:00.000Z');
 assert.throws(()=>annualRenewalDate('bad date'),/Invalid payment timestamp/);
});
