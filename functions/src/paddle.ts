// Server-only Paddle fulfillment. Environment-scoped mirrors must never be client-writable.
import {getAuth} from 'firebase-admin/auth';
import {paymentRegion,standardPlans,checkoutAmount,isCheckoutPlan,isFreeCheckoutProfile,remotePriceMatches,completedMatches} from './paddle-checkout-core';
import { Paddle, Environment } from '@paddle/paddle-node-sdk';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { defineSecret, defineString } from 'firebase-functions/params';
import { onCall, onRequest, HttpsError } from 'firebase-functions/v2/https';

const apiKey=defineSecret('PADDLE_API_KEY');
const webhookSecret=defineSecret('PADDLE_WEBHOOK_SECRET');
const environment=defineString('PADDLE_ENVIRONMENT',{default:'sandbox'});
const mirrorEnabled=defineString('PADDLE_WEBHOOK_ENABLED',{default:'false'});
const provisionEnabled=defineString('PADDLE_PROVISION_ENABLED',{default:'false'});
const portalEnabled=defineString('PADDLE_PORTAL_ENABLED',{default:'false'});
const checkoutEnabled=defineString('PADDLE_CHECKOUT_ENABLED',{default:'false'});
const pilotUid=defineString('PADDLE_TEST_UID',{default:''});
const checkoutToken=defineString('PADDLE_CLIENT_TOKEN',{default:''});
const priceMap=defineString('PADDLE_PRO_PRICE_MAP',{default:'{}'});

type Mode='sandbox'|'live';
type Json=Record<string,unknown>;
type Event={event_id:string;event_type:string;occurred_at:string;data:Json};
type Price={productId:string;interval:'week'|'month'|'year';offer?:string};
const eventTypes=new Set(['subscription.created','subscription.updated','subscription.canceled','subscription.activated','subscription.trialing','subscription.paused','subscription.resumed','subscription.past_due','customer.created','customer.updated','transaction.completed']);
const id=(value:unknown,prefix:string):value is string=>typeof value==='string'&&new RegExp('^'+prefix+'_[a-z0-9]{26}$').test(value);
const obj=(value:unknown):Json=>value&&typeof value==='object'&&!Array.isArray(value)?value as Json:{};
const str=(value:unknown):string=>typeof value==='string'?value:'';
const millis=(value:unknown):number=>typeof value==='string'?Date.parse(value):NaN;

function mode():Mode{
 const value=environment.value();
 if(value!=='sandbox'&&value!=='live')throw Error('Invalid Paddle environment');
 return value;
}
function client(m:Mode){
 const key=apiKey.value();
 if(!key)throw Error('Missing Paddle API key');
 return new Paddle(key,{environment:m==='sandbox'?Environment.sandbox:Environment.production});
}
export function grantsPaddleAccess(status:unknown,expiresAt:unknown,now=Date.now()):boolean{
 return (status==='active'||status==='trialing')&&typeof expiresAt==='number'&&Number.isFinite(expiresAt)&&expiresAt>now;
}
export function shouldApplyPaddleEvent(previousMs:unknown,nextMs:number):boolean{
 return Number.isFinite(nextMs)&&(typeof previousMs!=='number'||nextMs>previousMs);
}
export function mappedPaddlePrice(data:Json,configured:Record<string,Price>):Price|undefined{
 const items=data.items;
 if(!Array.isArray(items)||items.length!==1)return undefined;
 const item=obj(items[0]),price=obj(item.price);
 if(item.quantity!==1||!id(price.id,'pri')||!id(price.product_id,'pro'))return undefined;
 const mapped=configured[price.id],cycle=obj(price.billing_cycle);
 if(!mapped||mapped.productId!==price.product_id||mapped.interval!==cycle.interval||cycle.frequency!==1)return undefined;
 return mapped;
}
function config():Record<string,Price>{
 const result=JSON.parse(priceMap.value()) as Record<string,Price>;
 if(!result||typeof result!=='object'||Array.isArray(result))throw Error('Invalid Paddle price map');
 for(const [key,value] of Object.entries(result)){
  if(!id(key,'pri')||!value||!id(value.productId,'pro')||!['week','month','year'].includes(value.interval))throw Error('Invalid Paddle price entry');
 }
 return result;
}
export function parseVerifiedPaddleEvent(raw:string):Event{
 const e=obj(JSON.parse(raw));
 if(!id(e.event_id,'evt')||typeof e.event_type!=='string'||!Number.isFinite(millis(e.occurred_at))||!e.data||typeof e.data!=='object')throw Error('Invalid event envelope');
 return {event_id:e.event_id,event_type:e.event_type,occurred_at:str(e.occurred_at),data:obj(e.data)};
}
function subscriptionSnapshot(data:Json){
 const period=obj(data.current_billing_period),change=obj(data.scheduled_change);
 const end=millis(period.ends_at),next=millis(data.next_billed_at);
 const expiry=Number.isFinite(end)?end:(data.status==='trialing'&&Number.isFinite(next)?next:null);
 return {customerId:str(data.customer_id),status:str(data.status),expiresAtMs:expiry,scheduledAction:str(change.action)||null,scheduledAt:str(change.effective_at)||null,items:Array.isArray(data.items)?data.items:[],updatedAt:str(data.updated_at)||null};
}


