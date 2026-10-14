import {randomUUID} from 'node:crypto';
import {getAuth} from 'firebase-admin/auth';
import {getFirestore,FieldValue} from 'firebase-admin/firestore';
import {defineSecret,defineString} from 'firebase-functions/params';
import {HttpsError,onCall,onRequest} from 'firebase-functions/v2/https';
import {onSchedule} from 'firebase-functions/v2/scheduler';
import {validSignature,periodEnd,matchesPayment} from './paystack-core';
import {ANNUAL_LAUNCH,annualRenewalDate,validateAnnualLaunchConsent} from './annual-launch';
import {MONTHLY_LAUNCH,monthlyChargeCents,validateMonthlyConsent} from './monthly-launch';
const secret=defineSecret('PAYSTACK_SECRET_KEY');
const monthly=defineString('PAYSTACK_MONTHLY_PLAN_CODE',{default:''});
const annual=defineString('PAYSTACK_ANNUAL_PLAN_CODE',{default:''});
const enabled=defineString('PAYSTACK_ENABLED',{default:'false'});
// Separate feature gate. Default false; no discounted transaction can start until QA is complete.
const annualLaunchEnabled=defineString('PAYSTACK_ANNUAL_LAUNCH_ENABLED',{default:'false'});
const monthlyLaunchEnabled=defineString('PAYSTACK_MONTHLY_LAUNCH_ENABLED',{default:'false'});
const APP='https://app.batchboss.co.za';
const options={region:'europe-west1',invoker:'public' as const,secrets:[secret],cors:[APP]};
const db=()=>getFirestore();
const now=()=>FieldValue.serverTimestamp();
function uidOf(r:any){if(!r.auth)throw new HttpsError('unauthenticated','Please sign in.');return r.auth.uid as string;}
function mode(){const key=secret.value();if(!/^sk_(test|live)_/.test(key))throw new HttpsError('failed-precondition','Paystack is not configured.');return key.startsWith('sk_live_')?'live':'test';}
function planFor(interval:string){if(!['monthly','annual'].includes(interval))throw new HttpsError('invalid-argument','Choose monthly or annual.');const planCode=interval==='monthly'?monthly.value():annual.value();if(!/^PLN_[a-zA-Z0-9]+$/.test(planCode))throw new HttpsError('failed-precondition','Payment plans are not configured yet.');return {interval:interval as 'monthly'|'annual',amount:interval==='monthly'?14900:119900,currency:'ZAR',planCode,domain:mode()};}
async function api(path:string,body?:unknown){
 // Only fixed operation labels and status codes enter logs: never keys or customer data.
 const operation=path.startsWith('/plan/')?'plan lookup':path==='/customer'?'customer setup':path==='/transaction/initialize'?'checkout initialization':path.startsWith('/transaction/verify/')?'payment verification':'subscription management';
 let res:Response;
 try{
    res=await fetch(`https://api.paystack.co${path}`,{method:body?'POST':'GET',headers:{Authorization:`Bearer ${secret.value()}`,'Content-Type':'application/json','User-Agent':'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36'},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(20000)});
 }catch{
  console.error('Paystack request failed',{operation,reason:'network-or-timeout'});
  throw new HttpsError('unavailable',`Paystack could not be reached during ${operation}. Please try again shortly.`);
 }
 let json:any;
 try{json=await res.json();}catch{json=null;}
 if(!res.ok||json?.status!==true){
  console.error('Paystack request rejected',{operation,httpStatus:res.status,validResponse:json!==null});
  if(res.status===401)throw new HttpsError('failed-precondition','Paystack rejected the configured secret key. Check PAYSTACK_SECRET_KEY in Firebase and redeploy the payment functions.');
  if(res.status===403)throw new HttpsError('failed-precondition',`Paystack denied access during ${operation}. Check the Paystack account permissions and activation status.`);
  if(res.status===429)throw new HttpsError('resource-exhausted','Paystack received too many requests. Please wait a minute and try again.');
  if(res.status>=500||!json)throw new HttpsError('unavailable',`Paystack is temporarily unavailable during ${operation} (HTTP ${res.status}). Please try again shortly.`);
  if(operation==='plan lookup')throw new HttpsError('failed-precondition',`Paystack could not load the configured plan (HTTP ${res.status}). Check that the plan code belongs to the same Paystack account and test/live mode as the secret key.`);
  throw new HttpsError('failed-precondition',`Paystack rejected ${operation} (HTTP ${res.status}). Contact support to check the payment configuration.`);
 }
 return json.data;
}
function safeLink(raw:string,host:string){const url=new URL(raw);if(url.protocol!=='https:'||url.hostname!==host)throw new Error('Unexpected payment URL');return url.toString();}
export const getPaystackPlans=onCall(options,async request=>{uidOf(request);if(enabled.value()!=='true')return {enabled:false};return {enabled:true,testMode:mode()==='test',monthly:149,annual:1199,annualLaunchOffer:{enabled:false,firstYear:ANNUAL_LAUNCH.firstYearCents/100,renewal:ANNUAL_LAUNCH.recurringYearCents/100,terms:ANNUAL_LAUNCH.terms,consentVersion:ANNUAL_LAUNCH.consentVersion}};});
// Phase 1: one-off first-year checkout. This is never a recurring-plan transaction,
// because Paystack ignores the specified amount when a plan code is supplied.
export const createAnnualLaunchCheckout=onCall(options,async request=>{
 const uid=uidOf(request);
 if(enabled.value()!=='true'||annualLaunchEnabled.value()!=='true')
  throw new HttpsError('failed-precondition','The annual launch offer is not accepting payments.');
 if(!validateAnnualLaunchConsent(request.data?.renewalConsent))
  throw new HttpsError('invalid-argument','Accept the annual automatic-renewal agreement first.');
 if(mode()!=='test')
  throw new HttpsError('failed-precondition','Annual launch checkouts are restricted to test mode until end-to-end testing is complete.');
 const user=await getAuth().getUser(uid);
 if(!user.email)throw new HttpsError('failed-precondition','Your account needs an email address.');
 const reference='BBL-'+randomUUID();
 const lock=db().doc('paystackCheckouts/'+uid);
 const profileRef=db().doc('users/'+uid);
 const paymentRef=db().doc('subscriptionPayments/'+reference);
 const customer=await api('/customer',{email:user.email});
 const customerCode=String(customer.customer_code||'');
 if(!/^CUS_[a-zA-Z0-9]+$/.test(customerCode))throw new HttpsError('failed-precondition','Payment customer could not be created.');
 const customerRef=db().doc('paystackCustomers/'+customerCode);
 const reused=await db().runTransaction(async t=>{
  const [current,profile,mapped]=await Promise.all([t.get(lock),t.get(profileRef),t.get(customerRef)]);
  if(!profile.exists)throw new HttpsError('failed-precondition','Complete your bakery profile first.');
  const p=profile.data()!;
  if((p.subscriptionStatus==='active'&&p.subscriptionExpiresAt?.toMillis?.()>Date.now())||
     (p.subscriptionSource==='paystack'&&p.paystackSubscriptionCode))
   throw new HttpsError('failed-precondition','You already have a subscription. Contact support before purchasing another.');
  if(mapped.exists&&mapped.data()?.uid!==uid)throw new HttpsError('permission-denied','Payment customer is linked to another account.');
  const old=current.data();
  if(old&&old.expiresAtMs>Date.now()){
   if(old.interval==='annual_launch'&&old.checkoutUrl)return String(old.checkoutUrl);
   throw new HttpsError('failed-precondition','A checkout is already pending. Please complete it or try again in 30 minutes.');
  }
  t.set(customerRef,{uid,domain:mode(),updatedAt:now()},{merge:true});
  t.set(lock,{reference,interval:'annual_launch',expiresAtMs:Date.now()+1800000});
  t.create(paymentRef,{uid,provider:'paystack',offer:'annual_launch',interval:'annual',
   amount:ANNUAL_LAUNCH.firstYearCents,currency:ANNUAL_LAUNCH.currency,domain:mode(),
   customer:customerCode,status:'pending',renewalAmount:ANNUAL_LAUNCH.recurringYearCents,
   renewalPlanCode:annual.value(),renewalConsentVersion:ANNUAL_LAUNCH.consentVersion,
   renewalConsentAt:now(),createdAt:now()});
  return null;
 });
 if(reused)return {checkoutUrl:reused};
 try{
  const result=await api('/transaction/initialize',{email:user.email,
   amount:ANNUAL_LAUNCH.firstYearCents,currency:ANNUAL_LAUNCH.currency,
   reference,channels:['card'],callback_url:APP+'/?payment=paystack',
   metadata:{batchbossReference:reference,offer:'annual_launch'}});
  const checkoutUrl=safeLink(result.authorization_url,'checkout.paystack.com');
  await lock.update({checkoutUrl});return {checkoutUrl};
 }catch(e){await lock.delete();await paymentRef.update({status:'checkout_failed',updatedAt:now()});throw e;}
});
// This quote only describes the promotion; it never creates a payment.
export const getMonthlyLaunchQuote=onCall(options,async request=>{
 uidOf(request);
 return {enabled:false,offer:'monthly_launch',introductory:MONTHLY_LAUNCH.introductoryCents/100,
 regular:MONTHLY_LAUNCH.regularCents/100,terms:MONTHLY_LAUNCH.terms,
 consentVersion:MONTHLY_LAUNCH.consentVersion,offerEndsLocal:MONTHLY_LAUNCH.offerEndsLocal,
 pricingPreviewCents:monthlyChargeCents(new Date())};
});
// Explicit fail-closed until provider-level renewal price transition is implemented.
export const createMonthlyLaunchCheckout=onCall(options,async request=>{
 uidOf(request);
 if(!validateMonthlyConsent(request.data?.renewalConsent))
  throw new HttpsError('invalid-argument','Accept the monthly renewal pricing terms first.');
 if(monthlyLaunchEnabled.value()!=='true'||mode()!=='test')
  throw new HttpsError('failed-precondition','The monthly launch offer is not accepting payments.');
 throw new HttpsError('unimplemented','The monthly launch billing transition is not implemented.');
});
export const createPaystackCheckout=onCall(options,async request=>{
 const uid=uidOf(request);if(enabled.value()!=='true')throw new HttpsError('failed-precondition','Paystack checkout is not enabled yet.');
 const requestedPlan=String(request.data?.plan);
 // This offer has two different annual charges. It MUST NOT use an ordinary
 // recurring plan until a tested customer-specific renewal transition exists.
 if(requestedPlan==='annual_launch'){
  if(!validateAnnualLaunchConsent(request.data?.renewalConsent))throw new HttpsError('invalid-argument','You must accept the automatic annual renewal terms before checkout.');
  throw new HttpsError('failed-precondition','The annual launch offer is not available for checkout yet. Please choose a standard plan or contact support.');
 }
 const plan=planFor(requestedPlan);
 const user=await getAuth().getUser(uid);if(!user.email)throw new HttpsError('failed-precondition','Your account needs an email address.');
 // Backend owns pricing and validates the dashboard plan before opening checkout.
 const remotePlan=await api(`/plan/${encodeURIComponent(plan.planCode)}`);
 if(remotePlan.amount!==plan.amount||remotePlan.currency!=='ZAR'||remotePlan.interval!==(plan.interval==='annual'?'annually':'monthly')||remotePlan.domain!==plan.domain)throw new HttpsError('failed-precondition','Paystack plan configuration does not match BatchBoss pricing. Please contact support.');
 let previousEnded=false;
 const previous=(await db().doc(`users/${uid}`).get()).data();
 if(previous?.subscriptionSource==='paystack' && previous.paystackSubscriptionCode){
  const linked=await syncSubscription(String(previous.paystackSubscriptionCode));
  previousEnded=!!linked&&['cancelled','completed','complete'].includes(linked.remote.status)&&previous.subscriptionExpiresAt?.toMillis?.()<=Date.now();
 }
 const lockRef=db().doc(`paystackCheckouts/${uid}`);const profileRef=db().doc(`users/${uid}`);const reference=`BB-${randomUUID()}`;
 const existing=await db().runTransaction(async t=>{const [lock,profile]=await Promise.all([t.get(lockRef),t.get(profileRef)]);if(!profile.exists)throw new HttpsError('failed-precondition','Complete your bakery profile first.');const p=profile.data()!;if(p.subscriptionSource==='paystack'&&p.paystackSubscriptionCode&&!previousEnded)throw new HttpsError('failed-precondition','Manage your existing Paystack subscription before starting another.');if(p.subscriptionSource==='payfast'&&p.subscriptionStatus==='active')throw new HttpsError('failed-precondition','Contact support to switch your existing PayFast subscription without duplicate billing.');const old=lock.data();if(old&&old.expiresAtMs>Date.now()){if(old.interval===plan.interval&&old.checkoutUrl)return old.checkoutUrl as string;throw new HttpsError('failed-precondition','A checkout is already open. Complete it or wait 30 minutes before starting another.');}t.set(lockRef,{reference,interval:plan.interval,expiresAtMs:Date.now()+30*60*1000});return null;});
 if(existing)return {checkoutUrl:existing};
 try{
  const customer=await api('/customer',{email:user.email});const customerCode=String(customer.customer_code);if(!/^CUS_[a-zA-Z0-9]+$/.test(customerCode))throw new Error('Invalid customer');
  const customerRef=db().doc(`paystackCustomers/${customerCode}`);
  await db().runTransaction(async t=>{const mapped=await t.get(customerRef);if(mapped.exists&&mapped.data()?.uid!==uid)throw new HttpsError('failed-precondition','This payment customer is linked to another account. Contact support.');t.set(customerRef,{uid,domain:plan.domain,updatedAt:now()},{merge:true});});
  await db().doc(`subscriptionPayments/${reference}`).set({...plan,uid,customer:customerCode,provider:'paystack',status:'pending',createdAt:now()});
  const result=await api('/transaction/initialize',{email:user.email,amount:plan.amount,currency:'ZAR',plan:plan.planCode,reference,channels:['card'],callback_url:`${APP}/?payment=paystack`,metadata:{batchbossReference:reference}});
  const checkoutUrl=safeLink(result.authorization_url,'checkout.paystack.com');
  await lockRef.update({checkoutUrl});return {checkoutUrl};
 }catch(e){await lockRef.delete();throw e;}
});
// Reconcile first-year payment separately: the one-off transaction deliberately
// has no Paystack plan code, unlike the normal recurring plan settlements.
async function settleAnnualLaunch(reference:string, expectedUid?:string){
 if(!/^BBL-[a-f0-9-]{36}$/i.test(reference))
  throw new HttpsError('invalid-argument','Invalid launch payment reference.');
 const ref=db().doc('subscriptionPayments/'+reference);
 const snapshot=await ref.get();
 const p=snapshot.data();
 if(!p||p.offer!=='annual_launch'||p.provider!=='paystack'||(expectedUid&&expectedUid!==p.uid))
  throw new HttpsError('permission-denied','This payment is not linked to your account.');
 if(p.status==='success')return {status:'success',testMode:p.domain==='test',renewalSetup:p.renewalSetup||'pending'};
 // Repeated callbacks must not re-verify or overwrite an already reconciled payment.
 if(p.status==='payment_verified'&&p.renewalSetup==='pending')return {status:'payment_verified',testMode:p.domain==='test',renewalSetup:'pending'};
 if(p.renewalSetup==='submitting'||p.renewalSetup==='needs_reconciliation')return {status:'payment_verified',testMode:p.domain==='test',renewalSetup:p.renewalSetup};
 const tx=await api('/transaction/verify/'+encodeURIComponent(reference));
 if(tx.status!=='success')return {status:String(tx.status||'pending')};
 const authorization=String(tx.authorization?.authorization_code||'');
 if(tx.reference!==reference||tx.amount!==ANNUAL_LAUNCH.firstYearCents||
    tx.currency!=='ZAR'||tx.domain!==p.domain||
    tx.customer?.customer_code!==p.customer||!tx.paid_at||
    !/^AUTH_[a-zA-Z0-9]+$/.test(authorization)||tx.authorization?.reusable!==true)
  throw new HttpsError('failed-precondition','Launch payment verification failed; contact support.');
 const renewalDate=annualRenewalDate(String(tx.paid_at));
 // Record the verified first-year charge independently of renewal-plan availability.
 // A future-plan lookup failure must never hide the fact that the customer paid.
 // Future renewal configuration is checked just before scheduling that subscription.
 await db().runTransaction(async t=>{
  const current=await t.get(ref);
  if(current.data()?.status!=='pending')return;
  t.update(ref,{status:'payment_verified',paidAt:tx.paid_at,
   transactionId:String(tx.id),renewalDate,firstYearAccessUntil:renewalDate,
   entitlementStatus:'awaiting_renewal_setup',renewalSetup:'pending',
   authorizationCode:authorization,updatedAt:now()});
 });
 return {status:'payment_verified',testMode:p.domain==='test',renewalSetup:'pending'};
}
export const verifyAnnualLaunchPayment=onCall(options,async request=>
 settleAnnualLaunch(String(request.data?.reference||''),uidOf(request)));
