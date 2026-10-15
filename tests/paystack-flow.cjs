const {test}=require('node:test');const assert=require('node:assert/strict');const Module=require('node:module');const {createHmac}=require('node:crypto');
const records=new Map();let updates=0;let queue=Promise.resolve();let key='sk_live_fixture_not_a_real_key';let promoEnabled=false;
class Ref{constructor(path){this.path=path;this.id=path.split('/').pop()} async get(){return {exists:records.has(this.path),data:()=>records.get(this.path),id:this.id,ref:this}}async set(data,opt){records.set(this.path,opt?.merge?{...records.get(this.path),...data}:data)}async update(data){updates++;records.set(this.path,{...records.get(this.path),...data})}async delete(){records.delete(this.path)}}
const db={doc:p=>new Ref(p),collection:p=>({where:(field,op,value)=>({get:async()=>({docs:await Promise.all([...records.keys()].filter(k=>k.startsWith(p+'/')&&records.get(k)[field]===value).map(k=>new Ref(k).get()))})})}),runTransaction:fn=>{const result=queue.then(()=>fn({get:r=>r.get(),set:(r,d,o)=>r.set(d,o),update:(r,d)=>r.update(d)}));queue=result.catch(()=>{});return result}};
class HttpsError extends Error{constructor(code,message){super(message);this.code=code}}
const old=Module._load;Module._load=function(id,...args){if(id==='firebase-admin/firestore')return {getFirestore:()=>db,FieldValue:{serverTimestamp:()=>new Date()}};if(id==='firebase-admin/auth')return {getAuth:()=>({getUser:async()=>({email:'fixture@example.com'})})};if(id==='firebase-functions/params')return {defineSecret:()=>({value:()=>key}),defineString:name=>({value:()=>name==='PAYSTACK_ANNUAL_LAUNCH_ENABLED'?(promoEnabled?'true':'false'):name==='PAYSTACK_ENABLED'?'true':name==='PAYSTACK_MONTHLY_PLAN_CODE'?'PLN_month':'PLN_year'})};if(id==='firebase-functions/v2/https')return {HttpsError,onCall:(o,fn)=>fn,onRequest:(o,fn)=>fn};if(id==='firebase-functions/v2/scheduler')return {onSchedule:(o,fn)=>fn};return old.call(this,id,...args)};
const api=require('../functions/lib/paystack.js');Module._load=old;
const expected={uid:'baker1',provider:'paystack',amount:14900,currency:'ZAR',customer:'CUS_baker',planCode:'PLN_month',domain:'live',interval:'monthly'};
let tx={reference:'BB-first',status:'success',amount:14900,currency:'ZAR',customer:{customer_code:'CUS_baker'},plan:{plan_code:'PLN_month'},domain:'live',paid_at:'2026-10-12T10:00:00Z',id:123};
global.fetch=async(url)=>({ok:true,json:async()=>({status:true,data:url.includes('/subscription/')?{domain:'live',customer:{customer_code:'CUS_baker'},plan:{plan_code:'PLN_month',interval:'monthly',currency:'ZAR'},amount:14900,status:'active'}:tx})});
const request=uid=>({auth:{uid},data:{reference:'BB-first'}});
function reset(){records.clear();updates=0;records.set('users/baker1',{subscriptionStatus:'free'});records.set('subscriptionPayments/BB-first',{...expected,status:'pending'});tx={...tx,status:'success',amount:14900,domain:'live',reference:'BB-first',paid_at:'2026-10-12T10:00:00Z'}}
async function webhook(event,valid=true){const body=Buffer.from(JSON.stringify(event));let status;let output;await api.paystackWebhook({method:'POST',body:event,rawBody:body,headers:{'x-paystack-signature':valid?createHmac('sha512',key).update(body).digest('hex'):'bad'}},{status(s){status=s;return this},send(s){output=s}});return {status,output}}
test('callback and concurrent duplicate verification grant exactly once',async()=>{reset();await Promise.all([api.verifyPaystackPayment(request('baker1')),api.verifyPaystackPayment(request('baker1'))]);assert.equal(updates,1);assert.equal(records.get('users/baker1').subscriptionExpiresAt.toISOString(),'2026-11-12T10:00:00.000Z');assert.equal(records.get('subscriptionPayments/BB-first').status,'success')});
test('another account cannot verify this checkout',async()=>{reset();await assert.rejects(api.verifyPaystackPayment(request('other')),e=>e.code==='permission-denied');assert.equal(updates,0)});
test('failed and wrong-amount payments do not grant access',async()=>{reset();tx.status='failed';assert.equal((await api.verifyPaystackPayment(request('baker1'))).status,'failed');assert.equal(updates,0);tx.status='success';tx.amount=1;await assert.rejects(api.verifyPaystackPayment(request('baker1')));assert.equal(updates,0)});
test('test transactions record success without activating live profile',async()=>{reset();tx.domain='test';records.get('subscriptionPayments/BB-first').domain='test';await api.verifyPaystackPayment(request('baker1'));assert.equal(updates,0);assert.equal(records.get('users/baker1').subscriptionStatus,'free')});
test('forged webhook never changes data',async()=>{reset();assert.equal((await webhook({event:'charge.success',data:{reference:'BB-first'}},false)).status,401);assert.equal(updates,0)});
test('verified renewal invoice is mapped without relying on checkout metadata',async()=>{reset();records.set('paystackCustomers/CUS_baker',{uid:'baker1'});records.set('users/baker1',{subscriptionSource:'paystack',subscriptionStatus:'active',paystackSubscriptionCode:'SUB_baker'});tx.reference='renewal-2';tx.paid_at='2026-11-12T10:00:00Z';const event={event:'invoice.update',data:{paid:true,subscription:{subscription_code:'SUB_baker'},transaction:{reference:'renewal-2'}}};assert.equal((await webhook(event)).status,200);assert.equal(records.get('subscriptionPayments/renewal-2').uid,'baker1');assert.equal(records.get('users/baker1').subscriptionExpiresAt.toISOString(),'2026-12-12T10:00:00.000Z');await webhook(event);assert.equal(records.get('users/baker1').subscriptionExpiresAt.toISOString(),'2026-12-12T10:00:00.000Z')});

 test('checkout reports upstream failures without leaking provider payloads',async()=>{
 const original=global.fetch;
 try{
  for(const [status,code,pattern] of [[401,'failed-precondition',/secret key/],[403,'failed-precondition',/denied access/],[404,'failed-precondition',/same Paystack account and test\/live mode/],[429,'resource-exhausted',/too many requests/],[503,'unavailable',/temporarily unavailable/]]){
   reset();global.fetch=async()=>({ok:false,status,json:async()=>({status:false,message:'private customer email and secret'})});
   await assert.rejects(api.createPaystackCheckout({auth:{uid:'baker1'},data:{plan:'monthly'}}),e=>e.code===code&&pattern.test(e.message)&&!e.message.includes('private'));
   assert.equal(updates,0);
  }
  global.fetch=async()=>{throw new Error('private network details')};
  await assert.rejects(api.createPaystackCheckout({auth:{uid:'baker1'},data:{plan:'monthly'}}),e=>e.code==='unavailable'&&/could not be reached/.test(e.message));
  global.fetch=async()=>({ok:false,status:502,json:async()=>{throw new Error('HTML')}});
  await assert.rejects(api.createPaystackCheckout({auth:{uid:'baker1'},data:{plan:'monthly'}}),e=>e.code==='unavailable'&&/HTTP 502/.test(e.message));
 }finally{global.fetch=original;}
 });