// Only a signed-in, explicitly allowed pilot account may initiate Paddle checkout.
// All requests use a server-created transaction, never arbitrary client-side price IDs.
function checkoutReady(uid:string,m:Mode){
 const token=checkoutToken.value();
 return checkoutEnabled.value()==='true'&&mirrorEnabled.value()==='true'&&!!pilotUid.value()&&pilotUid.value()===uid&&
  (m==='sandbox'?token.startsWith('test_'):token.startsWith('live_')&&provisionEnabled.value()==='true');
}
const appOrigin='https://app.batchboss.co.za';
async function paddleApi(m:Mode,path:string,body?:Json):Promise<Json>{
 const origin=m==='live'?'https://api.paddle.com':'https://sandbox-api.paddle.com';
 const key=apiKey.value();
 if(!key)throw new HttpsError('failed-precondition','Paddle API configuration is missing.');
 try{
  const response=await fetch(origin+path,{method:body?'POST':'GET',headers:{Authorization:'Bearer '+key,'Content-Type':'application/json','Paddle-Version':'1'},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(20000)});
  if(!response.ok)throw Error('Paddle request rejected');
  const result=obj(await response.json());
  return obj(result.data);
 }catch{throw new HttpsError('unavailable','Paddle could not complete the request. Contact support before retrying.');}
}
async function accountRegion(uid:string){
 const db=getFirestore(),profile=(await db.doc('users/'+uid).get()).data();
 if(!profile||typeof profile.bakeryId!=='string'||!profile.bakeryId||profile.bakeryId.includes('/'))return null;
 const bakery=(await db.doc('bakeries/'+profile.bakeryId).get()).data();
 if(!bakery||bakery.ownerUid!==uid)return null;
 return paymentRegion(bakery);
}
function planPrice(plan:keyof typeof standardPlans){
 const entries=Object.entries(config()).filter(([,price])=>price.interval===standardPlans[plan].interval&&!price.offer);
 return entries.length===1?{priceId:entries[0][0],...entries[0][1]}:null;
}
export const getPaddleCheckoutOptions=onCall({region:'europe-west1',invoker:'public',cors:[appOrigin]},async request=>{
 if(!request.auth||request.auth.token.firebase?.sign_in_provider==='anonymous')throw new HttpsError('unauthenticated','Sign in first.');
 const uid=request.auth.uid,m=mode(),route=await accountRegion(uid);
 if(!route||route.provider!=='paddle'||!checkoutReady(uid,m))return {enabled:false,plans:[]};
 const plans=(['weekly','monthly','annual'] as const).filter(p=>planPrice(p)).map(p=>({plan:p,amount:checkoutAmount(p,route.currency),currency:route.currency}));
 return {enabled:plans.length>0,plans,environment:m};
});
export const createPaddleCheckout=onCall({region:'europe-west1',invoker:'public',cors:[appOrigin],secrets:[apiKey]},async request=>{
 if(!request.auth||request.auth.token.firebase?.sign_in_provider==='anonymous')throw new HttpsError('unauthenticated','Sign in first.');
 const uid=request.auth.uid,m=mode(),plan:unknown=request.data?.plan;
 if(!checkoutReady(uid,m))throw new HttpsError('failed-precondition','Paddle checkout is not enabled for this account.');
 if(!isCheckoutPlan(plan))throw new HttpsError('invalid-argument','Choose a standard Pro plan.');
 const route=await accountRegion(uid);
 if(!route||route.provider!=='paddle')throw new HttpsError('failed-precondition','Use your local subscription checkout.');
 const mapped=planPrice(plan),amount=checkoutAmount(plan,route.currency);
 if(!mapped||amount===undefined)throw new HttpsError('failed-precondition','This regional plan is not configured.');
 const currentUser=await getAuth().getUser(uid);
 if(!currentUser.email||!currentUser.emailVerified||currentUser.disabled)throw new HttpsError('failed-precondition','Verify your account email before subscribing.');
 const remote=await paddleApi(m,'/prices/'+mapped.priceId);
 if(!remotePriceMatches(remote,{...mapped,country:route.country,currency:route.currency,amount}))throw new HttpsError('failed-precondition','Paddle price does not match the regional catalog.');
 const db=getFirestore(),base=db.collection('paddleBilling').doc(m),lock=base.collection('checkoutLocks').doc(uid),profile=db.collection('users').doc(uid);
 // A pending transaction may still be payable. Never expire the lock on a timer.
 // Failed/ambiguous provider calls require explicit reconciliation, not a second purchase.
 const previous=await db.runTransaction(async tx=>{
  const [p,l,ps,a]=await Promise.all([tx.get(profile),tx.get(lock),tx.get(db.doc('paystackCheckouts/'+uid)),tx.get(base.collection('accountBindings').doc(uid))]);
  if(!isFreeCheckoutProfile(p.data())||ps.exists||a.exists)throw new HttpsError('failed-precondition','Manage existing billing with support before opening a new checkout.');
  const old=l.data();
  if(old){
   if(old.plan===plan&&old.status==='ready'&&id(old.transactionId,'txn'))return old.transactionId as string;
   throw new HttpsError('failed-precondition','A payment attempt is already linked. Contact support before opening another.');
  }
  tx.create(lock,{uid,plan,status:'creating',country:route.country,currency:route.currency,createdAt:FieldValue.serverTimestamp()});
  return null;
 });
 if(previous)return {transactionId:previous,token:checkoutToken.value(),environment:m};
 // Require a fresh provider customer. Never claim an existing customer solely by email.
 const customer=await paddleApi(m,'/customers',{email:currentUser.email});
 if(!id(customer.id,'ctm'))throw new HttpsError('unavailable','Provider customer setup needs review.');
 await lock.update({customerId:customer.id});
 const customerId=customer.id;
 const transaction=await paddleApi(m,'/transactions',{items:[{price_id:mapped.priceId,quantity:1}],customer_id:customerId,currency_code:route.currency,collection_mode:'automatic'});
 if(!id(transaction.id,'txn')||transaction.customer_id!==customerId||transaction.currency_code!==route.currency)throw new HttpsError('unavailable','Provider transaction setup needs review.');
 const transactionId=transaction.id,customerOwner=base.collection('customerBindings').doc(customerId);
 await db.runTransaction(async tx=>{
  const [l,owner]=await Promise.all([tx.get(lock),tx.get(customerOwner)]);
  if(l.data()?.status!=='creating'||l.data()?.customerId!==customerId||owner.exists)throw new HttpsError('failed-precondition','Billing owner needs review.');
  tx.create(customerOwner,{uid,createdAt:FieldValue.serverTimestamp()});
  tx.create(base.collection('checkoutIntents').doc(transactionId),{uid,transactionId,customerId,priceId:mapped.priceId,productId:mapped.productId,interval:mapped.interval,plan,currency:route.currency,country:route.country,amount,status:'pending',createdAt:FieldValue.serverTimestamp()});
  tx.update(lock,{status:'ready',transactionId});
 });
 return {transactionId,token:checkoutToken.value(),environment:m};
});
function projection(profile:Json,entityId:string,snap:ReturnType<typeof subscriptionSnapshot>,prices:Record<string,Price>,live:boolean){
 if(!live)return null;
 const current=profile.subscriptionSource,owns=profile.paddleSubscriptionId===entityId&&current==='paddle';
 const price=mappedPaddlePrice({items:snap.items},prices);
 if((owns||isFreeCheckoutProfile(profile))&&price&&grantsPaddleAccess(snap.status,snap.expiresAtMs)){
  return {subscriptionPlan:'pro',subscriptionSource:'paddle',subscriptionStatus:snap.status,subscriptionExpiresAt:new Date(snap.expiresAtMs!),subscriptionBillingInterval:price.interval,subscriptionOffer:price.offer??null,paddleCustomerId:snap.customerId,paddleSubscriptionId:entityId,updatedAt:FieldValue.serverTimestamp()};
 }
 if(owns)return {subscriptionStatus:snap.status==='canceled'?'cancelled':snap.status==='past_due'?'past_due':'expired',subscriptionExpiresAt:new Date(),updatedAt:FieldValue.serverTimestamp()};
 // Store verified ownership even if the subscription initially arrives inactive.
 if(isFreeCheckoutProfile(profile)&&price)return {subscriptionPlan:'pro',subscriptionSource:'paddle',subscriptionStatus:'expired',subscriptionExpiresAt:new Date(),paddleCustomerId:snap.customerId,paddleSubscriptionId:entityId,updatedAt:FieldValue.serverTimestamp()};
 return null;
}

