const {test}=require('node:test');const assert=require('node:assert/strict');const Module=require('node:module');const {createHmac}=require('node:crypto');
const records=new Map();let updates=0;let queue=Promise.resolve();const key='sk_live_fixture_not_a_real_key';
class Ref{constructor(path){this.path=path;this.id=path.split('/').pop()} async get(){return {exists:records.has(this.path),data:()=>records.get(this.path),id:this.id,ref:this}}async set(data,opt){records.set(this.path,opt?.merge?{...records.get(this.path),...data}:data)}async update(data){updates++;records.set(this.path,{...records.get(this.path),...data})}async delete(){records.delete(this.path)}}
const db={doc:p=>new Ref(p),collection:p=>({where:(field,op,value)=>({get:async()=>({docs:await Promise.all([...records.keys()].filter(k=>k.startsWith(p+'/')&&records.get(k)[field]===value).map(k=>new Ref(k).get()))})})}),runTransaction:fn=>{const result=queue.then(()=>fn({get:r=>r.get(),set:(r,d,o)=>r.set(d,o),update:(r,d)=>r.update(d)}));queue=result.catch(()=>{});return result}};
class HttpsError extends Error{constructor(code,message){super(message);this.code=code}}
const old=Module._load;Module._load=function(id,...args){if(id==='firebase-admin/firestore')return {getFirestore:()=>db,FieldValue:{serverTimestamp:()=>new Date()}};if(id==='firebase-admin/auth')return {getAuth:()=>({getUser:async()=>({email:'fixture@example.com'})})};if(id==='firebase-functions/params')return {defineSecret:()=>({value:()=>key}),defineString:name=>({value:()=>name==='PAYSTACK_ENABLED'?'true':name==='PAYSTACK_MONTHLY_PLAN_CODE'?'PLN_month':'PLN_year'})};if(id==='firebase-functions/v2/https')return {HttpsError,onCall:(o,fn)=>fn,onRequest:(o,fn)=>fn};if(id==='firebase-functions/v2/scheduler')return {onSchedule:(o,fn)=>fn};return old.call(this,id,...args)};
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
  api.createAnnualLaunchCheckout({auth:{uid:'baker1'},data:{renewalConsent:{accepted:true,version:'annual-launch-r990-then-r1199-v1'}}}),
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