test('introductory annual checkout remains disabled unless explicitly enabled',async()=>{
 reset();
 const before=[...records.keys()];
 await assert.rejects(
  api.createAnnualLaunchCheckout({auth:{uid:'baker1'},data:{renewalConsent:{accepted:true,version:'annual-launch-r990-then-r1490-v1'}}}),
  error=>error.code==='failed-precondition'
 );
 assert.deepEqual([...records.keys()],before);
});

test('introductory monthly checkout remains disabled and does not create a payment',async()=>{
 reset();
 const before=[...records.keys()];
 await assert.rejects(
  api.createMonthlyLaunchCheckout({auth:{uid:'baker1'},data:{renewalConsent:{accepted:true,version:'monthly-r99-through-2026-then-r149-v1'}}}),
  error=>error.code==='failed-precondition'
 );
 assert.deepEqual([...records.keys()],before);
});

test('annual launch status is owner-only and omits card and customer details',async()=>{
 reset();
 const reference='BBL-12345678-1234-1234-1234-123456789abc';
 records.set('subscriptionPayments/'+reference,{
  uid:'baker1',offer:'annual_launch',status:'payment_verified',domain:'test',
  renewalSetup:'needs_reconciliation',entitlementStatus:'awaiting_support_review',
  firstYearAccessUntil:'2027-10-14T09:00:00.000Z',
  authorizationCode:'AUTH_private',customer:'CUS_private'
 });
 const status=await api.getAnnualLaunchStatus({auth:{uid:'baker1'},data:{reference}});
 assert.equal(status.renewalSetup,'needs_reconciliation');
 assert.equal(status.testMode,true);
 assert.equal(status.firstYearAccessUntil,'2027-10-14T09:00:00.000Z');
 assert.equal('authorizationCode' in status,false);
 assert.equal('customer' in status,false);
 await assert.rejects(
  api.getAnnualLaunchStatus({auth:{uid:'other'},data:{reference}}),
  error=>error.code==='permission-denied'
 );
});