// All binding records are created by trusted server-side checkout reconciliation,
// never from a webhook email or client-supplied custom data. No binding = mirror only.
async function applyEvent(m:Mode,e:Event){
 if(!eventTypes.has(e.event_type))return 'ignored';
 const db=getFirestore(),base=db.collection('paddleBilling').doc(m);
 const kind=e.event_type.split('.')[0],prefix=kind==='subscription'?'sub':kind==='customer'?'ctm':'txn';
 if(!id(e.data.id,prefix))throw Error('Invalid Paddle entity ID');
 const entityId=e.data.id,occurredAtMs=millis(e.occurred_at);
 const ref=base.collection(kind+'s').doc(entityId),receipt=base.collection('events').doc(e.event_id);
 const prices=kind==='subscription'||kind==='transaction'?config():{};
 const liveProvision=m==='live'&&provisionEnabled.value()==='true';
 return db.runTransaction(async tx=>{
  const [seen,old]=await Promise.all([tx.get(receipt),tx.get(ref)]);
  if(seen.exists)return 'duplicate';
  const oldData=old.data();
  const isNew=shouldApplyPaddleEvent(oldData?.occurredAtMs,occurredAtMs);
  let binding:Json={},profile:Json={},profileRef:FirebaseFirestore.DocumentReference|undefined;
  if(kind==='subscription'&&isNew&&liveProvision){
   const b=await tx.get(base.collection('subscriptionBindings').doc(entityId));
   binding=obj(b.data());
   if(typeof binding.uid==='string'&&binding.uid.length>0&&!binding.uid.includes('/')&&binding.customerId===e.data.customer_id){
    profileRef=db.collection('users').doc(binding.uid);
    const p=await tx.get(profileRef);
    if(p.exists)profile=obj(p.data());else profileRef=undefined;
   }
  }
  // Completion can arrive before or after subscription.created. Both paths reconcile
  // from the verified subscription mirror, never from the browser or a transaction alone.
  if(kind==='transaction'&&isNew&&e.event_type==='transaction.completed'){
   const intentRef=base.collection('checkoutIntents').doc(entityId),intent=(await tx.get(intentRef)).data();
   if(intent&&intent.status==='pending'){
    const uid=intent.uid,customerId=intent.customerId,subId=e.data.subscription_id;
    if(!completedMatches(e.data,intent)||typeof uid!=='string'||!uid||uid.includes('/')||!id(customerId,'ctm')||!id(subId,'sub')){
     tx.update(intentRef,{status:'review',reason:'provider-completion-mismatch',updatedAt:FieldValue.serverTimestamp()});
    }else{
     const ownerRef=base.collection('customerBindings').doc(customerId),accountRef=base.collection('accountBindings').doc(uid),subRef=base.collection('subscriptionBindings').doc(subId),lockRef=base.collection('checkoutLocks').doc(uid),userRef=db.collection('users').doc(uid);
     const [owner,account,sub,lock,user,mirror]=await Promise.all([tx.get(ownerRef),tx.get(accountRef),tx.get(subRef),tx.get(lockRef),tx.get(userRef),tx.get(base.collection('subscriptions').doc(subId))]);
     const mirrored=mirror.data();
     if(owner.data()?.uid!==uid||account.exists||sub.exists||lock.data()?.transactionId!==entityId||!isFreeCheckoutProfile(user.data())||(mirrored&&mirrored.customerId!==customerId)){
      tx.update(intentRef,{status:'review',reason:'ownership-conflict',updatedAt:FieldValue.serverTimestamp()});
     }else{
      tx.create(accountRef,{uid,customerId,subscriptionId:subId,createdAt:FieldValue.serverTimestamp()});
      tx.create(subRef,{uid,customerId,transactionId:entityId,createdAt:FieldValue.serverTimestamp()});
      tx.update(intentRef,{status:'linked',subscriptionId:subId,updatedAt:FieldValue.serverTimestamp()});
      tx.update(lockRef,{status:'linked',subscriptionId:subId});
      if(mirrored){
       const patch=projection(obj(user.data()),subId,mirrored as ReturnType<typeof subscriptionSnapshot>,prices,liveProvision);
       if(patch)tx.update(userRef,patch);
      }
     }
    }
   }
  }
  if(isNew){
   const data=kind==='subscription'?subscriptionSnapshot(e.data):kind==='customer'?{email:str(e.data.email),status:str(e.data.status)}:{customerId:str(e.data.customer_id),subscriptionId:str(e.data.subscription_id)||null,status:str(e.data.status),currency:str(e.data.currency_code),totals:obj(obj(e.data.details).totals)};
   tx.set(ref,{...data,occurredAtMs,lastEventId:e.event_id,updatedAt:FieldValue.serverTimestamp()});
   if(kind==='subscription'&&profileRef){
    const patch=projection(profile,entityId,subscriptionSnapshot(e.data),prices,liveProvision);
    if(patch)tx.update(profileRef,patch);
   }
  }
  tx.create(receipt,{eventType:e.event_type,entityId,occurredAtMs,applied:isNew,receivedAt:FieldValue.serverTimestamp()});
  return isNew?'mirrored':'stale';
 });
}