/**
 * Test-only scheduled renewal setup. A failed or uncertain remote POST is held
 * for human reconciliation. We never retry the POST automatically and risk a
 * second future subscription.
 */
export const scheduleAnnualLaunchRenewal=onCall(options,async request=>{
 const uid=uidOf(request);
 if(annualLaunchEnabled.value()!=='true'||mode()!=='test')
  throw new HttpsError('failed-precondition','Launch renewal scheduling is disabled.');
 const reference=String(request.data?.reference||'');
 const verification=await settleAnnualLaunch(reference,uid);
 if(verification.status!=='payment_verified'&&verification.status!=='success')
  return verification;
 const ref=db().doc('subscriptionPayments/'+reference);
 let canSchedule=false;
 await db().runTransaction(async t=>{
  const snap=await t.get(ref),p=snap.data();
  if(p?.uid!==uid||p?.domain!=='test'||!p?.authorizationCode||
     !['payment_verified','success'].includes(String(p.status))||
     p.renewalConsentVersion!==ANNUAL_LAUNCH.consentVersion||
     p.renewalAmount!==ANNUAL_LAUNCH.recurringYearCents||
     p.renewalPlanCode!==annual.value()||
     !p.renewalDate)
   throw new HttpsError('failed-precondition','Verified test payment and matching renewal agreement required.');
  if(p.renewalSetup==='scheduled')return;
  if(p.renewalSetup!=='pending')
   throw new HttpsError('failed-precondition','Renewal setup requires manual reconciliation; do not retry the subscription.');
  t.update(ref,{renewalSetup:'submitting',renewalStartedAt:now()});canSchedule=true;
 });
 if(!canSchedule)return {status:'success',renewalSetup:'scheduled',testMode:true};
 const payment=(await ref.get()).data()!;
 try{
  const renewalPlan=planFor('annual');
  const remotePlan=await api('/plan/'+encodeURIComponent(renewalPlan.planCode));
  if(payment.renewalPlanCode!==renewalPlan.planCode||
     remotePlan.amount!==ANNUAL_LAUNCH.recurringYearCents||
     remotePlan.currency!==ANNUAL_LAUNCH.currency||
     remotePlan.interval!=='annually'||remotePlan.domain!==payment.domain)
   throw new HttpsError('failed-precondition','Annual renewal plan configuration does not match agreed pricing.');
  const remote=await api('/subscription',{customer:payment.customer,plan:payment.renewalPlanCode,
   authorization:payment.authorizationCode,start_date:payment.renewalDate});
  const code=String(remote.subscription_code||'');
  if(!/^SUB_[a-zA-Z0-9]+$/.test(code))throw new Error('Missing scheduled subscription code');
  await db().doc('paystackSubscriptions/'+code).set({
   uid,customer:payment.customer,provider:'paystack',domain:'test',interval:'annual',
   amount:ANNUAL_LAUNCH.recurringYearCents,planCode:payment.renewalPlanCode,
   offer:'annual_launch',sourceReference:reference,status:String(remote.status||'unknown'),
   renewalDate:payment.renewalDate,updatedAt:now()
  },{merge:true});
  await ref.update({renewalSetup:'scheduled',status:'success',subscriptionCode:code,entitlementStatus:'test_verified_only',updatedAt:now()});
  return {status:'success',testMode:true,renewalSetup:'scheduled'};
 }catch{
  await ref.update({renewalSetup:'needs_reconciliation',entitlementStatus:'awaiting_support_review',updatedAt:now()});
  throw new HttpsError('failed-precondition','The payment is verified, but automatic renewal requires a support check. Do not purchase again.');
 }
});