test('verified first-year launch payment is recorded independently of renewal plan lookup',async()=>{
 reset();
 const reference='BBL-12345678-1234-1234-1234-123456789abc';
 records.set('subscriptionPayments/'+reference,{
  uid:'baker1',offer:'annual_launch',provider:'paystack',status:'pending',
  domain:'test',currency:'ZAR',customer:'CUS_baker',interval:'annual',
  amount:99000,renewalAmount:149000,
  renewalConsentVersion:'annual-launch-r990-then-r1490-v1',
  renewalPlanCode:'PLN_year'
 });
 tx={reference,status:'success',amount:99000,currency:'ZAR',
  customer:{customer_code:'CUS_baker'},domain:'test',paid_at:'2026-10-14T09:00:00Z',
  authorization:{authorization_code:'AUTH_test',reusable:true},id:777};
 const original=global.fetch;
 const originalKey=key;
 key='sk_test_fixture_not_a_real_key';
 const requests=[];
 global.fetch=async(url)=>{
  requests.push(String(url));
  if(String(url).includes('/plan/'))throw new Error('Unexpected future plan lookup');
  return {ok:true,json:async()=>({status:true,data:tx})};
 };
 try{
  const result=await api.verifyAnnualLaunchPayment({auth:{uid:'baker1'},data:{reference}});
  assert.equal(result.status,'payment_verified');
  assert.equal(records.get('subscriptionPayments/'+reference).firstYearAccessUntil,'2027-10-14T09:00:00.000Z');
  assert.equal(records.get('subscriptionPayments/'+reference).entitlementStatus,'awaiting_renewal_setup');
  assert.equal(records.get('users/baker1').subscriptionStatus,'free');
  assert.equal(requests.some(x=>x.includes('/plan/')),false);
 }finally{global.fetch=original;key=originalKey;}
});

test('annual launch rejects cross-environment verification without changing records',async()=>{
 reset();
 const reference='BBL-12345678-1234-1234-1234-123456789abc';
 records.set('subscriptionPayments/'+reference,{
  uid:'baker1',offer:'annual_launch',provider:'paystack',status:'payment_verified',
  domain:'test',renewalSetup:'pending',customer:'CUS_baker',amount:99000
 });
 const previous=structuredClone(records.get('subscriptionPayments/'+reference));
 await assert.rejects(
  api.verifyAnnualLaunchPayment({auth:{uid:'baker1'},data:{reference}}),
  error=>error.code==='permission-denied'
 );
 assert.deepEqual(records.get('subscriptionPayments/'+reference),previous);
 assert.equal(records.get('users/baker1').subscriptionStatus,'free');
});

