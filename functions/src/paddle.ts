// Server-only Paddle fulfillment. Environment-scoped mirrors must never be client-writable.
import {createHash} from 'node:crypto';
import {listingRegion,sameListingRegion,listingPermissions} from './listingRegion';
import {marketplaceFees,validListingId,oneOffPriceMatches,marketplaceCompleted} from './paddle-marketplace-core';
import {isLaunchOffer,baseLaunchPlan,launchConfig,launchDiscountMatches,SalePlan} from './paddle-launch-core';
import {getAuth} from 'firebase-admin/auth';
import {paymentRegion,standardPlans,checkoutAmount,isCheckoutPlan,isFreeCheckoutProfile,remotePriceMatches,completedMatches,configuredCheckoutPlan,planRights,CheckoutPlan} from './paddle-checkout-core';
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
const publicCheckoutEnabled=defineString('PADDLE_PUBLIC_CHECKOUT_ENABLED',{default:'false'});
const pilotUid=defineString('PADDLE_TEST_UID',{default:''});
const checkoutToken=defineString('PADDLE_CLIENT_TOKEN',{default:''});
const listingSubscriptionsEnabled=defineString('PADDLE_LISTING_SUBSCRIPTIONS_ENABLED',{default:'false'});
const marketplaceEnabled=defineString('PADDLE_MARKETPLACE_ENABLED',{default:'false'});
const marketplacePrice=defineString('PADDLE_MARKETPLACE_PRICE_ID',{default:''});
const marketplaceProduct=defineString('PADDLE_MARKETPLACE_PRODUCT_ID',{default:''});
const launchEnabled=defineString('PADDLE_LAUNCH_ENABLED',{default:'false'});
const launchMap=defineString('PADDLE_LAUNCH_DISCOUNT_MAP',{default:'{}'});
const priceMap=defineString('PADDLE_PRO_PRICE_MAP',{default:'{}'});

type Mode='sandbox'|'live';
type Json=Record<string,unknown>;
type Event={event_id:string;event_type:string;occurred_at:string;data:Json};
type Price={productId:string;interval:'week'|'month'|'year';offer?:string;plan?:CheckoutPlan};
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
  if(!id(key,'pri')||!value||!id(value.productId,'pro')||!['week','month','year'].includes(value.interval)||(value.plan!==undefined&&!configuredCheckoutPlan(value)))throw Error('Invalid Paddle price entry');
 }
 return result;
}
export function parseVerifiedPaddleEvent(raw:string):Event{
 const e=obj(JSON.parse(raw));
 if(!id(e.event_id,'evt')||typeof e.event_type!=='string'||!Number.isFinite(millis(e.occurred_at))||!e.data||typeof e.data!=='object')throw Error('Invalid event envelope');
 return {event_id:e.event_id,event_type:e.event_type,occurred_at:str(e.occurred_at),data:obj(e.data)};
}
// Paddle timestamps can contain microseconds; preserve fractional ordering instead
// of dropping distinct events that happen in the same JavaScript millisecond.
export function providerEventClock(value:unknown):number{
 const ms=millis(value);
 if(!Number.isFinite(ms))return NaN;
 const fraction=typeof value==='string'?/\.(\d+)(?:Z|[+-]\d{2}:\d{2})$/i.exec(value)?.[1]||'':'';
 const micros=Number(fraction.padEnd(6,'0').slice(3,6)||'0');
 return ms*1000+micros;
}
function subscriptionSnapshot(data:Json){
 const period=obj(data.current_billing_period),change=obj(data.scheduled_change);
 const end=millis(period.ends_at),next=millis(data.next_billed_at);
 // Trial billing periods can be null; next_billed_at is the card-backed trial end.
 // Also retain an explicitly supplied item trial end when no billing date exists.
 const trialEnds=Array.isArray(data.items)?data.items.map(item=>millis(obj(obj(item).trial_dates).ends_at)).filter(Number.isFinite):[];
 const expiry=data.status==='trialing'?(Number.isFinite(end)?end:Number.isFinite(next)?next:trialEnds.length?Math.min(...trialEnds):null):Number.isFinite(end)?end:null;
 return {customerId:str(data.customer_id),status:str(data.status),expiresAtMs:expiry,scheduledAction:str(change.action)||null,scheduledAt:str(change.effective_at)||null,items:Array.isArray(data.items)?data.items:[],updatedAt:str(data.updated_at)||null};
}


