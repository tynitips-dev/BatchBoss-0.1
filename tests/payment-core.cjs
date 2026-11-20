const {test}=require('node:test');const assert=require('node:assert/strict');const {createHmac}=require('node:crypto');const {validSignature,periodEnd,matchesPayment}=require('../functions/lib/paystack-core.js');
test('webhook authenticity rejects altered payload, missing and malformed signatures',()=>{const body=Buffer.from('{"event":"charge.success"}');const sig=createHmac('sha512','test-secret').update(body).digest('hex');assert.equal(validSignature(body,sig,'test-secret'),true);assert.equal(validSignature(Buffer.from('{}'),sig,'test-secret'),false);for(const s of ['', 'z'.repeat(128),sig.slice(1)])assert.equal(validSignature(body,s,'test-secret'),false)});
const expected={amount:14900,currency:'ZAR',customer:'CUS_1',planCode:'PLN_1',domain:'live'};const tx={status:'success',amount:14900,currency:'ZAR',customer:{customer_code:'CUS_1'},plan:{plan_code:'PLN_1'},domain:'live',reference:'BB-test',paid_at:'2026-10-12T10:00:00Z'};
test('only correct paid customer, plan, currency, amount and environment can activate access',()=>{assert.equal(matchesPayment(tx,expected),true);for(const change of [{amount:149},{currency:'NGN'},{customer:{customer_code:'CUS_other'}},{plan:{plan_code:'PLN_other'}},{domain:'test'},{status:'failed'},{paid_at:'bad'},{reference:'../../x'}])assert.equal(matchesPayment({...tx,...change},expected),false)});
test('billing dates clamp month end and leap year rather than overflow',()=>{assert.equal(periodEnd('2026-01-31T10:00:00Z','monthly').toISOString(),'2026-02-28T10:00:00.000Z');assert.equal(periodEnd('2024-02-29T10:00:00Z','annual').toISOString(),'2025-02-28T10:00:00.000Z');assert.equal(periodEnd('2026-10-12T10:00:00Z','monthly').toISOString(),'2026-11-12T10:00:00.000Z')});

test('international billing never changes the ZAR charge to the accounting currency',()=>{
 const {billingRoute}=require('../functions/lib/paystack-core.js');
 assert.deepEqual(billingRoute({},false),{enabled:true,billingCurrency:'ZAR',accountingCurrency:'ZAR',international:false});
 assert.deepEqual(billingRoute({countryCode:'GB',currency:'GBP'},false),{enabled:false,billingCurrency:'ZAR',accountingCurrency:'GBP',international:true});
 assert.equal(billingRoute({countryCode:'US',currency:'USD'},true).enabled,true);
 assert.equal(billingRoute({countryCode:'US',currency:'ZAR'},true),null);
 assert.equal(billingRoute(undefined,true),null);
});