test('customer can revoke a verified annual renewal before submission, but not after',async()=>{
 reset();
 const reference='BBL-12345678-1234-1234-1234-123456789abc';
 const ref='subscriptionPayments/'+reference;
 records.set(ref,{uid:'baker1',provider:'paystack',offer:'annual_launch',
  domain:'test',status:'payment_verified',renewalSetup:'pending'});
 await assert.rejects(
  api.cancelPendingAnnualLaunchRenewal({auth:{uid:'other'},data:{reference}}),
  error=>error.code==='permission-denied'
 );
 const result=await api.cancelPendingAnnualLaunchRenewal({auth:{uid:'baker1'},data:{reference}});
 assert.equal(result.renewalSetup,'cancelled');
 assert.equal(result.refundIssued,false);
 assert.equal(records.get(ref).renewalSetup,'cancelled');
 assert.equal(records.get('users/baker1').subscriptionStatus,'free');
 await api.cancelPendingAnnualLaunchRenewal({auth:{uid:'baker1'},data:{reference}});
 for(const state of ['submitting','scheduled','needs_reconciliation']){
  records.get(ref).renewalSetup=state;
  await assert.rejects(
   api.cancelPendingAnnualLaunchRenewal({auth:{uid:'baker1'},data:{reference}}),
   error=>error.code==='failed-precondition'
  );
 }
});

test('annual launch submitted cancellation request is owner-only and never claims provider cancellation',async()=>{
 reset();
 const reference='BBL-12345678-1234-1234-1234-123456789abc';
 const path='subscriptionPayments/'+reference;
 records.set(path,{uid:'baker1',provider:'paystack',offer:'annual_launch',domain:'live',
  status:'success',renewalSetup:'scheduled',subscriptionCode:'SUB_annual'});
 await assert.rejects(api.requestAnnualLaunchCancellation({auth:{uid:'other'},data:{reference}}),
  e=>e.code==='permission-denied');
 const result=await api.requestAnnualLaunchCancellation({auth:{uid:'baker1'},data:{reference}});
 assert.equal(result.cancellationStatus,'requested');
 assert.equal(result.providerCancellationConfirmed,false);
 assert.equal(records.get(path).renewalSetup,'scheduled');
 assert.equal(records.get(path).renewalCancellationStatus,'requested');
 const again=await api.requestAnnualLaunchCancellation({auth:{uid:'baker1'},data:{reference}});
 assert.equal(again.cancellationStatus,'requested');
 const status=await api.getAnnualLaunchStatus({auth:{uid:'baker1'},data:{reference}});
 assert.equal(status.renewalCancellationStatus,'requested');
 assert.equal(records.get('users/baker1').subscriptionStatus,'free');
});

test('revoked pending annual renewal remains cancelled when payment verification is repeated',async()=>{
 reset();
 const reference='BBL-12345678-1234-1234-1234-123456789abc';
 const path='subscriptionPayments/'+reference;
 records.set(path,{uid:'baker1',provider:'paystack',offer:'annual_launch',
  domain:'live',status:'payment_verified',renewalSetup:'pending',
  renewalConsentVersion:'annual-launch-r990-then-r1490-v1'});
 await api.cancelPendingAnnualLaunchRenewal({auth:{uid:'baker1'},data:{reference}});
 const original=global.fetch;
 global.fetch=async()=>{throw new Error('No new payment verification should be requested');};
 try{
  const result=await api.verifyAnnualLaunchPayment({auth:{uid:'baker1'},data:{reference}});
  assert.equal(result.renewalSetup,'cancelled');
  assert.equal(records.get(path).renewalSetup,'cancelled');
  assert.ok(records.get(path).renewalConsentRevokedAt);
  assert.equal(records.get('users/baker1').subscriptionStatus,'free');
 }finally{global.fetch=original;}
});

