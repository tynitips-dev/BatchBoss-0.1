// Isolated provider fixtures: real Paddle SDK signature verification, fake Firestore
// and outbound Paddle REST. Never connects to a live account or Firebase project.
const {test}=require('node:test');
const assert=require('node:assert/strict');
const Module=require('node:module');
const {createHmac}=require('node:crypto');
const records=new Map();
const clone=v=>v===undefined?undefined:structuredClone(v);
class Ref{
 constructor(path){this.path=path;this.id=path.split('/').at(-1)}
 collection(name){return {doc:id=>new Ref(this.path+'/'+name+'/'+id)}}
 async get(){const value=clone(records.get(this.path));return {exists:records.has(this.path),data:()=>clone(value),ref:this,id:this.id}}
 async update(data){if(!records.has(this.path))throw Error('missing fixture document');records.set(this.path,{...records.get(this.path),...clone(data)})}
}
const db={doc:p=>new Ref(p),collection:p=>({doc:id=>new Ref(p+'/'+id)}),async runTransaction(fn){
 const writes=[];let writing=false;
 const tx={get:async r=>{if(writing)throw Error('Firestore requires all reads before writes');return r.get()},create:(r,d)=>{writing=true;writes.push(['create',r,d])},set:(r,d,o)=>{writing=true;writes.push([o?.merge&&records.has(r.path)?'update':'set',r,d])},update:(r,d)=>{writing=true;writes.push(['update',r,d])}};
 const result=await fn(tx);
 for(const [op,r,d] of writes){if(op==='create'&&records.has(r.path))throw Error('duplicate document');if(op==='update'&&!records.has(r.path))throw Error('missing document');records.set(r.path,op==='update'?{...records.get(r.path),...clone(d)}:clone(d))}
 return result;
}};
class HttpsError extends Error{constructor(code,message){super(message);this.code=code}}
const ids={customer:'ctm_'+'c'.repeat(26),sub:'sub_'+'s'.repeat(26),txn:'txn_'+'t'.repeat(26),address:'add_'+'a'.repeat(26),monthly:'pri_'+'m'.repeat(26),annual:'pri_'+'y'.repeat(26),product:'pro_'+'p'.repeat(26),finder:'pri_'+'f'.repeat(26),suite:'pri_'+'u'.repeat(26),market:'pri_'+'k'.repeat(26),marketTxn:'txn_'+'v'.repeat(26),marketProduct:'pro_'+'m'.repeat(26)};
const signing='pdl_ntfset_fixture_secret_never_real';
const config={PADDLE_ENVIRONMENT:'live',PADDLE_WEBHOOK_ENABLED:'true',PADDLE_PROVISION_ENABLED:'true',PADDLE_PORTAL_ENABLED:'true',PADDLE_CHECKOUT_ENABLED:'true',PADDLE_TEST_UID:'baker1',PADDLE_CLIENT_TOKEN:'live_fixture',PADDLE_API_KEY:'pdl_live_apikey_'+ 'x'.repeat(64),PADDLE_WEBHOOK_SECRET:signing,PADDLE_LISTING_SUBSCRIPTIONS_ENABLED:'true',PADDLE_MARKETPLACE_ENABLED:'true',PADDLE_MARKETPLACE_PRICE_ID:ids.market,PADDLE_MARKETPLACE_PRODUCT_ID:ids.marketProduct,PADDLE_PRO_PRICE_MAP:JSON.stringify({[ids.monthly]:{productId:ids.product,interval:'month'},[ids.annual]:{productId:ids.product,interval:'year'},[ids.finder]:{productId:ids.product,interval:'month',plan:'finder'},[ids.suite]:{productId:ids.product,interval:'month',plan:'full_suite'}})};
const oldLoad=Module._load;
Module._load=function(name,...args){
 if(name==='firebase-admin/firestore')return {getFirestore:()=>db,FieldValue:{serverTimestamp:()=>new Date()}};
 if(name==='firebase-admin/auth')return {getAuth:()=>({getUser:async uid=>({uid,email:'fixture@example.com',emailVerified:true,disabled:false})})};
 if(name==='firebase-functions/params')return {defineSecret:n=>({value:()=>config[n]}),defineString:(n,o)=>({value:()=>config[n]??o?.default})};
 if(name==='firebase-functions/v2/https')return {HttpsError,onRequest:(_o,fn)=>fn,onCall:(_o,fn)=>fn};
 return oldLoad.call(this,name,...args);
};
const api=require('../functions/lib/paddle.js');
Module._load=oldLoad;
const root=()=> 'paddleBilling/'+config.PADDLE_ENVIRONMENT+'/';
const user=()=>records.get('users/baker1');
const set=(path,data)=>records.set(path,clone(data));
const request=(uid='baker1',data={})=>({auth:{uid,token:{firebase:{sign_in_provider:'password'}}},data});
const price=(annual=false)=>({id:annual?ids.annual:ids.monthly,product_id:ids.product,billing_cycle:{interval:annual?'year':'month',frequency:1}});
const item=(annual=false)=>({quantity:1,price:{...price(annual),status:'active',tax_mode:'account_setting',unit_price:{amount:annual?'6999':'849',currency_code:'GBP'},quantity:{minimum:1,maximum:1},unit_price_overrides:[]}});
const plus=(days)=>new Date(Date.now()+days*86400000).toISOString();
const future=plus(7),renewal=plus(37);
let seq=0,requests=[],addressCountry='GB',checkoutPlan='monthly';
function reset(mode='live'){
 records.clear();seq=0;requests=[];addressCountry='GB';checkoutPlan='monthly';config.PADDLE_MARKETPLACE_ENABLED='true';config.PADDLE_LISTING_SUBSCRIPTIONS_ENABLED='true';config.PADDLE_ENVIRONMENT=mode;config.PADDLE_CLIENT_TOKEN=mode==='live'?'live_fixture':'test_fixture';config.PADDLE_PROVISION_ENABLED='true';config.PADDLE_CHECKOUT_ENABLED='true';
 set('users/baker1',{subscriptionPlan:'free',subscriptionStatus:'free',bakeryId:'bakery1'});
 set('bakeries/bakery1',{ownerUid:'baker1',countryCode:'GB',currency:'GBP'});
}
global.fetch=async(url,opts)=>{
 requests.push([url,opts?.method]);
 assert.equal(new URL(url).hostname,config.PADDLE_ENVIRONMENT==='live'?'api.paddle.com':'sandbox-api.paddle.com');
 const path=new URL(url).pathname;let data;
 if(path==='/prices/'+ids.monthly)data={...price(),status:'active',unit_price:{amount:'1099',currency_code:'USD'},unit_price_overrides:[{country_codes:['GB'],unit_price:{amount:'849',currency_code:'GBP'}}]};
 else if(path==='/prices/'+ids.finder||path==='/prices/'+ids.suite){const finder=path.endsWith(ids.finder);data={...price(),id:finder?ids.finder:ids.suite,status:'active',unit_price:{amount:finder?'399':'1699',currency_code:'USD'},unit_price_overrides:[{country_codes:['GB'],unit_price:{amount:finder?'299':'1299',currency_code:'GBP'}}]};}
 else if(path==='/prices/'+ids.market)data={id:ids.market,product_id:ids.marketProduct,status:'active',billing_cycle:null,trial_period:null,unit_price:{amount:'149',currency_code:'USD'},unit_price_overrides:[{country_codes:['GB'],unit_price:{amount:'119',currency_code:'GBP'}}]};
 else if(path==='/customers'){assert.equal(JSON.parse(opts.body).email,'fixture@example.com');data={id:ids.customer}}
 else if(path==='/transactions'){const body=JSON.parse(opts.body);assert.equal(body.customer_id,ids.customer);assert.equal(body.currency_code,'GBP');assert.deepEqual(body.items,[{price_id:checkoutPlan==='marketplace'?ids.market:checkoutPlan==='finder'?ids.finder:checkoutPlan==='full_suite'?ids.suite:ids.monthly,quantity:1}]);data={id:checkoutPlan==='marketplace'?ids.marketTxn:ids.txn,customer_id:ids.customer,currency_code:'GBP'}}
 else if(path==='/transactions/'+ids.txn)data={id:ids.txn,customer_id:ids.customer,currency_code:'GBP',status:'ready'};
 else if(path==='/transactions/'+ids.marketTxn)data={id:ids.marketTxn,customer_id:ids.customer,currency_code:'GBP',status:'ready'};
 else if(path==='/customers/'+ids.customer+'/addresses/'+ids.address)data={id:ids.address,customer_id:ids.customer,country_code:addressCountry};
 else throw Error('Unexpected fixture API path '+path);
 return {ok:true,json:async()=>({data})};
};
function event(type,data,at){
 const n=++seq;
 return {event_id:'evt_'+n.toString(36).padStart(26,'0'),event_type:type,occurred_at:at??new Date(Date.now()+n*1000).toISOString(),data};
}
function subscription(status='active',expiry=future,extra={}){
 return {id:ids.sub,customer_id:ids.customer,status,billing_cycle:{interval:'month',frequency:1},current_billing_period:expiry?{starts_at:plus(-1),ends_at:expiry}:null,next_billed_at:expiry,items:[item()],...extra};
}
function completion(extra={}){
 return {id:ids.txn,subscription_id:ids.sub,customer_id:ids.customer,address_id:ids.address,currency_code:'GBP',collection_mode:'automatic',origin:'api',status:'completed',items:[item()],payments:[],details:{tax_rates_used:[],line_items:[],totals:{total:'0'}},...extra}
}
async function hook(e,{valid=true,alter=false,raw=true}={}){
 const body=JSON.stringify(e),ts=Math.floor(Date.now()/1000),sig=createHmac('sha256',signing).update(ts+':'+body).digest('hex');let code;
 await api.paddleWebhook({method:'POST',get:key=>key==='Paddle-Signature'?'ts='+ts+';h1='+(valid?sig:'0'.repeat(64)):undefined,rawBody:raw?Buffer.from(alter?body+' ':body):undefined,body:e},{status(n){code=n;return this},send(){}});
 return code;
}
async function begin(plan='monthly'){
 checkoutPlan=plan;
 const out=await api.createPaddleCheckout(request('baker1',{plan,customerId:'ctm_forged',uid:'other',priceId:'pri_forged'}));
 assert.equal(out.transactionId,ids.txn);assert.equal(out.customerId,ids.customer);assert.equal(out.country,'GB');
 assert.equal(records.get(root()+'checkoutIntents/'+ids.txn).uid,'baker1');assert.equal(user().subscriptionStatus,'free');
 return out;
}
test('webhook verifies exact raw bytes with the real SDK before writing',async()=>{
 reset();const e=event('customer.created',{id:ids.customer,email:'fixture@example.com',status:'active'});
 assert.equal(await hook(e,{valid:false}),400);assert.equal(await hook(e,{alter:true}),400);assert.equal(await hook(e,{raw:false}),400);
 assert.equal(records.size,2);
 assert.equal(await hook(e),200);assert.equal(records.get(root()+'customers/'+ids.customer).email,'fixture@example.com');
 const size=records.size;assert.equal(await hook(e),200);assert.equal(records.size,size);
});
test('server-owned checkout and both event orders activate only after verified completion',async()=>{
 for(const reverse of [false,true]){
  reset();await begin();
  const created=event('subscription.created',subscription('trialing',null,{next_billed_at:future}));
  const done=event('transaction.completed',completion());
  assert.equal(await hook(reverse?done:created),200);assert.equal(user().subscriptionStatus,'free');
  assert.equal(await hook(reverse?created:done),200);
  assert.equal(user().subscriptionStatus,'trialing');assert.equal(user().subscriptionSource,'paddle');assert.equal(user().paddleSubscriptionId,ids.sub);
  assert.equal(user().subscriptionExpiresAt.toISOString(),future);
  assert.equal(records.get(root()+'accountBindings/baker1').customerId,ids.customer);
  const before=records.size;assert.equal(await hook(done),200);assert.equal(records.size,before);
 }
});
test('renewal, upgrade, scheduled cancellation, immediate cancellation and stale delivery',async()=>{
 reset();await begin();assert.equal(await hook(event('transaction.completed',completion())),200);
 const created=event('subscription.created',subscription());assert.equal(await hook(created),200);
 assert.equal(user().subscriptionStatus,'active');
 assert.equal(await hook(event('subscription.updated',subscription('active',renewal,{items:[item(true)]}))),200);
 assert.equal(user().subscriptionBillingInterval,'year');assert.equal(user().subscriptionExpiresAt.toISOString(),renewal);
 assert.equal(await hook(event('subscription.updated',subscription('active',renewal,{items:[item(true)],scheduled_change:{action:'cancel',effective_at:renewal}}))),200);
 assert.equal(user().subscriptionStatus,'active');assert.equal(user().subscriptionExpiresAt.toISOString(),renewal);
 assert.equal(records.get(root()+'subscriptions/'+ids.sub).scheduledAction,'cancel');
 assert.equal(await hook(event('subscription.past_due',subscription('past_due',renewal))),200);assert.equal(user().subscriptionStatus,'past_due');
 assert.equal(await hook(event('subscription.updated',subscription('active',renewal))),200);assert.equal(user().subscriptionStatus,'active');
 const canceled=event('subscription.canceled',subscription('canceled',null,{next_billed_at:null}));
 assert.equal(await hook(canceled),200);assert.equal(user().subscriptionStatus,'cancelled');
 assert.equal(await hook(event('subscription.updated',subscription('active',renewal),created.occurred_at)),200);
 assert.equal(user().subscriptionStatus,'cancelled');
});
test('wrong billing country, mismatched price and existing Paystack access never grant Paddle entitlements',async()=>{
 for(const change of ['country','price','provider']){
  reset();await begin();
  if(change==='country')addressCountry='US';
  if(change==='provider')set('users/baker1',{...user(),subscriptionSource:'paystack',subscriptionStatus:'active',subscriptionPlan:'pro'});
  assert.equal(await hook(event('subscription.created',subscription())),200);
  assert.equal(await hook(event('transaction.completed',completion(change==='price'?{items:[item(true)]}:{}))),200);
  assert.equal(records.get(root()+'checkoutIntents/'+ids.txn).status,'review');
  assert.equal(records.has(root()+'accountBindings/baker1'),false);
  assert.equal(user().subscriptionStatus,change==='provider'?'active':'free');
  if(change==='provider')assert.equal(user().subscriptionSource,'paystack');
 }
});
test('sandbox stores isolated mirrors and bindings without touching live access',async()=>{
 reset('sandbox');await begin();
 assert.equal(await hook(event('subscription.created',subscription())),200);assert.equal(await hook(event('transaction.completed',completion())),200);
 assert.equal(user().subscriptionStatus,'free');assert.ok(records.has('paddleBilling/sandbox/accountBindings/baker1'));assert.ok(!records.has('paddleBilling/live/accountBindings/baker1'));
});
test('non-pilot, local, existing paid or open Paystack accounts cannot open Paddle checkout',async()=>{
 reset();await assert.rejects(api.createPaddleCheckout(request('other',{plan:'monthly'})),e=>e.code==='failed-precondition');
 set('bakeries/bakery1',{ownerUid:'baker1',countryCode:'ZA',currency:'ZAR'});assert.equal((await api.getPaddleCheckoutOptions(request())).enabled,false);
 await assert.rejects(api.createPaddleCheckout(request('baker1',{plan:'monthly'})),e=>e.code==='failed-precondition');
 reset();set('paystackCheckouts/baker1',{expiresAtMs:Date.now()+60000});await assert.rejects(api.createPaddleCheckout(request('baker1',{plan:'monthly'})),e=>e.code==='failed-precondition');
 reset();set('users/baker1',{...user(),subscriptionSource:'paystack',subscriptionStatus:'active'});await assert.rejects(api.createPaddleCheckout(request('baker1',{plan:'monthly'})),e=>e.code==='failed-precondition');
 assert.equal(requests.some(([url])=>url.endsWith('/transactions')),false);
});
test('reopening the same pilot checkout does not create another provider transaction',async()=>{
 reset();await begin();await begin();assert.equal(requests.filter(([url])=>url.endsWith('/transactions')).length,1);
 await assert.rejects(api.createPaddleCheckout(request('baker1',{plan:'weekly'})),e=>e.code==='failed-precondition');
});

