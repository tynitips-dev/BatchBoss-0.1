// Server-only Paddle fulfillment. Environment-scoped mirrors must never be client-writable.
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

// All binding records are created by trusted server-side checkout reconciliation,
// never from a webhook email or client-supplied custom data. No binding = mirror only.
async function applyEvent(m:Mode,e:Event){
 if(!eventTypes.has(e.event_type))return 'ignored';
 const db=getFirestore(),base=db.collection('paddleBilling').doc(m);
 const kind=e.event_type.split('.')[0],prefix=kind==='subscription'?'sub':kind==='customer'?'ctm':'txn';
 if(!id(e.data.id,prefix))throw Error('Invalid Paddle entity ID');
 const entityId=e.data.id,occurredAtMs=millis(e.occurred_at);
 const ref=base.collection(kind+'s').doc(entityId),receipt=base.collection('events').doc(e.event_id);
 const prices=kind==='subscription'?config():{};
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
  if(isNew){
   const data=kind==='subscription'?subscriptionSnapshot(e.data):kind==='customer'?{email:str(e.data.email),status:str(e.data.status)}:{customerId:str(e.data.customer_id),subscriptionId:str(e.data.subscription_id)||null,status:str(e.data.status),currency:str(e.data.currency_code),totals:obj(obj(e.data.details).totals)};
   tx.set(ref,{...data,occurredAtMs,lastEventId:e.event_id,updatedAt:FieldValue.serverTimestamp()});
   if(kind==='subscription'&&profileRef){
    const current=profile.subscriptionSource;
    // Never overwrite a different provider or a promotional grant. Require an existing
    // server-owned binding and exact current subscription when revoking access.
    const linked=profile.paddleSubscriptionId===entityId;
    const free=profile.subscriptionPlan==='free'&&profile.subscriptionStatus==='free'&&!current;
    const owns=linked&&current==='paddle';
    const price=mappedPaddlePrice(e.data,prices),snap=subscriptionSnapshot(e.data);
    if((owns||free)&&price&&grantsPaddleAccess(snap.status,snap.expiresAtMs)){
     tx.update(profileRef,{subscriptionPlan:'pro',subscriptionSource:'paddle',subscriptionStatus:snap.status,subscriptionExpiresAt:new Date(snap.expiresAtMs!),subscriptionBillingInterval:price.interval,subscriptionOffer:price.offer??null,paddleCustomerId:snap.customerId,paddleSubscriptionId:entityId,updatedAt:FieldValue.serverTimestamp()});
    }else if(owns){
     const status=snap.status==='canceled'?'cancelled':snap.status==='past_due'?'past_due':'expired';
     tx.update(profileRef,{subscriptionStatus:status,subscriptionExpiresAt:new Date(),updatedAt:FieldValue.serverTimestamp()});
    }
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