test('test-mode cancellation reconciliation confirms provider state without leaking tokens',async()=>{
 reset();
 const reference='BBL-12345678-1234-1234-1234-123456789abc';
 const path='subscriptionPayments/'+reference;
 records.set(path,{uid:'baker1',provider:'paystack',offer:'annual_launch',
  domain:'test',status:'success',renewalSetup:'scheduled',
  renewalCancellationStatus:'requested',subscriptionCode:'SUB_annual',
  customer:'CUS_baker',renewalPlanCode:'PLN_year'});
 const originalKey=key;const originalFetch=global.fetch;
 key='sk_test_fixture_not_a_real_key';
 let disabled=false;const calls=[];
 global.fetch=async(url,options)=>{
  calls.push(String(url));
  if(String(url).endsWith('/subscription/disable')){
   const body=JSON.parse(options.body);
   assert.equal(body.code,'SUB_annual');
   assert.equal(body.token,'private-token');
   disabled=true;
   return {ok:true,json:async()=>({status:true,data:{}})};
  }
  return {ok:true,json:async()=>({status:true,data:{
   subscription_code:'SUB_annual',customer:{customer_code:'CUS_baker'},
   plan:{plan_code:'PLN_year'},domain:'test',
   status:disabled?'non-renewing':'active',email_token:'private-token'
  }})};
 };
 try{
  // Feature flag is intentionally disabled, even in test mode.
  await assert.rejects(
   api.reconcileAnnualLaunchCancellation({auth:{uid:'baker1'},data:{reference}}),
   e=>e.code==='failed-precondition'
  );
  assert.equal(disabled,false);
  assert.equal(calls.length,0);
  promoEnabled=true;
  await assert.rejects(
   api.reconcileAnnualLaunchCancellation({auth:{uid:'someone-else'},data:{reference}}),
   e=>e.code==='permission-denied'
  );
  const result=await api.reconcileAnnualLaunchCancellation({auth:{uid:'baker1'},data:{reference}});
  assert.equal(result.providerConfirmed,true);
  assert.equal(result.state,'provider_confirmed');
  assert.equal(records.get(path).renewalCancellationStatus,'provider_confirmed');
  assert.equal(disabled,true);
  assert.equal(calls.length,3);
  assert.equal(records.get('users/baker1').subscriptionStatus,'free');
 }finally{key=originalKey;global.fetch=originalFetch;promoEnabled=false;}
});

test('provider cancellation cannot be confirmed from a mismatched follow-up lookup',async()=>{
 reset();
 const reference='BBL-12345678-1234-1234-1234-123456789abc';
 const path='subscriptionPayments/'+reference;
 records.set(path,{uid:'baker1',provider:'paystack',offer:'annual_launch',
  domain:'test',status:'success',renewalSetup:'scheduled',
  renewalCancellationStatus:'requested',subscriptionCode:'SUB_annual',
  customer:'CUS_baker',renewalPlanCode:'PLN_year'});
 const oldKey=key,oldFetch=global.fetch,oldFlag=promoEnabled;
 key='sk_test_fixture_not_a_real_key';promoEnabled=true;
 let requests=0;
 global.fetch=async(url)=>{
  requests++;
  const after=requests>2;
  return {ok:true,json:async()=>({status:true,data:
   String(url).endsWith('/subscription/disable')?{}:{
    subscription_code:after?'SUB_other':'SUB_annual',
    customer:{customer_code:'CUS_baker'},plan:{plan_code:'PLN_year'},
    domain:'test',status:after?'not_renewing':'active',email_token:'token'
   }
  })};
 };
 try{
  await assert.rejects(
   api.reconcileAnnualLaunchCancellation({auth:{uid:'baker1'},data:{reference}}),
   e=>e.code==='failed-precondition'
  );
  assert.equal(requests,3);
  assert.equal(records.get(path).renewalCancellationStatus,'requested');
 }finally{key=oldKey;global.fetch=oldFetch;promoEnabled=oldFlag;}
});