export const paddleWebhook=onRequest({region:'europe-west1',invoker:'public',secrets:[apiKey,webhookSecret]},async(req,res)=>{
 if(req.method!=='POST'){res.status(405).send('Method not allowed');return;}
 if(mirrorEnabled.value()!=='true'){res.status(503).send('Paddle webhook disabled');return;}
 const signature=req.get('Paddle-Signature');
 if(!signature||!Buffer.isBuffer(req.rawBody)){res.status(400).send('Missing signature or raw body');return;}
 let m:Mode,e:Event;
 try{
  m=mode();
  const raw=req.rawBody.toString('utf8');
  const secret=webhookSecret.value();
  if(!secret)throw Error('Missing signing secret');
  const verified=await client(m).webhooks.unmarshal(raw,secret,signature);
  if(!verified){res.status(400).send('Invalid signature');return;}
  e=parseVerifiedPaddleEvent(raw);
 }catch{res.status(400).send('Webhook verification failed');return;}
 try{await applyEvent(m,e);res.status(200).send('OK');}
 catch{res.status(500).send('Webhook processing failed');}
});

export const createPaddlePortalSession=onCall({region:'europe-west1',invoker:'public',secrets:[apiKey]},async request=>{
 if(!request.auth||request.auth.token.firebase?.sign_in_provider==='anonymous')throw new HttpsError('unauthenticated','Sign in first.');
 if(portalEnabled.value()!=='true')throw new HttpsError('failed-precondition','Paddle account management is not enabled.');
 const m=mode(),uid=request.auth.uid,base=getFirestore().collection('paddleBilling').doc(m);
 const binding=(await base.collection('accountBindings').doc(uid).get()).data();
 if(!binding||!id(binding.customerId,'ctm')||!id(binding.subscriptionId,'sub'))throw new HttpsError('failed-precondition','No verified Paddle subscription is linked.');
 const sub=(await base.collection('subscriptionBindings').doc(binding.subscriptionId).get()).data();
 if(sub?.uid!==uid||sub?.customerId!==binding.customerId)throw new HttpsError('permission-denied','Subscription ownership could not be verified.');
 try{
  const session=await client(m).customerPortalSessions.create(binding.customerId,[binding.subscriptionId]);
  const url=new URL(session.urls.general.overview);
  if(url.protocol!=='https:'||!['customer-portal.paddle.com','sandbox-customer-portal.paddle.com'].includes(url.hostname))throw Error('Unexpected portal host');
  return {url:url.href};
 }catch{throw new HttpsError('unavailable','Paddle account management is temporarily unavailable.');}
});