// Read-only subscription setup status for the signed-in baker.
// Intentionally omits card authorization, provider tokens and customer identifiers.
export const getAnnualLaunchStatus=onCall(options,async request=>{
 const uid=uidOf(request);
 const reference=String(request.data?.reference||'');
 if(!/^BBL-[a-f0-9-]{36}$/i.test(reference))
  throw new HttpsError('invalid-argument','Invalid launch payment reference.');
 const snapshot=await db().doc('subscriptionPayments/'+reference).get();
 const payment=snapshot.data();
 if(!payment||payment.uid!==uid||payment.offer!=='annual_launch')
  throw new HttpsError('permission-denied','This launch payment is not linked to your account.');
 return {
  paymentStatus:String(payment.status||'pending'),
  renewalSetup:String(payment.renewalSetup||'pending'),
  entitlementStatus:String(payment.entitlementStatus||'not_granted'),
  testMode:payment.domain==='test',
  // This is an informational record date, not a grant of paid access.
  firstYearAccessUntil:typeof payment.firstYearAccessUntil==='string'?payment.firstYearAccessUntil:null,
 };
});
async function settle(reference:string,expectedUid?:string,subscriptionCode?:string){
 if(!/^[a-zA-Z0-9_.-]{1,100}$/.test(reference))throw new HttpsError('invalid-argument','Invalid payment reference.');
 const paymentRef=db().doc(`subscriptionPayments/${reference}`);const payment=await paymentRef.get();let expected=payment.data();
 if(!expected&&subscriptionCode){const sub=await db().doc(`paystackSubscriptions/${subscriptionCode}`).get();expected=sub.data();}
 if(!expected||expected.provider!=='paystack'||(expectedUid&&expected.uid!==expectedUid))throw new HttpsError('permission-denied','This payment is not linked to your account.');
 const transaction=await api(`/transaction/verify/${encodeURIComponent(reference)}`);
 if(transaction.status!=='success')return {status:String(transaction.status||'pending')};
 if(transaction.reference!==reference || !matchesPayment(transaction,expected as any))throw new HttpsError('failed-precondition','Payment details do not match the subscription.');
 const expires=periodEnd(transaction.paid_at,expected.interval);const profileRef=db().doc(`users/${expected.uid}`);
 await db().runTransaction(async t=>{const [current,profile]=await Promise.all([t.get(paymentRef),t.get(profileRef)]);if(current.data()?.status==='success')return;if(!profile.exists)throw new Error('Missing profile');const p=profile.data()!;const old=p.subscriptionExpiresAt?.toDate?.();const expiry=old&&old>expires?old:expires;
 t.set(paymentRef,{...expected,reference,status:'success',transactionId:String(transaction.id),paidAt:transaction.paid_at,processedAt:now()},{merge:true});
 // Test mode records test payments but never grants production entitlements.
 if(expected!.domain==='live' && !(p.subscriptionSource==='admin_promotion' && p.subscriptionExpiresAt?.toMillis?.()>Date.now()))t.update(profileRef,{subscriptionPlan:'pro',subscriptionStatus:expiry>new Date()?'active':'expired',subscriptionSource:'paystack',subscriptionBillingInterval:expected!.interval,subscriptionExpiresAt:expiry,paystackCustomerCode:expected!.customer,...(subscriptionCode?{paystackSubscriptionCode:subscriptionCode}:{}),updatedAt:now()});
 });return {status:'success',testMode:expected.domain==='test'};
}
export const verifyPaystackPayment=onCall(options,async request=>settle(String(request.data?.reference||''),uidOf(request)));
async function syncSubscription(code:string){
 if(!/^SUB_[a-zA-Z0-9]+$/.test(code))throw new Error('Invalid subscription');
 const s=await api(`/subscription/${code}`);const customer=String(s.customer?.customer_code||'');const mapping=await db().doc(`paystackCustomers/${customer}`).get();if(!mapping.exists)return null;
 const uid=mapping.data()!.uid;const interval=s.plan?.interval==='annually'?'annual':s.plan?.interval==='monthly'?'monthly':'';if(!interval)return null;const plan=planFor(interval);
 if(s.domain!==plan.domain||s.plan?.plan_code!==plan.planCode||s.amount!==plan.amount||s.plan?.currency!=='ZAR')throw new Error('Subscription mismatch');
 await db().doc(`paystackSubscriptions/${code}`).set({...plan,customer,uid,provider:'paystack',status:s.status,updatedAt:now()},{merge:true});
 if(plan.domain==='live')await db().runTransaction(async t=>{const ref=db().doc(`users/${uid}`);const snap=await t.get(ref);if(!snap.exists)return;const p=snap.data()!;
 // Never overwrite a later subscription or an administrator's promotion.
 if(p.subscriptionSource!=='paystack'||(p.paystackSubscriptionCode&&p.paystackSubscriptionCode!==code&&!['cancelled','completed','complete'].includes(p.paystackSubscriptionStatus)))return;
 t.update(ref,{paystackSubscriptionCode:code,paystackSubscriptionStatus:s.status,subscriptionRenews:s.status==='active',updatedAt:now()});});
 return {uid,remote:s};
}
export const managePaystackSubscription=onCall(options,async request=>{const uid=uidOf(request);const profile=(await db().doc(`users/${uid}`).get()).data();const code=String(profile?.paystackSubscriptionCode||'');if(!/^SUB_[a-zA-Z0-9]+$/.test(code)||profile?.subscriptionSource!=='paystack')throw new HttpsError('failed-precondition','No Paystack subscription is linked yet. Please contact support.');const result=await api(`/subscription/${code}/manage/link`);return {url:safeLink(result.link,'paystack.com')};});
export const paystackWebhook=onRequest({region:'europe-west1',invoker:'public',secrets:[secret]},async(request,response)=>{
 if(request.method!=='POST'){response.status(405).send('Method not allowed');return;}
 if(!validSignature(request.rawBody,String(request.headers['x-paystack-signature']||''),secret.value())){response.status(401).send('Invalid signature');return;}
 try{const event=request.body;const data=event.data||{};
 if(event.event==='charge.success'){
  const reference=String(data.reference||'');if(!/^[a-zA-Z0-9_.-]{1,100}$/.test(reference)){response.status(200).send('Ignored');return;}const known=await db().doc(`subscriptionPayments/${reference}`).get();
  if(known.exists){if(known.data()?.offer==='annual_launch'){await settleAnnualLaunch(reference);response.status(200).send('OK');return;}await settle(reference);const customer=known.data()?.customer;
   // subscription.create may have arrived before charge.success.
   const subs=await db().collection('paystackSubscriptions').where('customer','==',customer).get();for(const sub of subs.docs)if(sub.data().planCode===known.data()?.planCode)await syncSubscription(sub.id);
  }
 }else if(['subscription.create','subscription.not_renew','subscription.disable','invoice.payment_failed','invoice.update'].includes(event.event)){
  const code=String(data.subscription?.subscription_code||data.subscription_code||'');const linked=await syncSubscription(code);
  if(linked&&event.event==='invoice.update'&&data.paid&&data.transaction?.reference)await settle(String(data.transaction.reference),undefined,code);
 }
 response.status(200).send('OK');
 }catch(error){console.error('Paystack event could not be processed',error instanceof HttpsError?error.code:'processing-error');response.status(500).send('Retry later');}
});
export const expirePaystackAccess=onSchedule({region:'europe-west1',schedule:'every 60 minutes'},async()=>{
 // Query only provider, then filter expiry locally: no composite index required.
 const users=await db().collection('users').where('subscriptionSource','==','paystack').get();
 for(const user of users.docs)await db().runTransaction(async t=>{const current=await t.get(user.ref);const p=current.data();if(p?.subscriptionSource==='paystack'&&p.subscriptionExpiresAt?.toMillis?.()<=Date.now()&&p.subscriptionStatus==='active')t.update(user.ref,{subscriptionStatus:'expired',updatedAt:now()});});
});