function listingItem(plan){return {...item(),price:{...item().price,id:plan==='finder'?ids.finder:ids.suite,unit_price:{amount:plan==='finder'?'299':'1299',currency_code:'GBP'}}};}
test('Paddle Finder alone grants region-bound Finder rights, not Pro; Full Suite grants all rights and cancels correctly',async()=>{
 for(const plan of ['finder','full_suite'])for(const reversed of [false,true]){
  reset();await begin(plan);
  const items=[listingItem(plan)],created=event('subscription.created',subscription('active',future,{items})),done=event('transaction.completed',completion({items}));
  assert.equal(await hook(reversed?created:done),200);assert.equal(await hook(reversed?done:created),200);
  assert.equal(user().subscriptionStatus,plan==='finder'?'free':'active');assert.equal(user().subscriptionPlan,plan==='finder'?'free':'pro');assert.equal(user().paddlePlan,plan);
  let grant=records.get('listingEntitlements/baker1');assert.equal(grant.paddleCountryCode,'GB');assert.equal(grant.paddleCurrency,'GBP');assert.equal(grant.paddleFinderExpiresAt.toISOString(),future);assert.equal(grant.paddleSuiteExpiresAt.getTime()>Date.now(),plan==='full_suite');
  assert.equal(await hook(event('subscription.updated',subscription('active',future,{items,scheduled_change:{action:'cancel',effective_at:future}}))),200);
  assert.equal(records.get('listingEntitlements/baker1').paddleFinderExpiresAt.toISOString(),future);
  assert.equal(await hook(event('subscription.canceled',subscription('canceled',null,{items,next_billed_at:null}))),200);
  grant=records.get('listingEntitlements/baker1');assert.equal(grant.paddleFinderExpiresAt.getTime(),0);assert.equal(grant.paddleSuiteExpiresAt.getTime(),0);
 }
});
test('Full Suite downgrade clears Suite rights; upgrade restores them without granting Pro for Finder',async()=>{
 reset();await begin('full_suite');let items=[listingItem('full_suite')];await hook(event('subscription.created',subscription('active',future,{items})));await hook(event('transaction.completed',completion({items})));
 assert.equal(await hook(event('subscription.updated',subscription('active',renewal,{items:[listingItem('finder')]}))),200);assert.equal(user().subscriptionStatus,'free');assert.equal(records.get('listingEntitlements/baker1').paddleSuiteExpiresAt.getTime(),0);
 assert.equal(await hook(event('subscription.updated',subscription('active',renewal,{items}))),200);assert.equal(user().subscriptionStatus,'active');assert.equal(records.get('listingEntitlements/baker1').paddleSuiteExpiresAt.toISOString(),renewal);
});
test('additional subscriptions remain disabled until explicitly enabled; sandbox does not grant listing rights',async()=>{
 reset();config.PADDLE_LISTING_SUBSCRIPTIONS_ENABLED='false';assert.ok(!(await api.getPaddleCheckoutOptions(request())).plans.some(p=>p.plan==='finder'||p.plan==='full_suite'));await assert.rejects(api.createPaddleCheckout(request('baker1',{plan:'finder'})),e=>e.code==='failed-precondition');
 reset('sandbox');await begin('finder');const items=[listingItem('finder')];await hook(event('subscription.created',subscription('active',future,{items})));await hook(event('transaction.completed',completion({items})));assert.equal(records.has('listingEntitlements/baker1'),false);
});