// Only a signed-in, explicitly allowed pilot account may initiate Paddle checkout.
// All requests use a server-created transaction, never arbitrary client-side price IDs.
function checkoutReady(uid:string,m:Mode){
 const token=checkoutToken.value();
 return checkoutEnabled.value()==='true'&&mirrorEnabled.value()==='true'&&((!!pilotUid.value()&&pilotUid.value()===uid)||(m==='live'&&publicCheckoutEnabled.value()==='true'))&&
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
// A shared UID-to-customer binding lets one-off buyers later subscribe without
// claiming customers by email. Ambiguous creation is held for manual reconciliation.
async function ensurePaddleCustomer(m:Mode,uid:string,email:string):Promise<string>{
 const db=getFirestore(),base=db.collection('paddleBilling').doc(m),ref=base.collection('customerAccounts').doc(uid);
 const existing=await db.runTransaction(async tx=>{
  const [current,account]=await Promise.all([tx.get(ref),tx.get(base.collection('accountBindings').doc(uid))]);
  const row=current.data();
  if(row&&row.status!=='ready')throw new HttpsError('failed-precondition','A billing customer is being prepared. Contact support before retrying.');
  const known=row?.customerId??account.data()?.customerId;
  if(known!==undefined){
   if(!id(known,'ctm'))throw new HttpsError('failed-precondition','Billing customer needs review.');
   const owner=await tx.get(base.collection('customerBindings').doc(known));
   if(owner.data()?.uid!==uid||account.exists&&account.data()?.customerId!==known)throw new HttpsError('permission-denied','Billing customer ownership mismatch.');
   if(!current.exists)tx.create(ref,{uid,customerId:known,status:'ready',createdAt:FieldValue.serverTimestamp()});
   return known;
  }
  tx.create(ref,{uid,status:'creating',createdAt:FieldValue.serverTimestamp()});return null;
 });
 if(existing)return existing;
 const customer=await paddleApi(m,'/customers',{email});
 if(!id(customer.id,'ctm'))throw new HttpsError('unavailable','Provider customer setup needs review.');
 const customerId=customer.id,owner=base.collection('customerBindings').doc(customerId);
 await db.runTransaction(async tx=>{
  const [r,o]=await Promise.all([tx.get(ref),tx.get(owner)]);
  if(r.data()?.status!=='creating'||o.exists)throw new HttpsError('failed-precondition','Billing owner needs review.');
  tx.create(owner,{uid,createdAt:FieldValue.serverTimestamp()});
  tx.update(ref,{status:'ready',customerId,updatedAt:FieldValue.serverTimestamp()});
 });
 return customerId;
}

function planPrice(plan:CheckoutPlan){
 if((plan==='finder'||plan==='full_suite')&&listingSubscriptionsEnabled.value()!=='true')return null;
 const entries=Object.entries(config()).filter(([,price])=>configuredCheckoutPlan(price)===plan);
 return entries.length===1?{priceId:entries[0][0],...entries[0][1]}:null;
}
function salePlan(plan:SalePlan,route:NonNullable<ReturnType<typeof paymentRegion>>){
 const base=isLaunchOffer(plan)?baseLaunchPlan(plan):plan;
 const mapped=planPrice(base),regular=checkoutAmount(base,route.currency);
 if(!mapped||regular===undefined)return null;
 const promo=isLaunchOffer(plan)&&launchEnabled.value()==='true'?launchConfig(launchMap.value(),plan,route.country,route.currency):null;
 if(isLaunchOffer(plan)&&!promo)return null;
 return {mapped,amount:promo?.amount??regular,regular,promo};
}
export const getPaddleCheckoutOptions=onCall({region:'europe-west1',invoker:'public',cors:[appOrigin]},async request=>{
 if(!request.auth||request.auth.token.firebase?.sign_in_provider==='anonymous')throw new HttpsError('unauthenticated','Sign in first.');
 const uid=request.auth.uid,m=mode(),route=await accountRegion(uid);
 if(!route||route.provider!=='paddle'||!checkoutReady(uid,m))return {enabled:false,plans:[]};
 const plans=(['weekly','monthly','annual','finder','full_suite','monthly_launch','annual_launch'] as const).flatMap(plan=>{const sale=salePlan(plan,route);return sale?[{plan,amount:sale.amount,currency:route.currency,...(sale.promo?{regularAmount:sale.regular,periods:sale.promo.periods,endsAt:sale.promo.endsAt}: {})}]:[]});
 return {enabled:plans.length>0,plans,environment:m};
});
export const createPaddleCheckout=onCall({region:'europe-west1',invoker:'public',cors:[appOrigin],secrets:[apiKey]},async request=>{
 if(!request.auth||request.auth.token.firebase?.sign_in_provider==='anonymous')throw new HttpsError('unauthenticated','Sign in first.');
 const uid=request.auth.uid,m=mode(),plan:unknown=request.data?.plan;
 if(!checkoutReady(uid,m))throw new HttpsError('failed-precondition','Paddle checkout is not enabled for this account.');
 if(!isCheckoutPlan(plan)&&!isLaunchOffer(plan))throw new HttpsError('invalid-argument','Choose an available BatchBoss subscription.');
 const route=await accountRegion(uid);
 if(!route||route.provider!=='paddle')throw new HttpsError('failed-precondition','Use your local subscription checkout.');
 const sale=salePlan(plan,route);
 if(!sale)throw new HttpsError('failed-precondition','This regional plan is not configured.');
 const {mapped,amount,regular,promo}=sale;
 if(promo&&request.data?.launchTermsAccepted!==true)throw new HttpsError('invalid-argument','Accept the launch renewal terms before continuing.');
 const currentUser=await getAuth().getUser(uid);
 if(!currentUser.email||!currentUser.emailVerified||currentUser.disabled)throw new HttpsError('failed-precondition','Verify your account email before subscribing.');
 const remote=await paddleApi(m,'/prices/'+mapped.priceId);
 if(!remotePriceMatches(remote,{...mapped,country:route.country,currency:route.currency,amount:regular}))throw new HttpsError('failed-precondition','Paddle price does not match the regional catalog.');
 if(promo&&!launchDiscountMatches(await paddleApi(m,'/discounts/'+promo.discountId),promo,mapped.priceId))throw new HttpsError('failed-precondition','Launch discount or renewal terms do not match the approved offer.');
 const db=getFirestore(),base=db.collection('paddleBilling').doc(m),lock=base.collection('checkoutLocks').doc(uid),profile=db.collection('users').doc(uid);
 // A pending transaction may still be payable. Never expire the lock on a timer.
 // Failed/ambiguous provider calls require explicit reconciliation, not a second purchase.
 const previous=await db.runTransaction(async tx=>{
  const [p,l,ps,a,pf,directoryAccess]=await Promise.all([tx.get(profile),tx.get(lock),tx.get(db.doc('paystackCheckouts/'+uid)),tx.get(base.collection('accountBindings').doc(uid)),tx.get(db.doc('payfastCheckouts/'+uid)),tx.get(db.doc('listingEntitlements/'+uid))]);
  if(!isFreeCheckoutProfile(p.data())||ps.exists||a.exists||pf.exists||directoryAccess.data()?.directorySubscriptionCode||directoryAccess.data()?.directoryPaymentReference)throw new HttpsError('failed-precondition','Manage existing billing with support before opening a new checkout.');
  const old=l.data();
  if(old){
   if(old.plan===plan&&old.status==='ready'&&id(old.transactionId,'txn')&&(!promo||old.discountId===promo.discountId&&old.amount===amount&&old.periods===promo.periods&&old.endsAt===promo.endsAt))return {transactionId:old.transactionId as string,customerId:old.customerId as string};
   throw new HttpsError('failed-precondition','A payment attempt is already linked. Contact support before opening another.');
  }
  tx.create(lock,{uid,plan,status:'creating',country:route.country,currency:route.currency,...(promo?{discountId:promo.discountId,amount,periods:promo.periods,endsAt:promo.endsAt}:{}),createdAt:FieldValue.serverTimestamp()});
  return null;
 });
 if(previous){if(!id(previous.customerId,'ctm'))throw new HttpsError('failed-precondition','Payment attempt needs review.');return {...previous,country:route.country,token:checkoutToken.value(),environment:m};}
 const customerId=await ensurePaddleCustomer(m,uid,currentUser.email);
 await lock.update({customerId});
 const transaction=await paddleApi(m,'/transactions',{items:[{price_id:mapped.priceId,quantity:1}],customer_id:customerId,currency_code:route.currency,collection_mode:'automatic',...(promo?{discount_id:promo.discountId}:{})});
 if(!id(transaction.id,'txn')||transaction.customer_id!==customerId||transaction.currency_code!==route.currency||promo&&transaction.discount_id!==promo.discountId)throw new HttpsError('unavailable','Provider transaction setup needs review.');
 const transactionId=transaction.id,customerOwner=base.collection('customerBindings').doc(customerId);
 await db.runTransaction(async tx=>{
  const [l,owner]=await Promise.all([tx.get(lock),tx.get(customerOwner)]);
  if(l.data()?.status!=='creating'||l.data()?.customerId!==customerId||owner.data()?.uid!==uid)throw new HttpsError('failed-precondition','Billing owner needs review.');
  tx.create(base.collection('checkoutIntents').doc(transactionId),{uid,transactionId,customerId,priceId:mapped.priceId,productId:mapped.productId,interval:mapped.interval,plan,currency:route.currency,country:route.country,amount,...(promo?{discountId:promo.discountId,regularAmount:regular,periods:promo.periods,endsAt:promo.endsAt}:{}),status:'pending',createdAt:FieldValue.serverTimestamp()});
  tx.update(lock,{status:'ready',transactionId});
 });
 return {transactionId,customerId,country:route.country,token:checkoutToken.value(),environment:m};
});

const listingKey=(uid:string,listingId:string)=>createHash('sha256').update(JSON.stringify([uid,listingId])).digest('hex');
function marketplaceConfig(uid:string,m:Mode,route:ReturnType<typeof paymentRegion>){
 if(!route||route.provider!=='paddle'||!checkoutReady(uid,m)||marketplaceEnabled.value()!=='true')return null;
 const priceId=marketplacePrice.value(),productId=marketplaceProduct.value(),amount=marketplaceFees[route.currency];
 if(!id(priceId,'pri')||!id(productId,'pro')||!amount||config()[priceId])return null;
 return {priceId,productId,country:route.country,currency:route.currency,amount};
}
export const getPaddleMarketplaceOptions=onCall({region:'europe-west1',invoker:'public',cors:[appOrigin]},async request=>{
 if(!request.auth||request.auth.token.firebase?.sign_in_provider==='anonymous')throw new HttpsError('unauthenticated','Sign in first.');
 const uid=request.auth.uid,m=mode(),settings=marketplaceConfig(uid,m,await accountRegion(uid));
 return settings?{enabled:true,amount:settings.amount,currency:settings.currency,environment:m}:{enabled:false};
});
export const createPaddleMarketplaceCheckout=onCall({region:'europe-west1',invoker:'public',cors:[appOrigin],secrets:[apiKey]},async request=>{
 if(!request.auth||request.auth.token.firebase?.sign_in_provider==='anonymous')throw new HttpsError('unauthenticated','Sign in first.');
 const uid=request.auth.uid,m=mode(),listingId=request.data?.id;
 if(!validListingId(listingId))throw new HttpsError('invalid-argument','Save a valid Marketplace listing first.');
 const settings=marketplaceConfig(uid,m,await accountRegion(uid));
 if(!settings)throw new HttpsError('failed-precondition','International Marketplace checkout is not available.');
 const account=await getAuth().getUser(uid);
 if(!account.email||!account.emailVerified||account.disabled)throw new HttpsError('failed-precondition','Verify your account email first.');
 const remote=await paddleApi(m,'/prices/'+settings.priceId);
 if(!oneOffPriceMatches(remote,settings))throw new HttpsError('failed-precondition','The Paddle listing fee does not match the regional catalog.');
 const db=getFirestore(),base=db.collection('paddleBilling').doc(m),key=listingKey(uid,listingId),lock=base.collection('marketplaceLocks').doc(key),workspace=db.doc('listingWorkspaces/'+uid),draft=workspace.collection('drafts').doc(listingId),publication=workspace.collection('publications').doc(listingId),workspaceLock=workspace.collection('marketplaceCheckouts').doc(listingId);
 const month=new Intl.DateTimeFormat('sv-SE',{timeZone:'UTC',year:'numeric',month:'2-digit'}).format(new Date());
 const region=listingRegion({countryCode:settings.country,currency:settings.currency});
 const previous=await db.runTransaction(async tx=>{
  const [d,l,p,w,u,access,usage]=await Promise.all([tx.get(draft),tx.get(lock),tx.get(publication),tx.get(workspaceLock),tx.get(db.doc('users/'+uid)),tx.get(db.doc('listingEntitlements/'+uid)),tx.get(workspace.collection('allowances').doc(month))]);
  const profile=u.data(),details=d.data(),old=l.data(),other=w.data();
  if(!profile||typeof profile.bakeryId!=='string'||!profile.bakeryId||profile.bakeryId.includes('/')||details?.kind!=='marketplace'||!sameListingRegion(details,region??undefined)||p.exists)throw new HttpsError('failed-precondition','Save an unpublished regional listing first.');
  const bakery=(await tx.get(db.doc('bakeries/'+profile.bakeryId))).data();
  if(bakery?.ownerUid!==uid||!sameListingRegion(bakery,region??undefined))throw new HttpsError('permission-denied','Listing workspace ownership changed.');
  if(old){
   if(old.status==='ready'&&id(old.transactionId,'txn')&&id(old.customerId,'ctm')&&old.country===settings.country&&old.currency===settings.currency&&old.priceId===settings.priceId)return {transactionId:old.transactionId,customerId:old.customerId};
   throw new HttpsError('failed-precondition','A listing payment is already linked. Please contact support before paying again.');
  }
  if(other)throw new HttpsError('failed-precondition','A prior listing checkout requires review.');
  if(listingPermissions(region,access.data()).suite&&(usage.data()?.used??0)<3)throw new HttpsError('failed-precondition','You have an included Marketplace listing. Use Save and publish instead.');
  tx.create(lock,{uid,listingId,bakeryId:profile.bakeryId,...settings,status:'creating',createdAt:FieldValue.serverTimestamp()});
  // Sandbox locks are isolated and cannot prevent edits or publish live listings.
  if(m==='live')tx.create(workspaceLock,{provider:'paddle',domain:'live',status:'pending',reference:key,countryCode:settings.country,currency:settings.currency,createdAt:FieldValue.serverTimestamp()});
  return null;
 });
 if(previous)return {...previous,country:settings.country,token:checkoutToken.value(),environment:m};
 const customerId=await ensurePaddleCustomer(m,uid,account.email);
 const transaction=await paddleApi(m,'/transactions',{items:[{price_id:settings.priceId,quantity:1}],customer_id:customerId,currency_code:settings.currency,collection_mode:'automatic'});
 if(!id(transaction.id,'txn')||transaction.customer_id!==customerId||transaction.currency_code!==settings.currency)throw new HttpsError('unavailable','Marketplace transaction needs review.');
 const transactionId=transaction.id;
 await db.runTransaction(async tx=>{
  const [l,owner]=await Promise.all([tx.get(lock),tx.get(base.collection('customerBindings').doc(customerId))]);
  const row=l.data();
  if(row?.status!=='creating'||owner.data()?.uid!==uid)throw new HttpsError('failed-precondition','Listing payment ownership needs review.');
  tx.create(base.collection('marketplaceIntents').doc(transactionId),{uid,listingId,bakeryId:row.bakeryId,key,customerId,transactionId,...settings,status:'pending',createdAt:FieldValue.serverTimestamp()});
  tx.update(lock,{status:'ready',transactionId,customerId});
 });
 return {transactionId,customerId,country:settings.country,token:checkoutToken.value(),environment:m};
});
async function settlePaddleMarketplace(m:Mode,e:Event){
 if(e.event_type!=='transaction.completed'||!id(e.data.id,'txn'))return;
 const db=getFirestore(),base=db.collection('paddleBilling').doc(m),intentRef=base.collection('marketplaceIntents').doc(e.data.id),intent=(await intentRef.get()).data();
 if(!intent||intent.status!=='pending')return;
 if(m==='live'&&provisionEnabled.value()!=='true')throw Error('Live provisioning disabled');
 let country:string|null=null;
 if(marketplaceCompleted(e.data,intent)&&id(e.data.customer_id,'ctm')&&id(e.data.address_id,'add')){
  const address=await paddleApi(m,'/customers/'+e.data.customer_id+'/addresses/'+e.data.address_id);
  if(address.id===e.data.address_id&&address.customer_id===e.data.customer_id)country=str(address.country_code);
 }
 const uid=intent.uid,listingId=intent.listingId;
 if(typeof uid!=='string'||!uid||uid.includes('/')||!validListingId(listingId)||!id(intent.customerId,'ctm')||intent.key!==listingKey(uid,listingId))throw Error('Invalid marketplace intent');
 const ws=db.doc('listingWorkspaces/'+uid),lock=base.collection('marketplaceLocks').doc(intent.key),checkout=ws.collection('marketplaceCheckouts').doc(listingId),publication=ws.collection('publications').doc(listingId),catalog=db.doc('listingCatalogs/marketplace/entries/'+intent.key);
 await db.runTransaction(async tx=>{
  const [current,l,owner,user,draft,pub,wl]=await Promise.all([tx.get(intentRef),tx.get(lock),tx.get(base.collection('customerBindings').doc(intent.customerId)),tx.get(db.doc('users/'+uid)),tx.get(ws.collection('drafts').doc(listingId)),tx.get(publication),tx.get(checkout)]);
  if(current.data()?.status!=='pending')return;
  const row=current.data()!,details=draft.data(),region=listingRegion({countryCode:row.country,currency:row.currency});
  const valid=marketplaceCompleted(e.data,row)&&country===row.country&&owner.data()?.uid===uid&&user.data()?.bakeryId===row.bakeryId&&l.data()?.transactionId===e.data.id&&l.data()?.customerId===row.customerId&&details?.kind==='marketplace'&&sameListingRegion(details,region??undefined)&&!pub.exists&&Number.isSafeInteger(details?.priceCents)&&details!.priceCents>0;
  const liveLockOK=m==='sandbox'||(wl.data()?.provider==='paddle'&&wl.data()?.domain==='live'&&wl.data()?.status==='pending'&&wl.data()?.reference===row.key&&sameListingRegion(wl.data(),region??undefined));
  if(!valid||!liveLockOK){tx.update(intentRef,{status:'review',reason:'listing-completion-conflict',updatedAt:FieldValue.serverTimestamp()});return;}
  tx.update(intentRef,{status:m==='live'?'paid':'test_paid',updatedAt:FieldValue.serverTimestamp()});
  tx.update(lock,{status:m==='live'?'paid':'test_paid',updatedAt:FieldValue.serverTimestamp()});
  if(m==='live'){
   const fields={kind:'marketplace',countryCode:region!.countryCode,currency:region!.currency,title:details!.title,area:details!.area,description:details!.description,priceCents:details!.priceCents,logo:'',photos:details!.photos??[],sellerType:'baker',standalonePaid:true,standaloneProvider:'paddle',status:'published',updatedAt:FieldValue.serverTimestamp()};
   tx.update(checkout,{status:'paid',transactionId:e.data.id,updatedAt:FieldValue.serverTimestamp()});
   tx.create(publication,fields);tx.create(catalog,{...fields,ownerUid:uid});
  }
 });
}

// Profile and listing grants are updated atomically from the same verified subscription.
function projectSubscription(tx:FirebaseFirestore.Transaction,profileRef:FirebaseFirestore.DocumentReference,profile:Json,entityId:string,snap:ReturnType<typeof subscriptionSnapshot>,prices:Record<string,Price>,live:boolean,regionBinding:Json){
 if(!live)return;
 const owns=profile.paddleSubscriptionId===entityId&&profile.subscriptionSource==='paddle';
 if(!owns&&!isFreeCheckoutProfile(profile))return;
 const price=mappedPaddlePrice({items:snap.items},prices);
 // Explicit configured plan needed for non-Pro grants; preserve older standard mappings.
 const plan=price?configuredCheckoutPlan(price):null;
 if(!plan&&!owns)return;
 const region=paymentRegion({countryCode:regionBinding.country,currency:regionBinding.currency});
 // Disallow listing grants without trusted checkout-region binding.
 const rights=planRights(plan),listingRegionOK=region?.provider==='paddle';
 const active=!!plan&&grantsPaddleAccess(snap.status,snap.expiresAtMs)&&(!rights.finder||listingRegionOK);
 const expires=active?new Date(snap.expiresAtMs!):new Date(0);
 const status=active?snap.status:snap.status==='canceled'?'cancelled':snap.status==='past_due'?'past_due':'expired';
 // Directory-only members retain a free Pro profile even during an active listing subscription.
 const proPlan=rights.pro||(!plan&&profile.subscriptionPlan==='pro');
 tx.update(profileRef,{subscriptionPlan:proPlan?'pro':'free',subscriptionSource:'paddle',subscriptionStatus:proPlan?status:'free',subscriptionExpiresAt:proPlan?expires:new Date(0),subscriptionBillingInterval:price?.interval??profile.subscriptionBillingInterval??null,subscriptionOffer:price?.offer??null,paddlePlan:plan??profile.paddlePlan??null,paddleSubscriptionStatus:status,paddleCustomerId:snap.customerId,paddleSubscriptionId:entityId,updatedAt:FieldValue.serverTimestamp()});
 if(listingRegionOK){
  tx.set(getFirestore().doc('listingEntitlements/'+profileRef.id),{paddleSubscriptionId:entityId,paddleCountryCode:region!.country,paddleCurrency:region!.currency,paddleFinderExpiresAt:active&&rights.finder?expires:new Date(0),paddleSuiteExpiresAt:active&&rights.suite?expires:new Date(0),updatedAt:FieldValue.serverTimestamp()},{merge:true});
 }
}
// All binding records are created by trusted server-side checkout reconciliation,
// never from a webhook email or client-supplied custom data. No binding = mirror only.
async function applyEvent(m:Mode,e:Event){
 if(!eventTypes.has(e.event_type))return 'ignored';
 const db=getFirestore(),base=db.collection('paddleBilling').doc(m);
 const kind=e.event_type.split('.')[0],prefix=kind==='subscription'?'sub':kind==='customer'?'ctm':'txn';
 if(!id(e.data.id,prefix))throw Error('Invalid Paddle entity ID');
 const entityId=e.data.id,occurredAtMs=millis(e.occurred_at),occurredAtMicros=providerEventClock(e.occurred_at);
 const ref=base.collection(kind+'s').doc(entityId),receipt=base.collection('events').doc(e.event_id);
 if((await receipt.get()).exists)return 'duplicate';
 await settlePaddleMarketplace(m,e);
 const prices=kind==='subscription'||kind==='transaction'?config():{};
 const liveProvision=m==='live'&&provisionEnabled.value()==='true';
 // Fetch the real billing country only for the original checkout, not recurring payments.
 let completedCountry:string|null=null;
 if(kind==='transaction'&&e.event_type==='transaction.completed'){
  const intent=(await base.collection('checkoutIntents').doc(entityId).get()).data();
  if(intent&&intent.status==='pending'&&completedMatches(e.data,intent)&&id(e.data.customer_id,'ctm')&&id(e.data.address_id,'add')){
   const address=await paddleApi(m,'/customers/'+e.data.customer_id+'/addresses/'+e.data.address_id);
   if(address.id===e.data.address_id&&address.customer_id===e.data.customer_id)completedCountry=str(address.country_code);
  }
 }
 return db.runTransaction(async tx=>{
  const [seen,old]=await Promise.all([tx.get(receipt),tx.get(ref)]);
  if(seen.exists)return 'duplicate';
  const oldData=old.data();
  const previousClock=typeof oldData?.occurredAtMicros==='number'?oldData.occurredAtMicros:typeof oldData?.occurredAtMs==='number'?oldData.occurredAtMs*1000:undefined;
  const isNew=shouldApplyPaddleEvent(previousClock,occurredAtMicros);
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
    if(!completedMatches(e.data,intent)||completedCountry!==intent.country||typeof uid!=='string'||!uid||uid.includes('/')||!id(customerId,'ctm')||!id(subId,'sub')){
     tx.update(intentRef,{status:'review',reason:'provider-completion-mismatch',updatedAt:FieldValue.serverTimestamp()});
    }else{
     const ownerRef=base.collection('customerBindings').doc(customerId),accountRef=base.collection('accountBindings').doc(uid),subRef=base.collection('subscriptionBindings').doc(subId),lockRef=base.collection('checkoutLocks').doc(uid),userRef=db.collection('users').doc(uid);
     const [owner,account,sub,lock,user,mirror]=await Promise.all([tx.get(ownerRef),tx.get(accountRef),tx.get(subRef),tx.get(lockRef),tx.get(userRef),tx.get(base.collection('subscriptions').doc(subId))]);
     const mirrored=mirror.data();
     if(owner.data()?.uid!==uid||account.exists||sub.exists||lock.data()?.transactionId!==entityId||!isFreeCheckoutProfile(user.data())||(mirrored&&mirrored.customerId!==customerId)){
      tx.update(intentRef,{status:'review',reason:'ownership-conflict',updatedAt:FieldValue.serverTimestamp()});
     }else{
      tx.create(accountRef,{uid,customerId,subscriptionId:subId,createdAt:FieldValue.serverTimestamp()});
      tx.create(subRef,{uid,customerId,transactionId:entityId,country:intent.country,currency:intent.currency,createdAt:FieldValue.serverTimestamp()});
      tx.update(intentRef,{status:'linked',subscriptionId:subId,updatedAt:FieldValue.serverTimestamp()});
      tx.update(lockRef,{status:'linked',subscriptionId:subId});
      if(mirrored){
       projectSubscription(tx,userRef,obj(user.data()),subId,mirrored as ReturnType<typeof subscriptionSnapshot>,prices,liveProvision,obj(intent));
      }
     }
    }
   }
  }
  if(isNew){
   const data=kind==='subscription'?subscriptionSnapshot(e.data):kind==='customer'?{email:str(e.data.email),status:str(e.data.status)}:{customerId:str(e.data.customer_id),subscriptionId:str(e.data.subscription_id)||null,status:str(e.data.status),currency:str(e.data.currency_code),totals:obj(obj(e.data.details).totals)};
   tx.set(ref,{...data,occurredAtMs,occurredAtMicros,lastEventId:e.event_id,updatedAt:FieldValue.serverTimestamp()});
   if(kind==='subscription'&&profileRef){
    projectSubscription(tx,profileRef,profile,entityId,subscriptionSnapshot(e.data),prices,liveProvision,binding);
   }
  }
  tx.create(receipt,{eventType:e.event_type,entityId,occurredAtMs,occurredAtMicros,applied:isNew,receivedAt:FieldValue.serverTimestamp()});
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

// Only server-created pending transactions owned by this authenticated account can
// be resumed through Paddle's approved default payment link. Never open a raw _ptxn.
export const resumePaddleCheckout=onCall({region:'europe-west1',invoker:'public',cors:[appOrigin],secrets:[apiKey]},async request=>{
 if(!request.auth||request.auth.token.firebase?.sign_in_provider==='anonymous')throw new HttpsError('unauthenticated','Sign in to the account that started checkout.');
 const uid=request.auth.uid,m=mode(),transactionId=request.data?.transactionId;
 if(!id(transactionId,'txn')||!checkoutReady(uid,m))throw new HttpsError('failed-precondition','Checkout is not available.');
 const region=await accountRegion(uid);
 if(!region||region.provider!=='paddle')throw new HttpsError('failed-precondition','Checkout is unavailable in this region.');
 const db=getFirestore(),base=db.collection('paddleBilling').doc(m);
 const [subscription,listing]=await Promise.all([base.collection('checkoutIntents').doc(transactionId).get(),base.collection('marketplaceIntents').doc(transactionId).get()]);
 if(subscription.exists===listing.exists)throw new HttpsError('permission-denied','This checkout is not linked to your account.');
 const intent=(subscription.exists?subscription:listing).data()!;
 if(intent.uid!==uid||intent.status!=='pending'||intent.country!==region.country||intent.currency!==region.currency||!id(intent.customerId,'ctm'))throw new HttpsError('permission-denied','This checkout is not available for your account.');
 const owner=(await base.collection('customerBindings').doc(intent.customerId).get()).data();
 if(owner?.uid!==uid)throw new HttpsError('permission-denied','Checkout ownership could not be verified.');
 // Re-run the relevant gated options check; a withdrawn launch offer cannot be reopened.
 if(subscription.exists){
  const plan=intent.plan;if(!isCheckoutPlan(plan)&&!isLaunchOffer(plan))throw new HttpsError('failed-precondition','Subscription requires review.');
  const sale=salePlan(plan,region);
  if(!sale||sale.mapped.priceId!==intent.priceId||sale.amount!==intent.amount||sale.promo&&sale.promo.discountId!==intent.discountId)throw new HttpsError('failed-precondition','Subscription offer has changed. Contact support.');
 }else if(!validListingId(intent.listingId)||!marketplaceConfig(uid,m,region))throw new HttpsError('failed-precondition','Listing checkout is unavailable.');
 const remote=await paddleApi(m,'/transactions/'+transactionId);
 if(remote.id!==transactionId||remote.customer_id!==intent.customerId||remote.currency_code!==intent.currency||!['draft','ready'].includes(str(remote.status))||!Array.isArray(remote.items)||remote.items.length!==1||obj(remote.items[0]).quantity!==1||obj(obj(remote.items[0]).price).id!==intent.priceId||intent.discountId&&remote.discount_id!==intent.discountId)throw new HttpsError('failed-precondition','The payment is no longer open. Check your subscription or listing status.');
 return {transactionId,customerId:intent.customerId,country:region.country,token:checkoutToken.value(),environment:m,listingId:listing.exists?intent.listingId:null,launchOffer:subscription.exists&&isLaunchOffer(intent.plan)};
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