test('standard webhook synchronization cannot overwrite introductory subscription mapping',async()=>{
 reset();
 records.set('paystackCustomers/CUS_baker',{uid:'baker1'});
 const path='paystackSubscriptions/SUB_annual';
 records.set(path,{uid:'baker1',provider:'paystack',offer:'annual_launch',
  sourceReference:'BBL-12345678-1234-1234-1234-123456789abc',
  customer:'CUS_baker',domain:'live',interval:'annual',planCode:'PLN_year',
  status:'active'});
 const before=structuredClone(records.get(path));
 const original=global.fetch;
 global.fetch=async()=>({ok:true,json:async()=>({status:true,data:{
  subscription_code:'SUB_annual',status:'active',domain:'live',
  customer:{customer_code:'CUS_baker'},
  plan:{plan_code:'PLN_year',interval:'annually',currency:'ZAR'},amount:149000
 }})});
 try{
  const result=await webhook({event:'subscription.create',data:{subscription_code:'SUB_annual'}});
  assert.equal(result.status,200);
  assert.deepEqual(records.get(path),before);
  assert.equal(records.get('users/baker1').subscriptionStatus,'free');
 }finally{global.fetch=original;}
});

test('introductory renewal invoice never enters standard payment settlement',async()=>{
 reset();
 const code='SUB_annual';
 const ref='paystackSubscriptions/'+code;
 records.set('paystackCustomers/CUS_baker',{uid:'baker1'});
 records.set(ref,{uid:'baker1',provider:'paystack',offer:'annual_launch',
  sourceReference:'BBL-12345678-1234-1234-1234-123456789abc',
  customer:'CUS_baker',domain:'live',interval:'annual',planCode:'PLN_year',
  status:'active'});
 const original=global.fetch;
 global.fetch=async()=>({ok:true,json:async()=>({status:true,data:{
  subscription_code:code,status:'active',domain:'live',
  customer:{customer_code:'CUS_baker'},
  plan:{plan_code:'PLN_year',interval:'annually',currency:'ZAR'},amount:149000
 }})});
 try{
  const result=await webhook({event:'invoice.update',data:{
   paid:true,subscription:{subscription_code:code},transaction:{reference:'renewal-launch-1'}
  }});
  assert.equal(result.status,200);
  assert.equal(records.has('subscriptionPayments/renewal-launch-1'),false);
  assert.equal(records.get('users/baker1').subscriptionStatus,'free');
  assert.equal(records.get(ref).offer,'annual_launch');
  const queued=[...records.entries()].filter(([k])=>k.startsWith('paystackLaunchReconciliation/'));
  assert.equal(queued.length,1);
  assert.equal(queued[0][1].eventType,'invoice.update');
  assert.equal(queued[0][1].transactionReference,'renewal-launch-1');
  await webhook({event:'invoice.update',data:{
   paid:true,subscription:{subscription_code:code},transaction:{reference:'renewal-launch-1'}
  }});
  assert.equal([...records.keys()].filter(k=>k.startsWith('paystackLaunchReconciliation/')).length,1);
 }finally{global.fetch=original;}
});

test('separate failed annual launch invoices create separate review records',async()=>{
 reset();
 const code='SUB_annual';
 records.set('paystackCustomers/CUS_baker',{uid:'baker1'});
 records.set('paystackSubscriptions/'+code,{
  uid:'baker1',offer:'annual_launch',provider:'paystack',customer:'CUS_baker',
  domain:'live',interval:'annual',planCode:'PLN_year'});
 const original=global.fetch;
 global.fetch=async()=>({ok:true,json:async()=>({status:true,data:{
  subscription_code:code,status:'active',domain:'live',amount:149000,
  customer:{customer_code:'CUS_baker'},
  plan:{plan_code:'PLN_year',interval:'annually',currency:'ZAR'}
 }})});
 try{
  for(const invoice of ['INV_first','INV_second']){
   const result=await webhook({event:'invoice.payment_failed',data:{
    invoice_code:invoice,subscription:{subscription_code:code}
   }});
   assert.equal(result.status,200);
  }
  assert.equal([...records.keys()].filter(k=>k.startsWith('paystackLaunchReconciliation/')).length,2);
 }finally{global.fetch=original;}
});