const listingId='12345678-1234-1234-1234-123456789abc';
const ws='listingWorkspaces/baker1/';
const listingKey=require('node:crypto').createHash('sha256').update(JSON.stringify(['baker1',listingId])).digest('hex');
const catalogPath='listingCatalogs/marketplace/entries/'+listingKey;
function seedListing(){set(ws+'drafts/'+listingId,{kind:'marketplace',countryCode:'GB',currency:'GBP',title:'Mixer',area:'London',description:'Fixture',priceCents:4321,photos:[]});}
function oneOffDone(extra={}){return completion({id:ids.marketTxn,subscription_id:null,items:[{...item(),price:{...item().price,id:ids.market,product_id:ids.marketProduct,billing_cycle:null,unit_price:{amount:'119',currency_code:'GBP'}}}],...extra});}
async function beginListing(){checkoutPlan='marketplace';const data=await api.createPaddleMarketplaceCheckout(request('baker1',{id:listingId,amount:1,uid:'forged'}));assert.equal(data.transactionId,ids.marketTxn);return data;}
test('one-off listing is paid exactly once, publishes its original item price, and never grants Pro',async()=>{
 reset();seedListing();assert.deepEqual(await api.getPaddleMarketplaceOptions(request()),{enabled:true,amount:119,currency:'GBP',environment:'live'});
 await beginListing();await beginListing();assert.equal(requests.filter(([url])=>url.endsWith('/transactions')).length,1);
 assert.equal(records.get(ws+'marketplaceCheckouts/'+listingId).status,'pending');assert.equal(records.has(catalogPath),false);
 const done=event('transaction.completed',oneOffDone());assert.equal(await hook(done),200);assert.equal(await hook(done),200);
 assert.equal(records.get(ws+'marketplaceCheckouts/'+listingId).status,'paid');assert.equal(records.get(catalogPath).priceCents,4321);assert.equal(records.get(catalogPath).standaloneProvider,'paddle');assert.equal(user().subscriptionStatus,'free');assert.equal(records.has(root()+'accountBindings/baker1'),false);
 // A later subscription reuses the securely bound customer rather than creating a new one by email.
 await begin();assert.equal(requests.filter(([url])=>url.endsWith('/customers')).length,1);
});
test('mismatched country, currency or recurring Marketplace payment is held for review',async()=>{
 for(const problem of ['country','currency','subscription']){
  reset();seedListing();await beginListing();if(problem==='country')addressCountry='US';
  const extra=problem==='currency'?{currency_code:'USD'}:problem==='subscription'?{subscription_id:ids.sub}:{};
  assert.equal(await hook(event('transaction.completed',oneOffDone(extra))),200);
  assert.equal(records.get(root()+'marketplaceIntents/'+ids.marketTxn).status,'review');assert.equal(records.has(catalogPath),false);assert.equal(user().subscriptionStatus,'free');
 }
});
test('sandbox Marketplace checkout cannot publish or lock a live draft',async()=>{
 reset('sandbox');seedListing();await beginListing();assert.equal(records.has(ws+'marketplaceCheckouts/'+listingId),false);
 assert.equal(await hook(event('transaction.completed',oneOffDone())),200);assert.equal(records.get(root()+'marketplaceIntents/'+ids.marketTxn).status,'test_paid');assert.equal(records.has(catalogPath),false);assert.equal(user().subscriptionStatus,'free');
});
test('Marketplace is independently gated and cannot sell a nonexistent, relabelled or already published draft',async()=>{
 reset();seedListing();config.PADDLE_MARKETPLACE_ENABLED='false';assert.equal((await api.getPaddleMarketplaceOptions(request())).enabled,false);await assert.rejects(beginListing,e=>e.code==='failed-precondition');
 reset();await assert.rejects(beginListing,e=>e.code==='failed-precondition');seedListing();set(ws+'drafts/'+listingId,{...records.get(ws+'drafts/'+listingId),countryCode:'US',currency:'USD'});await assert.rejects(beginListing,e=>e.code==='failed-precondition');seedListing();set(ws+'publications/'+listingId,{status:'published'});await assert.rejects(beginListing,e=>e.code==='failed-precondition');
 assert.equal(requests.some(([url])=>url.endsWith('/transactions')),false);
});

test('approved payment URL can only resume a server-owned pending transaction',async()=>{reset();await begin();const data=await api.resumePaddleCheckout(request('baker1',{transactionId:ids.txn}));assert.equal(data.transactionId,ids.txn);assert.equal(data.customerId,ids.customer);await assert.rejects(api.resumePaddleCheckout(request('baker1',{transactionId:ids.marketTxn})),e=>e.code==='permission-denied');await assert.rejects(api.resumePaddleCheckout(request('other',{transactionId:ids.txn})));const intent=records.get(root()+'checkoutIntents/'+ids.txn);intent.status='linked';await assert.rejects(api.resumePaddleCheckout(request('baker1',{transactionId:ids.txn})),e=>e.code==='permission-denied');});
